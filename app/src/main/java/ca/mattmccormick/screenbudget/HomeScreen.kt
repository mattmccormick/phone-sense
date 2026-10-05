package ca.mattmccormick.screenbudget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.mattmccormick.screenbudget.budget.displayBudget
import ca.mattmccormick.screenbudget.budget.distractionMinutes
import ca.mattmccormick.screenbudget.budget.formatHoursMinutes
import ca.mattmccormick.screenbudget.budget.remainingDailyBudget
import ca.mattmccormick.screenbudget.budget.weekStart
import ca.mattmccormick.screenbudget.data.AppRuleDao
import ca.mattmccormick.screenbudget.data.AppUsage
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.GoalDao
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDao
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private data class HomeData(
    val goal: Goal?,
    val usedSoFar: Int,
    val chart: HomeChartModel,
)

@Composable
internal fun HomeRoute(
    usageDao: UsageDao,
    goalDao: GoalDao,
    appRuleDao: AppRuleDao,
    settings: Settings,
    onSetGoal: () -> Unit,
    modifier: Modifier = Modifier,
    readCurrentDay: (ZoneId) -> CurrentDayUsageSnapshotResult = {
        CurrentDayUsageSnapshotResult.Unavailable
    },
    today: () -> LocalDate = LocalDate::now,
    zone: ZoneId = ZoneId.systemDefault(),
    refreshKey: Any? = Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    var data by remember(usageDao, goalDao, appRuleDao) { mutableStateOf<HomeData?>(null) }
    var snapshot by remember { mutableStateOf<CurrentDayUsageSnapshot?>(null) }
    var resumeVersion by remember { mutableIntStateOf(0) }
    val refreshMutex = remember { Mutex() }
    val currentReader by rememberUpdatedState(readCurrentDay)

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
        val previousSnapshot = snapshot
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
                val currentWeekStart = weekStart(refreshDate, settings.weekStartDay)
                val chartStart = refreshDate.minusDays(41)
                val firstChartWeek = weekStart(chartStart, settings.weekStartDay)
                val days = usageDao.daysBetween(firstChartWeek, refreshDate)
                val excluded = appRuleDao.excludedKeys().toSet()
                val normalizedDays = days.map {
                    it.day.copy(totalMinutes = distractionMinutes(it.day, it.apps, excluded))
                }.filterNot { it.date == currentSnapshot?.date }.toMutableList()
                currentSnapshot?.let { current ->
                    val day = DailyUsage(
                        current.date,
                        current.totalMillis.toMinutes(),
                        Source.COLLECTED,
                        current.capturedAt,
                    )
                    val apps = current.perPackageMillis.map { (appKey, millis) ->
                        AppUsage(current.date, appKey, millis.toMinutes())
                    }
                    normalizedDays += day.copy(
                        totalMinutes = distractionMinutes(day, apps, excluded),
                    )
                }
                val goals = goalDao.between(
                    firstChartWeek,
                    currentWeekStart,
                )
                currentSnapshot to HomeData(
                    goal = goals.firstOrNull { it.weekStart == currentWeekStart },
                    usedSoFar = normalizedDays.filter { it.date >= currentWeekStart }
                        .sumOf { it.totalMinutes },
                    chart = chartModel(normalizedDays, goals, settings.weekStartDay, refreshDate),
                )
            }
        }
        snapshot = refreshed.first
        data = refreshed.second
    }

    data?.let { homeData ->
        HomeScreen(
            goal = homeData.goal,
            usedSoFar = homeData.usedSoFar,
            chart = homeData.chart,
            settings = settings,
            onSetGoal = onSetGoal,
            modifier = modifier,
            today = homeData.chart.dates.last(),
        )
    }
}

private fun Long.toMinutes(): Int = (this / 60_000L).toInt()

@Composable
internal fun HomeScreen(
    goal: Goal?,
    usedSoFar: Int,
    settings: Settings,
    onSetGoal: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    chart: HomeChartModel = chartModel(
        days = emptyList(),
        goals = listOfNotNull(goal),
        weekStartDay = settings.weekStartDay,
        end = today,
    ),
) {
    val currentWeekStart = weekStart(today, settings.weekStartDay)
    val dayIndex = ChronoUnit.DAYS.between(currentWeekStart, today).toInt()

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        if (goal == null) {
            Button(onClick = onSetGoal) {
                Text("Set a goal")
            }
        } else {
            val remaining = remainingDailyBudget(goal.minutes, usedSoFar, dayIndex)
            val budget = displayBudget(goal.minutes, remaining)
            Text("Today's remaining budget", style = MaterialTheme.typography.headlineMedium)
            Text(formatHoursMinutes(budget), style = MaterialTheme.typography.displayLarge)
            Text("Goal ${formatHoursMinutes(goal.minutes)}")
            Text("$usedSoFar minutes used this week")
            Text("Day ${dayIndex + 1} of 7")
        }
        Text("Last six weeks", style = MaterialTheme.typography.titleMedium)
        HomeChart(chart)
    }
}
