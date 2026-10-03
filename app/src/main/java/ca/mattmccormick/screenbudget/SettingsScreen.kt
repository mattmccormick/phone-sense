package ca.mattmccormick.screenbudget

import android.app.TimePickerDialog
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.export.ImportStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val settingsTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun SettingsScreen(
    settings: Settings,
    hasUsageAccess: Boolean,
    notificationsEnabled: Boolean,
    onBack: () -> Unit = {},
    onWeekStartDayChange: (DayOfWeek) -> Unit = {},
    onNotificationTimeChange: (LocalTime) -> Unit = {},
    onReductionPercentChange: (Int) -> Unit = {},
    collectNow: () -> CollectResult = { CollectResult.NothingToDo },
    openUsageSettings: () -> Unit = {},
    openNotificationSettings: () -> Unit = {},
    launchExport: (Intent) -> Unit = {},
    exportError: String? = null,
    today: () -> LocalDate = LocalDate::now,
    openImportDocument: () -> Unit = {},
    importStatus: ImportStatus? = null,
    onAbout: () -> Unit = {},
    showTimePicker: ((LocalTime, (LocalTime) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var weekStartDay by remember(settings.weekStartDay) { mutableStateOf(settings.weekStartDay) }
    var weekMenuExpanded by remember { mutableStateOf(false) }
    var notificationTime by remember(settings.notificationTime) {
        mutableStateOf(settings.notificationTime)
    }
    var reductionPercent by remember(settings.reductionPercent) {
        mutableStateOf(settings.reductionPercent)
    }
    var collectionResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val chooseTime = showTimePicker ?: { current: LocalTime, chosen: (LocalTime) -> Unit ->
        TimePickerDialog(
            context,
            { _, hour, minute -> chosen(LocalTime.of(hour, minute)) },
            current.hour,
            current.minute,
            true,
        ).show()
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium)
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
            if (!notificationsEnabled) {
                Text("Notifications are off. Turn them on to get goal reminders.")
                Button(onClick = openNotificationSettings) {
                    Text("Open notification settings")
                }
            }
            Text("Week starts on")
            Button(onClick = { weekMenuExpanded = true }) {
                Text(weekStartDay.getDisplayName(TextStyle.FULL, Locale.getDefault()))
            }
            DropdownMenu(
                expanded = weekMenuExpanded,
                onDismissRequest = { weekMenuExpanded = false },
            ) {
                DayOfWeek.entries.forEach { day ->
                    DropdownMenuItem(
                        text = { Text(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                        onClick = {
                            weekStartDay = day
                            weekMenuExpanded = false
                            onWeekStartDayChange(day)
                        },
                    )
                }
            }
            Text("Notification time")
            Button(
                onClick = {
                    chooseTime(notificationTime) { chosen ->
                        notificationTime = chosen
                        onNotificationTimeChange(chosen)
                    }
                },
            ) {
                Text(notificationTime.format(settingsTimeFormatter))
            }
            Text("Recommendation reduction")
            Text("$reductionPercent%")
            Button(
                onClick = {
                    reductionPercent = (reductionPercent - 5).coerceAtLeast(0)
                    onReductionPercentChange(reductionPercent)
                },
                enabled = reductionPercent > 0,
            ) { Text("Decrease reduction") }
            Button(
                onClick = {
                    reductionPercent = (reductionPercent + 5).coerceAtMost(100)
                    onReductionPercentChange(reductionPercent)
                },
                enabled = reductionPercent < 100,
            ) { Text("Increase reduction") }
            Text(if (hasUsageAccess) "Usage access: allowed" else "Usage access: missing")
            Button(onClick = openUsageSettings) { Text("Open usage access settings") }
            Button(
                onClick = {
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { collectNow() }
                        collectionResult = result.displayText()
                    }
                },
            ) {
                Text("Collect now")
            }
            collectionResult?.let { Text(it) }
            Button(
                onClick = {
                    launchExport(createExportIntent(ExportFormat.JSON, today()))
                },
            ) { Text("Export JSON") }
            Button(
                onClick = {
                    launchExport(createExportIntent(ExportFormat.CSV, today()))
                },
            ) { Text("Export CSV") }
            exportError?.let { Text(it) }
            Button(onClick = openImportDocument) { Text("Import JSON") }
            when (importStatus) {
                is ImportStatus.Success -> {
                    val result = importStatus.result
                    Text(
                        "Imported ${result.importedDays} ${dayWord(result.importedDays)}; " +
                            "skipped ${result.skippedDays} collected ${dayWord(result.skippedDays)}",
                    )
                    val importedWeekStart = result.weekStartDay.getDisplayName(
                        TextStyle.FULL,
                        Locale.getDefault(),
                    )
                    Text("File week starts on $importedWeekStart")
                    Button(
                        onClick = {
                            weekStartDay = result.weekStartDay
                            onWeekStartDayChange(result.weekStartDay)
                        },
                    ) {
                        Text("Use $importedWeekStart as week start")
                    }
                }
                is ImportStatus.Error -> Text("Import failed: ${importStatus.message}")
                null -> Unit
            }
            Button(onClick = onAbout) { Text(stringResource(R.string.about)) }
        }
    }
}

private fun dayWord(count: Int): String = if (count == 1) "day" else "days"

private fun CollectResult.displayText(): String = when (this) {
    is CollectResult.Collected ->
        "Collected ${dates.size} days through ${dates.maxOrNull()}"
    CollectResult.NothingToDo -> "Everything is already up to date"
    CollectResult.NotUnlocked -> "Collection will retry after the device is unlocked"
}

@Composable
internal fun HomeWithSettings(
    settings: Settings,
    hasUsageAccess: Boolean = true,
    notificationsEnabled: Boolean = true,
    onWeekStartDayChange: (DayOfWeek) -> Unit = {},
    onNotificationTimeChange: (LocalTime) -> Unit = {},
    onReductionPercentChange: (Int) -> Unit = {},
    collectNow: () -> CollectResult = { CollectResult.NothingToDo },
    openUsageSettings: () -> Unit = {},
    openNotificationSettings: () -> Unit = {},
    launchExport: (Intent) -> Unit = {},
    exportError: String? = null,
    openImportDocument: () -> Unit = {},
    importStatus: ImportStatus? = null,
    mainContent: @Composable (Settings) -> Unit,
) {
    var showingSettings by remember { mutableStateOf(false) }
    var showingAbout by remember { mutableStateOf(false) }
    if (showingAbout) {
        AboutScreen(onBack = { showingAbout = false })
    } else if (showingSettings) {
        SettingsScreen(
            settings = settings,
            hasUsageAccess = hasUsageAccess,
            notificationsEnabled = notificationsEnabled,
            onBack = { showingSettings = false },
            onWeekStartDayChange = onWeekStartDayChange,
            onNotificationTimeChange = onNotificationTimeChange,
            onReductionPercentChange = onReductionPercentChange,
            collectNow = collectNow,
            openUsageSettings = openUsageSettings,
            openNotificationSettings = openNotificationSettings,
            launchExport = launchExport,
            exportError = exportError,
            openImportDocument = openImportDocument,
            importStatus = importStatus,
            onAbout = { showingAbout = true },
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Button(onClick = { showingSettings = true }) {
                Text(stringResource(R.string.settings))
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                mainContent(settings)
            }
        }
    }
}
