package ca.mattmccormick.phone_sense

import android.content.Intent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.mattmccormick.phone_sense.export.ImportStatus
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DataSettingsScreen(
    onBack: () -> Unit = {},
    collectNow: () -> CollectResult = { CollectResult.NothingToDo },
    launchExport: (Intent) -> Unit = {},
    exportError: String? = null,
    today: () -> LocalDate = LocalDate::now,
    openImportDocument: () -> Unit = {},
    importStatus: ImportStatus? = null,
) {
    var collectionResult by remember { mutableStateOf<String?>(null) }
    var collecting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    SettingsPage("Data", onBack) {
        SettingsRow("Export JSON", "Back up usage and goals") {
            launchExport(createExportIntent(ExportFormat.JSON, today()))
        }
        SettingsRow("Export CSV", "Open usage in a spreadsheet") {
            launchExport(createExportIntent(ExportFormat.CSV, today()))
        }
        exportError?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        SettingsRow("Import JSON", "Restore usage and goals", openImportDocument)
        when (importStatus) {
            is ImportStatus.Success -> {
                val result = importStatus.result
                Text("Imported ${result.importedDays} ${dayWord(result.importedDays)}; " +
                    "skipped ${result.skippedDays} collected ${dayWord(result.skippedDays)}",
                    Modifier.padding(16.dp))
            }
            is ImportStatus.Error -> Text("Import failed: ${importStatus.message}",
                Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
        SettingsRow("Collect now", if (collecting) "Collecting…" else collectionResult ?: "Refresh recorded usage") {
            if (!collecting) {
                collecting = true
                scope.launch {
                    collectionResult = withContext(Dispatchers.IO) {
                        runCatching { collectNow().displayText() }.getOrElse { "Could not collect usage. Please try again." }
                    }
                    collecting = false
                }
            }
        }
    }
}

private fun dayWord(count: Int): String = if (count == 1) "day" else "days"

private fun CollectResult.displayText(): String = when (this) {
    is CollectResult.Collected -> "Collected ${dates.size} days through ${dates.maxOrNull()}"
    CollectResult.NothingToDo -> "Everything is already up to date"
    CollectResult.NotUnlocked -> "Collection will retry after the device is unlocked"
}
