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
import ca.mattmccormick.screenbudget.budget.formatHoursMinutes
import ca.mattmccormick.screenbudget.budget.remainingDailyBudget
import ca.mattmccormick.screenbudget.budget.weekStart
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
)

@Composable
internal fun HomeRoute(
    usageDao: UsageDao,
    goalDao: GoalDao,
    settings: Settings,
    onSetGoal: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    refreshKey: Any? = Unit,
) {
    val currentWeekStart = weekStart(today, settings.weekStartDay)
    var data by remember(usageDao, goalDao) { mutableStateOf<HomeData?>(null) }

    LaunchedEffect(usageDao, goalDao, currentWeekStart, today, refreshKey) {
        data = withContext(Dispatchers.IO) {
            HomeData(
                goal = goalDao.forWeek(currentWeekStart),
                usedSoFar = usageDao.daysBetween(currentWeekStart, today)
                    .sumOf { it.day.totalMinutes },
            )
        }
    }

    data?.let {
        HomeScreen(
            goal = it.goal,
            usedSoFar = it.usedSoFar,
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
    }
}
