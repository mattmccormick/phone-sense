package ca.mattmccormick.phone_sense

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.mattmccormick.phone_sense.budget.weekStart
import ca.mattmccormick.phone_sense.data.AppRuleDao
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.GoalDao
import ca.mattmccormick.phone_sense.data.Settings
import ca.mattmccormick.phone_sense.data.UsageDao
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.abs

private val SWIPE_THRESHOLD = 40.dp

private data class HomeRefresh(
    val date: LocalDate,
    val snapshot: CurrentDayUsageSnapshot?,
    val week: HomeWeek,
)

@Composable
internal fun HomeRoute(
    usageDao: UsageDao,
    goalDao: GoalDao,
    appRuleDao: AppRuleDao,
    settings: Settings,
    modifier: Modifier = Modifier,
    readCurrentDay: (ZoneId) -> CurrentDayUsageSnapshotResult = {
        CurrentDayUsageSnapshotResult.Unavailable
    },
    today: () -> LocalDate = LocalDate::now,
    zone: ZoneId = ZoneId.systemDefault(),
    refreshKey: Any? = Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    var refresh by remember(usageDao, goalDao, appRuleDao) { mutableStateOf<HomeRefresh?>(null) }
    var refreshVersion by remember(usageDao, goalDao, appRuleDao) { mutableIntStateOf(0) }
    var chartPage by remember(usageDao, goalDao, appRuleDao) { mutableStateOf<HomeChartPage?>(null) }
    var page by remember(usageDao, goalDao, appRuleDao) { mutableIntStateOf(0) }
    var resumeVersion by remember { mutableIntStateOf(0) }
    var savingGoal by remember { mutableStateOf(false) }
    var goalError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val refreshMutex = remember { Mutex() }
    val currentReader by rememberUpdatedState(readCurrentDay)

    LaunchedEffect(zone) {
        while (true) {
            val midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
            delay(Duration.between(Instant.now(), midnight).toMillis().coerceAtLeast(1))
            resumeVersion++
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeVersion++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(
        usageDao,
        goalDao,
        appRuleDao,
        settings.weekStartDay,
        refreshKey,
        resumeVersion,
    ) {
        val previousSnapshot = refresh?.snapshot
        val reader = currentReader
        val refreshed = withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                val result = reader(zone)
                val refreshDate = when (result) {
                    is CurrentDayUsageSnapshotResult.Available -> result.snapshot.date
                    CurrentDayUsageSnapshotResult.Unavailable -> today()
                }
                val currentSnapshot = when {
                    result is CurrentDayUsageSnapshotResult.Available -> result.snapshot
                    previousSnapshot?.date == refreshDate -> previousSnapshot
                    else -> null
                }
                HomeRefresh(
                    date = refreshDate,
                    snapshot = currentSnapshot,
                    week = loadHomeWeek(
                        usageDao, goalDao, appRuleDao, settings.weekStartDay, refreshDate, currentSnapshot,
                    ),
                )
            }
        }
        refresh = refreshed
        refreshVersion++
    }

    // The counter rather than refresh itself: a refresh that leaves the week unchanged still has
    // to redraw the chart, because an import can rewrite any day the window covers.
    LaunchedEffect(refreshVersion, page) {
        val loaded = refresh ?: return@LaunchedEffect
        chartPage = withContext(Dispatchers.IO) {
            refreshMutex.withLock {
                loadHomeChart(
                    usageDao, goalDao, appRuleDao, settings.weekStartDay,
                    chartWindowEnd(loaded.date, settings.weekStartDay, page), loaded.date, loaded.snapshot,
                )
            }
        }
    }

    val loaded = refresh
    val loadedChart = chartPage
    if (loaded != null && loadedChart != null) {
        HomeScreen(
            goal = loaded.week.goal,
            usedSoFar = loaded.week.usedSoFar,
            usedToday = loaded.week.usedToday,
            chart = loadedChart.chart,
            page = page,
            earlierDataAvailable = loadedChart.earlierDataAvailable,
            onPageChange = { page = it },
            settings = settings,
            onSetGoal = { goal ->
                savingGoal = true
                goalError = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { goalDao.insert(goal) } }
                    if (result.isSuccess) {
                        refresh = refresh?.let { it.copy(week = it.week.copy(goal = goal)) }
                        resumeVersion++
                    } else goalError = "Could not save your goal. Please try again."
                    savingGoal = false
                }
            },
            savingGoal = savingGoal,
            goalError = goalError,
            modifier = modifier,
            today = loaded.date,
        )
    }
}

@Composable
internal fun HomeScreen(
    goal: Goal?,
    usedSoFar: Int,
    settings: Settings,
    onSetGoal: (Goal) -> Unit,
    modifier: Modifier = Modifier,
    savingGoal: Boolean = false,
    goalError: String? = null,
    today: LocalDate = LocalDate.now(),
    chart: HomeChartModel = chartModel(
        days = emptyList(),
        goals = listOfNotNull(goal),
        weekStartDay = settings.weekStartDay,
        end = today,
        today = today,
    ),
    usedToday: Int? = null,
    page: Int = 0,
    earlierDataAvailable: Boolean = false,
    onPageChange: (Int) -> Unit = {},
) {
    val currentWeekStart = weekStart(today, settings.weekStartDay)

    val allowance = todayAllowance(goal, usedSoFar, usedToday, today, settings.weekStartDay)
    val locale = LocalConfiguration.current.locales[0]
    val colors = MaterialTheme.colorScheme
    fun showEarlier() { if (earlierDataAvailable) onPageChange(page + 1) }
    fun showLater() { if (page > 0) onPageChange(page - 1) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (goal == null) {
            WeeklyGoalEntry(currentWeekStart, onSetGoal, savingGoal, goalError)
        } else {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("This week’s goal", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Text("${goal.minutes}", style = MaterialTheme.typography.headlineLarge)
                    Text("min/day average", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Daily allowance", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Text("$allowance", style = MaterialTheme.typography.headlineLarge)
                    Text("min/day", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            Text("Allowance adjusted for earlier usage this week.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        HorizontalDivider(color = colors.outlineVariant)
        HomeToday(usedToday, allowance)
        HorizontalDivider(color = colors.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your trend", style = MaterialTheme.typography.titleLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(chartRangeLabel(chart, locale),
                            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        if (page > 0) {
                            TextButton(onClick = { onPageChange(0) }) { Text("Back to this week") }
                        }
                    }
                }
                ChevronButton(back = true, description = "Earlier week", enabled = earlierDataAvailable,
                    onClick = { showEarlier() }, fontSize = 24.sp)
                ChevronButton(back = false, description = "Later week", enabled = page > 0,
                    onClick = { showLater() }, fontSize = 24.sp)
            }
            Text("Counted minutes per day", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            HomeChart(chart, Modifier.pointerInput(page, earlierDataAvailable) {
                val threshold = SWIPE_THRESHOLD.toPx()
                var dragged = 0f
                var paged = false
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f; paged = false },
                    onHorizontalDrag = { _, delta ->
                        dragged += delta
                        if (!paged && abs(dragged) >= threshold) {
                            paged = true
                            if (dragged > 0) showEarlier() else showLater()
                        }
                    },
                )
            })
        }
    }
}
