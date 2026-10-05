package ca.mattmccormick.screenbudget

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkManager
import ca.mattmccormick.screenbudget.data.Settings as AppSettings
import ca.mattmccormick.screenbudget.data.SettingsRepository
import ca.mattmccormick.screenbudget.data.UsageDatabase
import ca.mattmccormick.screenbudget.export.ImportService
import ca.mattmccormick.screenbudget.export.ImportStatus
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var database: UsageDatabase
    private val settingsRepository by lazy {
        SettingsRepository(applicationContext.settingsDataStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        database = (application as ScreenBudgetApplication).database
        val initialDestination = AppDestination.from(intent)
        setContent {
            ScreenBudgetTheme {
                val usageEventsSource = remember { UsageEventsSource(this@MainActivity) }
                val notificationManager = remember {
                    getSystemService(NotificationManager::class.java)
                }
                val collector = remember {
                    Collector(database.usageDao(), usageEventsSource)
                }
                val currentDayReader = remember {
                    CurrentDayUsageSnapshotReader(usageEventsSource)
                }
                val importer = remember { ImportService(database) }
                val appInfoSource = remember { AppInfoResolver(packageManager) }
                val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
                val scope = rememberCoroutineScope()
                var importStatus by remember { mutableStateOf<ImportStatus?>(null) }
                var importVersion by remember { mutableIntStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current
                var notificationsEnabled by remember {
                    mutableStateOf(notificationManager.areNotificationsEnabled())
                }
                DisposableEffect(lifecycleOwner, notificationManager) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            notificationsEnabled = notificationManager.areNotificationsEnabled()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    notificationsEnabled = granted
                    scope.launch {
                        settingsRepository.setNotificationsDeclined(!granted)
                    }
                }
                var pendingExportFormat by remember { mutableStateOf(ExportFormat.JSON) }
                var exportError by remember { mutableStateOf<String?>(null) }
                val exportDocument = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    scope.launch {
                        exportError = withContext(Dispatchers.IO) {
                            handleExportResult(result.resultCode, result.data) { destination ->
                                writeExport(
                                    destination = destination,
                                    format = pendingExportFormat,
                                    database = database,
                                    settings = settings,
                                    openOutputStream = contentResolver::openOutputStream,
                                )
                            }
                        }
                    }
                }
                val importDocument = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri != null) {
                        scope.launch {
                            val status = withContext(Dispatchers.IO) {
                                runCatching {
                                    val encoded = contentResolver.openInputStream(uri)
                                        ?.bufferedReader()
                                        ?.use { it.readText() }
                                        ?: error("Could not read the selected file")
                                    ImportStatus.Success(importer.importJson(encoded))
                                }.getOrElse { error ->
                                    ImportStatus.Error(
                                        error.message ?: "The selected file could not be imported",
                                    )
                                }
                            }
                            importStatus = status
                            if (status is ImportStatus.Success) {
                                importVersion++
                            }
                        }
                    }
                }
                ScreenBudgetApp(
                    usageEventsSource = usageEventsSource,
                    launchSettings = ::startActivity,
                    settings = settings,
                    createNotificationChannels = {
                        NotificationChannels.create(this@MainActivity)
                    },
                    requestNotificationPermission = { permission ->
                        notificationPermission.launch(permission)
                    },
                    recordNotificationChoice = { declined ->
                        scope.launch {
                            settingsRepository.setNotificationsDeclined(declined)
                        }
                    },
                    finishOnboarding = { weekStartDay, notificationTime ->
                        scope.launch {
                            settingsRepository.finishOnboarding(weekStartDay, notificationTime)
                            scheduleDailyCollection(
                                WorkManager.getInstance(this@MainActivity),
                                Clock.systemDefaultZone(),
                                notificationTime,
                            )
                        }
                    },
                    collectUsage = {
                        collector.collect(LocalDate.now(), ZoneId.systemDefault())
                    },
                    mainContent = { collectionVersion ->
                        HomeWithSettings(
                            settings = settings,
                            hasUsageAccess = usageEventsSource.hasUsageAccess(),
                            notificationsEnabled =
                                notificationsEnabled && !settings.notificationsDeclined,
                            collectNow = {
                                collector.collect(LocalDate.now(), ZoneId.systemDefault())
                            },
                            openUsageSettings = {
                                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            },
                            openNotificationSettings = {
                                scope.launch {
                                    settingsRepository.setNotificationsDeclined(false)
                                }
                                startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                                )
                            },
                            launchExport = { intent ->
                                pendingExportFormat = if (intent.type == ExportFormat.CSV.mimeType) {
                                    ExportFormat.CSV
                                } else {
                                    ExportFormat.JSON
                                }
                                exportError = null
                                exportDocument.launch(intent)
                            },
                            exportError = exportError,
                            openImportDocument = {
                                importDocument.launch(arrayOf("application/json"))
                            },
                            importStatus = importStatus,
                            appSettings = { onBack ->
                                AppAllowanceSettings(
                                    usageDao = database.usageDao(),
                                    appRuleDao = database.appRuleDao(),
                                    appInfoSource = appInfoSource,
                                    onBack = onBack,
                                    readCurrentDay = { zone ->
                                        if (usageEventsSource.hasUsageAccess()) currentDayReader.read(zone)
                                        else CurrentDayUsageSnapshotResult.Unavailable
                                    },
                                )
                            },
                            mainContent = { currentSettings ->
                                AppNavigationShell(
                                    initialDestination = initialDestination,
                                    home = {
                                        HomeRoute(
                                            usageDao = database.usageDao(),
                                            goalDao = database.goalDao(),
                                            appRuleDao = database.appRuleDao(),
                                            settings = currentSettings,
                                            readCurrentDay = { zone ->
                                                if (usageEventsSource.hasUsageAccess()) {
                                                    currentDayReader.read(zone)
                                                } else {
                                                    CurrentDayUsageSnapshotResult.Unavailable
                                                }
                                            },
                                            refreshKey = collectionVersion to importVersion,
                                        )
                                    },
                                    dayDetail = {
                                        DayDetailScreen(
                                            dao = database.usageDao(),
                                            appRuleDao = database.appRuleDao(),
                                            appInfoSource = appInfoSource,
                                            readCurrentDay = { zone ->
                                                if (usageEventsSource.hasUsageAccess()) {
                                                    currentDayReader.read(zone)
                                                } else {
                                                    CurrentDayUsageSnapshotResult.Unavailable
                                                }
                                            },
                                            refreshKey = collectionVersion,
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun ScreenBudgetApp(
    usageEventsSource: UsageEventsSource,
    launchSettings: (Intent) -> Unit,
    settings: AppSettings = AppSettings(),
    createNotificationChannels: () -> Unit = {},
    requestNotificationPermission: (permission: String) -> Unit = {},
    recordNotificationChoice: (notificationsDeclined: Boolean) -> Unit = {},
    finishOnboarding: (DayOfWeek, LocalTime) -> Unit = { _, _ -> },
    collectUsage: () -> Unit = {},
    mainContent: @Composable (collectionVersion: Int) -> Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    var hasUsageAccess by remember(usageEventsSource) {
        mutableStateOf(usageEventsSource.hasUsageAccess())
    }
    var notificationStepDone by remember(settings.onboardingDone) {
        mutableStateOf(settings.onboardingDone)
    }
    var onboardingFinished by remember(settings.onboardingDone) {
        mutableStateOf(settings.onboardingDone)
    }
    var collectionVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner, usageEventsSource) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = usageEventsSource.hasUsageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(hasUsageAccess) {
        if (hasUsageAccess) {
            withContext(Dispatchers.IO) { collectUsage() }
            collectionVersion++
        }
    }

    if (!hasUsageAccess && !settings.onboardingDone) {
        UsageAccessScreen(
            onAllowUsageAccess = {
                launchSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )
    } else if (!notificationStepDone) {
        NotificationOnboardingScreen(
            onTurnOnNotifications = {
                createNotificationChannels()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotificationPermission(Manifest.permission.POST_NOTIFICATIONS)
                }
                recordNotificationChoice(false)
                notificationStepDone = true
            },
            onNotNow = {
                createNotificationChannels()
                recordNotificationChoice(true)
                notificationStepDone = true
            },
        )
    } else if (!onboardingFinished) {
        ScheduleOnboardingScreen(
            settings = settings,
            onFinish = { weekStartDay, notificationTime ->
                finishOnboarding(weekStartDay, notificationTime)
                onboardingFinished = true
            },
        )
    } else {
        mainContent(collectionVersion)
    }
}

@Composable
private fun UsageAccessScreen(
    onAllowUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.usage_access_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.usage_access_explanation),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onAllowUsageAccess,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.allow_usage_access))
                }
            }
        }
    }
}

@Preview(name = "Usage access", showBackground = true, showSystemUi = true)
@Composable
private fun UsageAccessScreenPreview() {
    ScreenBudgetTheme {
        UsageAccessScreen(onAllowUsageAccess = {})
    }
}
