package ca.mattmccormick.screenbudget.budget

import ca.mattmccormick.screenbudget.data.AppUsage
import ca.mattmccormick.screenbudget.data.DailyUsage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

fun distractionMinutes(
    day: DailyUsage,
    apps: List<AppUsage>,
    excluded: Set<String>,
): Int = (day.totalMinutes - apps.filter { it.appKey in excluded }.sumOf(AppUsage::minutes))
    .coerceAtLeast(0)

fun weekStart(date: LocalDate, weekStartDay: DayOfWeek): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(weekStartDay))

fun weekDays(weekStart: LocalDate): List<LocalDate> =
    (0L..6L).map(weekStart::plusDays)

fun remainingDailyBudget(goalMinutes: Int, usedSoFar: Int, dayIndex: Int): Int =
    ((goalMinutes * 7 - usedSoFar) / (7 - dayIndex)).coerceAtLeast(0)

fun displayBudget(goalMinutes: Int, remainingBudget: Int): Int =
    minOf(goalMinutes, remainingBudget)

fun formatHoursMinutes(minutes: Int): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return "${hours.toString().padStart(2, '0')}:${remainingMinutes.toString().padStart(2, '0')}"
}
