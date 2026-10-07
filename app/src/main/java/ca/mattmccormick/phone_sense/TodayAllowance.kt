package ca.mattmccormick.phone_sense

import ca.mattmccormick.phone_sense.budget.displayBudget
import ca.mattmccormick.phone_sense.budget.remainingDailyBudget
import ca.mattmccormick.phone_sense.budget.weekStart
import ca.mattmccormick.phone_sense.data.Goal
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
