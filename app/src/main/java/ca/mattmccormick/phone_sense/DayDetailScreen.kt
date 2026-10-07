package ca.mattmccormick.phone_sense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.mattmccormick.phone_sense.budget.distractionMinutes
import ca.mattmccormick.phone_sense.data.AppRule
import ca.mattmccormick.phone_sense.data.AppRuleDao
import ca.mattmccormick.phone_sense.data.AppUsage
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.DayWithApps
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDao
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
fun DayDetailScreen(
    dao: UsageDao,
    appRuleDao: AppRuleDao,
    appInfoSource: AppInfoSource,
    today: LocalDate = LocalDate.now(),
    refreshKey: Int = 0,
    loadDispatcher: CoroutineDispatcher = Dispatchers.IO,
    modifier: Modifier = Modifier,
    readCurrentDay: (ZoneId) -> CurrentDayUsageSnapshotResult = {
        CurrentDayUsageSnapshotResult.Unavailable
    },
    zone: ZoneId = ZoneId.systemDefault(),
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    var date by remember(today) { mutableStateOf(today) }
    var day by remember { mutableStateOf<DayWithApps?>(null) }
    var rules by remember { mutableStateOf(emptyList<AppRule>()) }
    var excludedKeys by remember { mutableStateOf(emptySet<String>()) }
    var loaded by remember { mutableStateOf(false) }
    var enteringManual by remember(date) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var snapshot by remember(today) { mutableStateOf<CurrentDayUsageSnapshot?>(null) }
    var resumeVersion by remember { mutableIntStateOf(0) }
    val currentReader by rememberUpdatedState(readCurrentDay)
    val refreshMutex = remember { Mutex() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(date, today, dao, appRuleDao, refreshKey, resumeVersion, zone) {
        loaded = false
        val reader = currentReader
        val previousSnapshot = snapshot
        val (displayDay, storedRules, refreshedSnapshot) = withContext(loadDispatcher) {
            refreshMutex.withLock {
                val result = if (date == today) reader(zone) else null
                val current = when (result) {
                    is CurrentDayUsageSnapshotResult.Available -> result.snapshot.takeIf { it.date == date }
                    CurrentDayUsageSnapshotResult.Unavailable -> previousSnapshot?.takeIf { it.date == date }
                    null -> null
                }
                val liveDay = current?.let {
                    DayWithApps(
                        DailyUsage(
                            it.date,
                            (it.totalMillis / 60_000L).toInt(),
                            Source.COLLECTED,
                            it.capturedAt,
                            includesAllApps = true,
                        ),
                        it.perPackageMillis.mapNotNull { (appKey, millis) ->
                            val minutes = (millis / 60_000L).toInt()
                            if (minutes > 0) AppUsage(it.date, appKey, minutes) else null
                        },
                    )
                }
                Triple(liveDay ?: dao.day(date), appRuleDao.all(), current)
            }
        }
        if (date == today) snapshot = refreshedSnapshot
        day = displayDay
        rules = storedRules
        excludedKeys = storedRules.filter { it.excluded }.map { it.appKey }.toSet()
        loaded = true
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DayDateHeading(date, today, onDateChange = { date = it })

            if (loaded) {
                val storedDay = day
                if (storedDay == null) {
                    val manualDate = date
                    Text("Not collected yet", modifier = Modifier.padding(top = 32.dp))
                    if (enteringManual) {
                        ManualEntryForm(
                            modifier = Modifier.weight(1f),
                            onSave = { totalMinutes, apps ->
                                scope.launch {
                                    day = withContext(loadDispatcher) {
                                        dao.insert(
                                            DailyUsage(
                                                date = manualDate,
                                                totalMinutes = totalMinutes,
                                                source = Source.MANUAL,
                                                collectedAt = Instant.now(),
                                            ),
                                            apps,
                                        )
                                        dao.day(manualDate)
                                    }
                                    enteringManual = false
                                }
                            },
                            date = manualDate,
                        )
                    } else {
                        Button(onClick = { enteringManual = true }) { Text("Enter by hand") }
                    }
                } else {
                    val counted = distractionMinutes(storedDay.day, storedDay.apps, excludedKeys)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DayUsageMetric(
                            label = if (storedDay.day.includesAllApps) "Total usage · all apps" else "Recorded usage",
                            minutes = storedDay.day.totalMinutes,
                            description = "Total usage",
                            modifier = Modifier.weight(1f),
                        )
                        DayUsageMetric(
                            label = "Counted toward allowance",
                            minutes = counted,
                            description = "Counted toward allowance",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(
                            items = storedDay.apps.sortedByDescending { it.minutes },
                            key = { it.appKey },
                        ) { usage ->
                            val appInfo = remember(usage.appKey, rules) {
                                if (usage.appKey.startsWith("label:")) {
                                    AppInfo(usage.appKey.removePrefix("label:"), null)
                                } else {
                                    val resolved = appInfoSource.resolve(usage.appKey)
                                    val savedLabel = rules.firstOrNull { it.appKey == usage.appKey }?.label
                                    if (resolved.label == usage.appKey && savedLabel != null) {
                                        resolved.copy(label = savedLabel)
                                    } else resolved
                                }
                            }
                            AppUsageRow(
                                appInfo = appInfo,
                                minutes = usage.minutes,
                                excluded = usage.appKey in excludedKeys,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayUsageMetric(label: String, minutes: Int, description: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$minutes min", style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { contentDescription = "$description: $minutes minutes" })
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun AppUsageRow(
    appInfo: AppInfo,
    minutes: Int,
    excluded: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(appInfo)
        Spacer(Modifier.width(16.dp))
        Text(appInfo.label, modifier = Modifier.weight(1f))
        Text("$minutes min")
        Spacer(Modifier.width(16.dp))
        val badgeColor = if (excluded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        val color = if (excluded) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
        Canvas(Modifier.size(24.dp).semantics {
            contentDescription = "${appInfo.label}: " + if (excluded) "excluded from allowance" else "included in allowance"
        }) {
            drawCircle(badgeColor)
            if (excluded) {
                drawLine(color, Offset(size.width * 0.3f, center.y), Offset(size.width * 0.7f, center.y),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawLine(color, Offset(size.width * 0.27f, size.height * 0.5f), Offset(size.width * 0.43f, size.height * 0.67f),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.43f, size.height * 0.67f), Offset(size.width * 0.74f, size.height * 0.34f),
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
    }
}
