package ca.mattmccormick.screenbudget

import android.app.Activity
import android.content.Intent
import android.net.Uri
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.UsageDatabase
import ca.mattmccormick.screenbudget.export.ExportAppRule
import ca.mattmccormick.screenbudget.export.ExportAppUsage
import ca.mattmccormick.screenbudget.export.ExportDailyUsage
import ca.mattmccormick.screenbudget.export.ExportDocument
import ca.mattmccormick.screenbudget.export.ExportGoal
import ca.mattmccormick.screenbudget.export.encodeExport
import ca.mattmccormick.screenbudget.export.toCsv
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

internal enum class ExportFormat(val mimeType: String, val extension: String) {
    JSON("application/json", "json"),
    CSV("text/csv", "csv"),
}

internal fun createExportIntent(format: ExportFormat, date: LocalDate): Intent =
    Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(format.mimeType)
        .putExtra(Intent.EXTRA_TITLE, "screen-budget-$date.${format.extension}")

internal fun writeExport(
    destination: Uri,
    format: ExportFormat,
    database: UsageDatabase,
    settings: Settings,
    exportedAt: Instant = Instant.now(),
    openOutputStream: (Uri) -> OutputStream?,
) {
    val days = database.usageDao().allDays()
    val dailyUsage = days.map {
        ExportDailyUsage(
            date = it.day.date.toString(),
            totalMinutes = it.day.totalMinutes,
            source = it.day.source.name,
        )
    }
    val appUsage = days.flatMap { day ->
        day.apps.sortedBy { it.appKey }.map {
            ExportAppUsage(it.date.toString(), it.appKey, it.minutes)
        }
    }
    val encoded = when (format) {
        ExportFormat.JSON -> encodeExport(
            ExportDocument(
                exportedAt = exportedAt.toString(),
                weekStartDay = settings.weekStartDay.name,
                dailyUsage = dailyUsage,
                appUsage = appUsage,
                appRules = database.appRuleDao().all().map {
                    ExportAppRule(it.appKey, it.label, it.excluded)
                },
                goals = database.goalDao().newestFirst().sortedBy { it.weekStart }.map {
                    ExportGoal(it.weekStart.toString(), it.minutes)
                },
            ),
        )
        ExportFormat.CSV -> toCsv(dailyUsage, appUsage)
    }

    requireNotNull(openOutputStream(destination)) { "Could not open export destination" }
        .bufferedWriter(Charsets.UTF_8)
        .use { it.write(encoded) }
}

internal fun handleExportResult(
    resultCode: Int,
    data: Intent?,
    write: (Uri) -> Unit,
): String? {
    if (resultCode != Activity.RESULT_OK) return null
    val destination = data?.data ?: return "Export failed: no destination was returned"
    return runCatching { write(destination) }
        .exceptionOrNull()
        ?.let { "Export failed: ${it.message ?: "could not write the file"}" }
}
