package ca.mattmccormick.phone_sense

import ca.mattmccormick.phone_sense.budget.weekStart
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Source
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChartModelTest {
    @Test
    fun containsOnePointPerDayForSixWeeksAndLeavesMissingDaysEmpty() {
        val end = LocalDate.of(2026, 10, 3)
        val recordedDate = end.minusDays(20)

        val model = chartModel(
            days = listOf(day(recordedDate, 37)),
            goals = emptyList(),
            weekStartDay = DayOfWeek.MONDAY,
            end = end,
            today = end,
        )

        assertEquals(42, model.dates.size)
        assertEquals(end.minusDays(41), model.dates.first())
        assertEquals(end, model.dates.last())
        assertEquals(42, model.dailyMinutes.size)
        assertEquals(37, model.dailyMinutes[21])
        assertNull(model.dailyMinutes[20])
        assertNull(model.dailyMinutes[22])
    }

    @Test
    fun repeatsEachGoalAcrossItsWeek() {
        val end = LocalDate.of(2026, 10, 11)
        val firstWeek = LocalDate.of(2026, 9, 28)
        val secondWeek = firstWeek.plusWeeks(1)

        val model = chartModel(
            days = emptyList(),
            goals = listOf(Goal(firstWeek, 45), Goal(secondWeek, 30)),
            weekStartDay = DayOfWeek.MONDAY,
            end = end,
            today = end,
        )

        val firstWeekIndices = model.dates.indices.filter {
            model.dates[it] in firstWeek..firstWeek.plusDays(6)
        }
        val secondWeekIndices = model.dates.indices.filter {
            model.dates[it] in secondWeek..secondWeek.plusDays(6)
        }
        assertEquals(List(7) { 45 }, firstWeekIndices.map(model.goalMinutes::get))
        assertEquals(List(7) { 30 }, secondWeekIndices.map(model.goalMinutes::get))
    }

    @Test
    fun repeatsEachWeeksMeanOfCollectedDaysAcrossTheWeek() {
        val end = LocalDate.of(2026, 10, 11)
        val week = LocalDate.of(2026, 9, 28)

        val model = chartModel(
            days = listOf(day(week, 20), day(week.plusDays(2), 40)),
            goals = emptyList(),
            weekStartDay = DayOfWeek.MONDAY,
            end = end,
            today = end,
        )

        val weekIndices = model.dates.indices.filter {
            model.dates[it] in week..week.plusDays(6)
        }
        assertEquals(List(7) { 30.0 }, weekIndices.map(model.weeklyAverageMinutes::get))
        assertNull(model.weeklyAverageMinutes[weekIndices.first() - 1])
    }

    @Test
    fun weekBoundariesFallOnTheConfiguredWeekStartDay() {
        val model = chartModel(
            days = emptyList(),
            goals = emptyList(),
            weekStartDay = DayOfWeek.THURSDAY,
            end = LocalDate.of(2026, 10, 3),
            today = LocalDate.of(2026, 10, 3),
        )

        assertEquals(6, model.weekBoundaryPositions.size)
        assertEquals(
            List(6) { DayOfWeek.THURSDAY },
            model.weekBoundaryPositions.map { model.dates[it].dayOfWeek },
        )
    }

    @Test
    fun aPastWindowCoversSixWholeWeeksOfDatesGoalsAndAverages() {
        val today = LocalDate.of(2026, 10, 7)
        val end = chartWindowEnd(today, DayOfWeek.MONDAY, 1)
        val lastWindowWeek = weekStart(end, DayOfWeek.MONDAY)

        val model = chartModel(
            days = listOf(day(lastWindowWeek, 20), day(lastWindowWeek.plusDays(6), 40)),
            goals = listOf(Goal(lastWindowWeek, 45)),
            weekStartDay = DayOfWeek.MONDAY,
            end = end,
            today = today,
        )

        assertEquals(LocalDate.of(2026, 8, 24), model.dates.first())
        assertEquals(LocalDate.of(2026, 10, 4), model.dates.last())
        assertEquals(listOf(0, 7, 14, 21, 28, 35), model.weekBoundaryPositions)
        assertEquals(List(7) { 45 }, (35..41).map(model.goalMinutes::get))
        assertEquals(List(7) { 30.0 }, (35..41).map(model.weeklyAverageMinutes::get))
        assertNull(model.goalMinutes[34])
        assertNull(model.weeklyAverageMinutes[34])
    }

    @Test
    fun marksAWindowThatEndsTodayAsIncludingToday() {
        val today = LocalDate.of(2026, 10, 3)

        val model = chartModel(
            days = emptyList(),
            goals = emptyList(),
            weekStartDay = DayOfWeek.MONDAY,
            end = today,
            today = today,
        )

        assertTrue(model.includesToday)
    }

    @Test
    fun marksAWindowThatEndsBeforeTodayAsNotIncludingToday() {
        val today = LocalDate.of(2026, 10, 3)

        val model = chartModel(
            days = emptyList(),
            goals = emptyList(),
            weekStartDay = DayOfWeek.MONDAY,
            end = today.minusWeeks(2),
            today = today,
        )

        assertFalse(model.includesToday)
    }

    @Test
    fun pageZeroEndsToday() {
        val today = LocalDate.of(2026, 10, 7)

        assertEquals(today, chartWindowEnd(today, DayOfWeek.MONDAY, 0))
        assertEquals(today, chartWindowEnd(today, DayOfWeek.WEDNESDAY, 0))
    }

    @Test
    fun theFirstPastPageEndsTheDayBeforeTheCurrentWeek() {
        val wednesday = LocalDate.of(2026, 10, 7)

        assertEquals(LocalDate.of(2026, 10, 4), chartWindowEnd(wednesday, DayOfWeek.MONDAY, 1))
        assertEquals(LocalDate.of(2026, 10, 2), chartWindowEnd(wednesday, DayOfWeek.SATURDAY, 1))
        assertEquals(LocalDate.of(2026, 10, 6), chartWindowEnd(wednesday, DayOfWeek.WEDNESDAY, 1))
    }

    @Test
    fun eachFurtherPageStepsBackOneWeek() {
        val wednesday = LocalDate.of(2026, 10, 7)

        assertEquals(LocalDate.of(2026, 9, 27), chartWindowEnd(wednesday, DayOfWeek.MONDAY, 2))
        assertEquals(LocalDate.of(2026, 9, 20), chartWindowEnd(wednesday, DayOfWeek.MONDAY, 3))
        assertEquals(LocalDate.of(2026, 8, 30), chartWindowEnd(wednesday, DayOfWeek.MONDAY, 6))
    }

    @Test
    fun everyPastPageHoldsWholeWeeksForEveryWeekStartDay() {
        val wednesday = LocalDate.of(2026, 10, 7)

        DayOfWeek.entries.forEach { weekStartDay ->
            (1..6).forEach { page ->
                val end = chartWindowEnd(wednesday, weekStartDay, page)
                assertEquals(weekStartDay, end.minusDays(41).dayOfWeek)
                assertEquals(weekStartDay, end.plusDays(1).dayOfWeek)
            }
        }
    }

    @Test
    fun rejectsANegativePage() {
        assertThrows(IllegalArgumentException::class.java) {
            chartWindowEnd(LocalDate.of(2026, 10, 7), DayOfWeek.MONDAY, -1)
        }
    }

    private fun day(date: LocalDate, minutes: Int) = DailyUsage(
        date = date,
        totalMinutes = minutes,
        source = Source.COLLECTED,
        collectedAt = Instant.EPOCH,
    )
}
