package ca.mattmccormick.phone_sense.export

import ca.mattmccormick.phone_sense.data.AppUsage
import ca.mattmccormick.phone_sense.data.AppRule
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

data class ImportResult(
    val importedDays: Int,
    val skippedDays: Int,
    val weekStartDay: DayOfWeek,
)

sealed interface ImportStatus {
    data class Success(val result: ImportResult) : ImportStatus
    data class Error(val message: String) : ImportStatus
}

class ImportService(private val database: UsageDatabase) {
    fun importJson(encoded: String): ImportResult {
        val document = decodeExport(encoded)
        val collectedAt = Instant.parse(document.exportedAt)
        val weekStartDay = DayOfWeek.valueOf(document.weekStartDay)
        val days = document.dailyUsage.map { exported ->
            val date = LocalDate.parse(exported.date)
            DailyUsage(date, exported.totalMinutes, Source.IMPORTED, collectedAt, exported.includesAllApps) to
                document.appUsage
                    .filter { it.date == exported.date }
                    .map { AppUsage(date, it.appKey, it.minutes) }
        }
        val rules = document.appRules.map { AppRule(it.appKey, it.label, it.excluded) }
        val goals = document.goals.map { Goal(LocalDate.parse(it.weekStart), it.minutes) }

        var importedDays = 0
        var skippedDays = 0
        database.runInTransaction {
            days.forEach { (day, apps) ->
                if (database.usageDao().day(day.date)?.day?.source == Source.COLLECTED) {
                    skippedDays++
                } else {
                    database.usageDao().insert(day, apps)
                    importedDays++
                }
            }
            rules.forEach(database.appRuleDao()::insert)
            goals.forEach(database.goalDao()::insert)
        }
        return ImportResult(importedDays, skippedDays, weekStartDay)
    }
}
