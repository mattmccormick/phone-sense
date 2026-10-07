package ca.mattmccormick.phone_sense

import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.budget.weekStart
import java.time.DayOfWeek
import java.time.LocalDate

internal data class HomeChartModel(
    val dates: List<LocalDate>,
    val dailyMinutes: List<Int?>,
    val goalMinutes: List<Int?>,
    val weeklyAverageMinutes: List<Double?>,
    val weekBoundaryPositions: List<Int>,
)

internal fun chartModel(
    days: List<DailyUsage>,
    goals: List<Goal>,
    weekStartDay: DayOfWeek,
    end: LocalDate,
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
    )
}
