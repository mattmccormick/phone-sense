package ca.mattmccormick.screenbudget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ca.mattmccormick.screenbudget.data.AppUsage
import java.time.LocalDate

private data class ManualAppInput(val name: String = "", val minutes: String = "")

@Composable
internal fun ManualEntryForm(
    date: LocalDate,
    onSave: (Int, List<AppUsage>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var totalText by remember { mutableStateOf("") }
    var appInputs by remember { mutableStateOf(listOf(ManualAppInput())) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = totalText,
            onValueChange = {
                totalText = it
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Total minutes") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        appInputs.forEachIndexed { index, input ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = input.name,
                    onValueChange = { name ->
                        appInputs = appInputs.updated(index, input.copy(name = name))
                        error = null
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("App name") },
                )
                OutlinedTextField(
                    value = input.minutes,
                    onValueChange = { minutes ->
                        appInputs = appInputs.updated(index, input.copy(minutes = minutes))
                        error = null
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("App minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        TextButton(onClick = { appInputs = appInputs + ManualAppInput() }) {
            Text("Add app")
        }
        error?.let { Text(it) }
        Button(
            onClick = {
                val total = totalText.toIntOrNull()
                val enteredApps = appInputs.filter { it.name.isNotBlank() || it.minutes.isNotBlank() }
                val apps = enteredApps.mapNotNull { input ->
                    val minutes = input.minutes.toIntOrNull()
                    if (input.name.isBlank() || minutes == null || minutes <= 0) null
                    else AppUsage(date, "label:${input.name.trim()}", minutes)
                }
                error = when {
                    total == null || total <= 0 -> "Enter a positive total"
                    apps.size != enteredApps.size -> "Enter an app name and positive minutes"
                    apps.sumOf(AppUsage::minutes) > total -> "App minutes cannot exceed total minutes"
                    else -> null
                }
                if (error == null) onSave(total!!, apps)
            },
        ) {
            Text("Save")
        }
    }
}

private fun List<ManualAppInput>.updated(index: Int, value: ManualAppInput) =
    toMutableList().also { it[index] = value }
