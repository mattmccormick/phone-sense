package ca.mattmccormick.phone_sense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ca.mattmccormick.phone_sense.data.Goal
import java.time.LocalDate

@Composable
internal fun WeeklyGoalEntry(
    weekStart: LocalDate,
    onSetGoal: (Goal) -> Unit,
    saving: Boolean = false,
    error: String? = null,
) {
    var minutesText by rememberSaveable(weekStart) { mutableStateOf("") }
    var invalid by rememberSaveable(weekStart) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Set this week’s goal", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = minutesText,
            onValueChange = { minutesText = it; invalid = false },
            label = { Text("Daily average (minutes)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = invalid,
            supportingText = if (invalid) ({ Text("Enter a positive number of minutes") }) else null,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = !saving, onClick = {
            val minutes = minutesText.toIntOrNull()
            if (minutes == null || minutes <= 0) invalid = true
            else onSetGoal(Goal(weekStart, minutes))
        }) { Text(if (saving) "Saving…" else "Set goal") }
    }
}
