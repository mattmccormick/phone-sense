package ca.mattmccormick.screenbudget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.GoalDao
import ca.mattmccormick.screenbudget.data.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun GoalsRoute(
    goalDao: GoalDao,
    settings: Settings,
    today: LocalDate = LocalDate.now(),
) {
    var goals by remember(goalDao) { mutableStateOf(emptyList<Goal>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(goalDao) {
        goals = withContext(Dispatchers.IO) { goalDao.newestFirst() }
    }

    GoalsScreen(
        goals = goals,
        settings = settings,
        today = today,
        onSetGoal = { goal ->
            scope.launch {
                goals = withContext(Dispatchers.IO) {
                    goalDao.insert(goal)
                    goalDao.newestFirst()
                }
            }
        },
    )
}

@Composable
internal fun GoalsScreen(
    goals: List<Goal>,
    settings: Settings,
    onSetGoal: (Goal) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var minutesText by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf(false) }
    val currentWeek = currentWeekStart(today, settings.weekStartDay)
    val validationMessage: (@Composable () -> Unit)? = if (validationError) {
        { Text("Enter a positive number of minutes") }
    } else {
        null
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Goals", style = MaterialTheme.typography.headlineMedium)
            Text("Week of $currentWeek", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = minutesText,
                onValueChange = {
                    minutesText = it
                    validationError = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Daily minutes") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = validationError,
                supportingText = validationMessage,
            )
            Button(
                onClick = {
                    val minutes = minutesText.toIntOrNull()
                    if (minutes == null || minutes <= 0) {
                        validationError = true
                    } else {
                        onSetGoal(Goal(currentWeek, minutes))
                        minutesText = ""
                    }
                },
            ) {
                Text("Set goal")
            }
            goals.sortedByDescending(Goal::weekStart).forEach { goal ->
                Text("${goal.weekStart}: ${goal.minutes} minutes")
            }
        }
    }
}

internal fun currentWeekStart(today: LocalDate, weekStartDay: DayOfWeek): LocalDate {
    val daysSinceWeekStart = (today.dayOfWeek.value - weekStartDay.value + 7) % 7
    return today.minusDays(daysSinceWeekStart.toLong())
}
