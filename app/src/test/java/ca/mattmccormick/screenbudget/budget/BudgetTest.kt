package ca.mattmccormick.screenbudget.budget

import ca.mattmccormick.screenbudget.data.AppUsage
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Source
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetTest {
    @Test
    fun distractionMinutesSubtractsExcludedAppsFromTotal() {
        val date = LocalDate.of(2026, 10, 3)
        val day = DailyUsage(date, totalMinutes = 120, Source.COLLECTED, Instant.EPOCH)
        val apps = listOf(
            AppUsage(date, "com.example.reader", 35),
            AppUsage(date, "com.example.video", 25),
            AppUsage(date, "com.example.messages", 10),
        )

        assertEquals(
            85,
            distractionMinutes(day, apps, excluded = setOf("com.example.reader")),
        )
    }

    @Test
    fun distractionMinutesNeverReturnsLessThanZero() {
        val date = LocalDate.of(2026, 10, 3)
        val day = DailyUsage(date, totalMinutes = 20, Source.COLLECTED, Instant.EPOCH)
        val apps = listOf(AppUsage(date, "com.example.reader", 25))

        assertEquals(
            0,
            distractionMinutes(day, apps, excluded = setOf("com.example.reader")),
        )
    }

    @Test
    fun weekStartFindsTheMostRecentConfiguredDay() {
        val wednesday = LocalDate.of(2026, 9, 30)

        assertEquals(
            LocalDate.of(2026, 9, 26),
            weekStart(wednesday, DayOfWeek.SATURDAY),
        )
        assertEquals(
            LocalDate.of(2026, 9, 28),
            weekStart(wednesday, DayOfWeek.MONDAY),
        )
        assertEquals(
            LocalDate.of(2026, 9, 26),
            weekStart(LocalDate.of(2026, 9, 26), DayOfWeek.SATURDAY),
        )
    }

    @Test
    fun weekDaysListsAllSevenDatesFromTheWeekStart() {
        val saturday = LocalDate.of(2026, 9, 26)

        assertEquals(
            (0L..6L).map(saturday::plusDays),
            weekDays(saturday),
        )
    }

    @Test
    fun averageDailyUsageRoundsTheMeanUp() {
        assertEquals(61, averageDailyUsage(listOf(61, 61, 61, 61, 61, 60, 60)))
    }

    @Test
    fun averageDailyUsageRequiresACompleteWeek() {
        assertThrows(IllegalArgumentException::class.java) {
            averageDailyUsage(List(6) { 60 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            averageDailyUsage(List(8) { 60 })
        }
    }

    @Test
    fun achievedIncludesUsageEqualToTheGoal() {
        assertTrue(achieved(average = 60, goal = 60))
        assertFalse(achieved(average = 61, goal = 60))
    }

    @Test
    fun recommendedGoalReducesAchievedAverageAndKeepsMissedGoal() {
        assertEquals(
            54,
            recommendedGoal(average = 61, goal = 70, reductionPercent = 10, achieved = true),
        )
        assertEquals(
            60,
            recommendedGoal(average = 61, goal = 60, reductionPercent = 10, achieved = false),
        )
    }

    @Test
    fun weeklySummaryCombinesTheResultAndRecommendation() {
        assertEquals(
            WeeklySummary(
                average = 61,
                achieved = true,
                recommendation = 54,
            ),
            weeklySummary(
                minutesPerDay = listOf(61, 61, 61, 61, 61, 60, 60),
                goal = 70,
                reductionPercent = 10,
            ),
        )
    }

    @Test
    fun remainingDailyBudgetSpreadsTheWeeklyRemainderAcrossDaysLeft() {
        assertEquals(42, remainingDailyBudget(goalMinutes = 45, usedSoFar = 101, dayIndex = 2))
    }

    @Test
    fun remainingDailyBudgetNeverReturnsLessThanZero() {
        assertEquals(0, remainingDailyBudget(goalMinutes = 45, usedSoFar = 400, dayIndex = 2))
    }

    @Test
    fun displayBudgetDoesNotExceedTheGoal() {
        assertEquals(45, displayBudget(goalMinutes = 45, remainingBudget = 70))
    }

    @Test
    fun formatHoursMinutesUsesZeroPaddedHoursAndMinutes() {
        assertEquals("01:32", formatHoursMinutes(92))
        assertEquals("00:00", formatHoursMinutes(0))
    }
}
