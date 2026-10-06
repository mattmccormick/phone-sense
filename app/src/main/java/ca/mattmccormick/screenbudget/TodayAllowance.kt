package ca.mattmccormick.screenbudget

import ca.mattmccormick.screenbudget.budget.displayBudget
import ca.mattmccormick.screenbudget.budget.remainingDailyBudget
import ca.mattmccormick.screenbudget.budget.weekStart
import ca.mattmccormick.screenbudget.data.Goal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal fun todayAllowance(
    goal: Goal?,
    usedSoFar: Int,
    usedToday: Int?,
    today: LocalDate,
    weekStartDay: DayOfWeek,
): Int? = goal?.let {
    val dayIndex = ChronoUnit.DAYS.between(weekStart(today, weekStartDay), today).toInt()
    // Today's usage must not reduce the allowance a second time.
    displayBudget(it.minutes, remainingDailyBudget(it.minutes, usedSoFar - (usedToday ?: 0), dayIndex))
}
