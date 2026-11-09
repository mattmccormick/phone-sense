package ca.mattmccormick.phone_sense

import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.budget.weekStart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class HomeChartModel(
    val dates: List<LocalDate>,
    val dailyMinutes: List<Int?>,
    val goalMinutes: List<Int?>,
    val weeklyAverageMinutes: List<Double?>,
    val weekBoundaryPositions: List<Int>,
    val includesToday: Boolean,
) {
    val hasData: Boolean
        get() = dailyMinutes.any { it != null } || goalMinutes.any { it != null }
}

// Page 0 ends today; every earlier page ends on the last day of a week, so it holds
// six whole weeks.
internal fun chartWindowEnd(today: LocalDate, weekStartDay: DayOfWeek, page: Int): LocalDate {
    require(page >= 0) { "A chart page cannot be negative: $page" }
    if (page == 0) return today
    return weekStart(today, weekStartDay).minusDays(1 + 7L * (page - 1))
}

// Named off the window rather than the page, so the label always describes the chart beside it
// while a page change is still loading. The day format matches the chart's week labels.
internal fun chartRangeLabel(chart: HomeChartModel, locale: Locale): String {
    if (chart.includesToday) return "Last 6 weeks"
    val format = DateTimeFormatter.ofPattern("MMM d", locale)
    return "${chart.dates.first().format(format)} to ${chart.dates.last().format(format)}"
}

internal fun chartModel(
    days: List<DailyUsage>,
    goals: List<Goal>,
    weekStartDay: DayOfWeek,
    end: LocalDate,
    today: LocalDate,
): HomeChartModel {
    val daysByDate = days.associateBy(DailyUsage::date)
    val goalsByWeek = goals.associateBy(Goal::weekStart)
    val averagesByWeek = days.groupBy { weekStart(it.date, weekStartDay) }
        .mapValues { (_, weekDays) -> weekDays.map(DailyUsage::totalMinutes).average() }
    val dates = (41L downTo 0L).map(end::minusDays)
    return HomeChartModel(
        dates = dates,
        dailyMinutes = dates.map { daysByDate[it]?.totalMinutes },
        goalMinutes = dates.map { goalsByWeek[weekStart(it, weekStartDay)]?.minutes },
        weeklyAverageMinutes = dates.map { averagesByWeek[weekStart(it, weekStartDay)] },
        weekBoundaryPositions = dates.indices.filter { dates[it].dayOfWeek == weekStartDay },
        includesToday = end == today,
    )
}
