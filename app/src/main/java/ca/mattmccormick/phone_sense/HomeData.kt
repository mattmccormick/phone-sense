package ca.mattmccormick.phone_sense

import ca.mattmccormick.phone_sense.budget.distractionMinutes
import ca.mattmccormick.phone_sense.budget.weekStart
import ca.mattmccormick.phone_sense.data.AppRuleDao
import ca.mattmccormick.phone_sense.data.AppUsage
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.GoalDao
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDao
import java.time.DayOfWeek
import java.time.LocalDate

internal data class HomeData(
    val goal: Goal?,
    val usedSoFar: Int,
    val chart: HomeChartModel,
)

// Call off the main thread. Both the screen and widgets use the same normalized data.
internal fun loadHomeData(
    usageDao: UsageDao,
    goalDao: GoalDao,
    appRuleDao: AppRuleDao,
    weekStartDay: DayOfWeek,
    refreshDate: LocalDate,
    currentSnapshot: CurrentDayUsageSnapshot?,
): HomeData {
    val currentWeekStart = weekStart(refreshDate, weekStartDay)
    val chartStart = refreshDate.minusDays(41)
    val firstChartWeek = weekStart(chartStart, weekStartDay)
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
    return HomeData(
        goal = goals.firstOrNull { it.weekStart == currentWeekStart },
        usedSoFar = normalizedDays.filter { it.date >= currentWeekStart }
            .sumOf { it.totalMinutes },
        chart = chartModel(normalizedDays, goals, weekStartDay, refreshDate),
    )
}

private fun Long.toMinutes(): Int = (this / 60_000L).toInt()
