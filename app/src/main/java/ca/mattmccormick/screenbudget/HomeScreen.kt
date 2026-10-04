package ca.mattmccormick.screenbudget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.mattmccormick.screenbudget.budget.displayBudget
import ca.mattmccormick.screenbudget.budget.distractionMinutes
import ca.mattmccormick.screenbudget.budget.formatHoursMinutes
import ca.mattmccormick.screenbudget.budget.remainingDailyBudget
import ca.mattmccormick.screenbudget.budget.weekStart
import ca.mattmccormick.screenbudget.data.AppRuleDao
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.GoalDao
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.UsageDao
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
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
    today: LocalDate = LocalDate.now(),
    refreshKey: Any? = Unit,
) {
    val currentWeekStart = weekStart(today, settings.weekStartDay)
    var data by remember(usageDao, goalDao, appRuleDao) { mutableStateOf<HomeData?>(null) }

    LaunchedEffect(usageDao, goalDao, appRuleDao, currentWeekStart, today, refreshKey) {
        data = withContext(Dispatchers.IO) {
            val chartStart = today.minusDays(41)
            val firstChartWeek = weekStart(chartStart, settings.weekStartDay)
            val days = usageDao.daysBetween(firstChartWeek, today)
            val excluded = appRuleDao.excludedKeys().toSet()
            val normalizedDays = days.map {
                it.day.copy(totalMinutes = distractionMinutes(it.day, it.apps, excluded))
            }
            val goals = goalDao.between(
                firstChartWeek,
                currentWeekStart,
            )
            HomeData(
                goal = goals.firstOrNull { it.weekStart == currentWeekStart },
                usedSoFar = normalizedDays.filter { it.date >= currentWeekStart }
                    .sumOf { it.totalMinutes },
                chart = chartModel(normalizedDays, goals, settings.weekStartDay, today),
            )
        }
    }

    data?.let {
        HomeScreen(
            goal = it.goal,
            usedSoFar = it.usedSoFar,
            chart = it.chart,
            settings = settings,
            onSetGoal = onSetGoal,
            modifier = modifier,
            today = today,
        )
    }
}

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
