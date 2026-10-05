package ca.mattmccormick.screenbudget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.mattmccormick.screenbudget.data.AppRule
import ca.mattmccormick.screenbudget.data.AppRuleDao
import ca.mattmccormick.screenbudget.data.AppUsage
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.DayWithApps
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDao
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
    var date by remember(today) { mutableStateOf(today.minusDays(1)) }
    var day by remember { mutableStateOf<DayWithApps?>(null) }
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
        val (displayDay, storedExcludedKeys, refreshedSnapshot) = withContext(loadDispatcher) {
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
                        ),
                        it.perPackageMillis.mapNotNull { (appKey, millis) ->
                            val minutes = (millis / 60_000L).toInt()
                            if (minutes > 0) AppUsage(it.date, appKey, minutes) else null
                        },
                    )
                }
                Triple(liveDay ?: dao.day(date), appRuleDao.excludedKeys().toSet(), current)
            }
        }
        if (date == today) snapshot = refreshedSnapshot
        day = displayDay
        excludedKeys = storedExcludedKeys
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { date = date.minusDays(1) },
                    modifier = Modifier.semantics { contentDescription = "Previous day" },
                ) { Text("Back") }
                Text(text = date.toString(), style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = { date = date.plusDays(1) },
                    enabled = date < today,
                    modifier = Modifier.semantics { contentDescription = "Next day" },
                ) { Text("Forward") }
            }

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
                    Text(
                        text = "${storedDay.day.totalMinutes} min",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(
                            items = storedDay.apps.sortedByDescending { it.minutes },
                            key = { it.appKey },
                        ) { usage ->
                            val appInfo = remember(usage.appKey) {
                                if (usage.appKey.startsWith("label:")) {
                                    AppInfo(usage.appKey.removePrefix("label:"), null)
                                } else {
                                    appInfoSource.resolve(usage.appKey)
                                }
                            }
                            AppUsageRow(
                                appInfo = appInfo,
                                minutes = usage.minutes,
                                excluded = usage.appKey in excludedKeys,
                                onExcludedChange = { excluded ->
                                    excludedKeys = if (excluded) {
                                        excludedKeys + usage.appKey
                                    } else {
                                        excludedKeys - usage.appKey
                                    }
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            appRuleDao.insert(
                                                AppRule(usage.appKey, appInfo.label, excluded),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    appInfo: AppInfo,
    minutes: Int,
    excluded: Boolean,
    onExcludedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = "${appInfo.label} icon" }
        if (appInfo.icon != null) {
            val painter = remember(appInfo.icon) {
                BitmapPainter(appInfo.icon.toBitmap().asImageBitmap())
            }
            Image(painter = painter, contentDescription = null, modifier = iconModifier)
        } else {
            Box(
                modifier = iconModifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(appInfo.label.take(1))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(appInfo.label, modifier = Modifier.weight(1f))
        Text("$minutes min")
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = excluded,
            onCheckedChange = onExcludedChange,
            modifier = Modifier.semantics {
                contentDescription = "Exclude ${appInfo.label}"
            },
        )
    }
}
