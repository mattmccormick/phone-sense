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

internal data class HomeChartPage(
    val chart: HomeChartModel,
    val earlierDataAvailable: Boolean,
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
    val currentWeek = normalizedDays(
        usageDao,
        appRuleDao,
        currentWeekStart,
        refreshDate,
        currentSnapshot,
    )
    return HomeData(
        goal = goalDao.forWeek(currentWeekStart),
        usedSoFar = currentWeek.sumOf(DailyUsage::totalMinutes),
        chart = loadHomeChart(
            usageDao,
            goalDao,
            appRuleDao,
            weekStartDay,
            refreshDate,
            refreshDate,
            currentSnapshot,
        ).chart,
    )
}

// Call off the main thread. The window is the 42 days ending on end; see chartWindowEnd.
internal fun loadHomeChart(
    usageDao: UsageDao,
    goalDao: GoalDao,
    appRuleDao: AppRuleDao,
    weekStartDay: DayOfWeek,
    end: LocalDate,
    today: LocalDate,
    currentSnapshot: CurrentDayUsageSnapshot?,
): HomeChartPage {
    val start = end.minusDays(41)
    // The first week's average covers the whole week, including days left of the chart.
    val firstChartWeek = weekStart(start, weekStartDay)
    val days = normalizedDays(usageDao, appRuleDao, firstChartWeek, end, currentSnapshot)
    val goals = goalDao.between(firstChartWeek, weekStart(end, weekStartDay))
    val earliestDate = usageDao.earliestDate()
    return HomeChartPage(
        chart = chartModel(days, goals, weekStartDay, end, today),
        earlierDataAvailable = earliestDate != null && earliestDate < start,
    )
}

private fun normalizedDays(
    usageDao: UsageDao,
    appRuleDao: AppRuleDao,
    start: LocalDate,
    end: LocalDate,
    currentSnapshot: CurrentDayUsageSnapshot?,
): List<DailyUsage> {
    val excluded = appRuleDao.excludedKeys().toSet()
    val snapshot = currentSnapshot?.takeIf { it.date in start..end }
    val collected = usageDao.daysBetween(start, end).map {
        it.day.copy(totalMinutes = distractionMinutes(it.day, it.apps, excluded))
    }.filterNot { it.date == snapshot?.date }
    val snapshotDay = snapshot?.let { current ->
        val day = DailyUsage(
            current.date,
            current.totalMillis.toMinutes(),
            Source.COLLECTED,
            current.capturedAt,
        )
        val apps = current.perPackageMillis.map { (appKey, millis) ->
            AppUsage(current.date, appKey, millis.toMinutes())
        }
        day.copy(totalMinutes = distractionMinutes(day, apps, excluded))
    }
    return collected + listOfNotNull(snapshotDay)
}

private fun Long.toMinutes(): Int = (this / 60_000L).toInt()
