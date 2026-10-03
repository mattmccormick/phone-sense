package ca.mattmccormick.screenbudget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.datastore.preferences.preferencesDataStore
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
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    private lateinit var database: UsageDatabase
    private val settingsRepository by lazy {
        SettingsRepository(applicationContext.settingsDataStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (application as ScreenBudgetApplication).database
        setContent {
            MaterialTheme {
                val usageEventsSource = remember { UsageEventsSource(this@MainActivity) }
                val collector = remember {
                    Collector(database.usageDao(), usageEventsSource)
                }
                val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
                val scope = rememberCoroutineScope()
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) {}
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
                    mainContent = { _ ->
                        GoalsRoute(database.goalDao(), settings)
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

    if (!hasUsageAccess) {
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
    MaterialTheme {
        UsageAccessScreen(onAllowUsageAccess = {})
    }
}
