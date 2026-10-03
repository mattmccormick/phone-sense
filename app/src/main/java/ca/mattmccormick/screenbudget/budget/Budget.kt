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
