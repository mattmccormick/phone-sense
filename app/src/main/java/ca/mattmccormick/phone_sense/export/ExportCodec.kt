package ca.mattmccormick.phone_sense.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val EXPORT_VERSION = 1

@Serializable
data class ExportDocument(
    val version: Int = EXPORT_VERSION,
    val exportedAt: String,
    val weekStartDay: String,
    val dailyUsage: List<ExportDailyUsage>,
    val appUsage: List<ExportAppUsage>,
    val appRules: List<ExportAppRule>,
    val goals: List<ExportGoal>,
)

@Serializable
data class ExportDailyUsage(
    val date: String,
    val totalMinutes: Int,
    val source: String,
    val includesAllApps: Boolean = false,
)

@Serializable
data class ExportAppUsage(
    val date: String,
    val appKey: String,
    val minutes: Int,
)

@Serializable
data class ExportAppRule(
    val appKey: String,
    val label: String,
    val excluded: Boolean,
)

@Serializable
data class ExportGoal(
    val weekStart: String,
    val minutes: Int,
)

private val exportJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

fun encodeExport(document: ExportDocument): String = exportJson.encodeToString(document)

fun decodeExport(encoded: String): ExportDocument {
    val document = exportJson.decodeFromString<ExportDocument>(encoded)
    require(document.version == EXPORT_VERSION) {
        "Unsupported export version ${document.version}; expected $EXPORT_VERSION"
    }
    return document
}

fun toCsv(
    dailyUsage: List<ExportDailyUsage>,
    appUsage: List<ExportAppUsage>,
): String {
    val appKeys = appUsage.map(ExportAppUsage::appKey).distinct().sorted()
    val minutesByDayAndApp = appUsage.associate { (it.date to it.appKey) to it.minutes }

    return buildString {
        appendCsvRow(listOf("date", "totalMinutes") + appKeys)
        dailyUsage.forEach { day ->
            appendCsvRow(
                listOf(day.date, day.totalMinutes.toString()) +
                    appKeys.map { appKey ->
                        minutesByDayAndApp[day.date to appKey]?.toString().orEmpty()
                    },
            )
        }
    }
}

private fun StringBuilder.appendCsvRow(cells: List<String>) {
    append(cells.joinToString(",", transform = ::escapeCsvCell))
    append('\n')
}

private fun escapeCsvCell(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
