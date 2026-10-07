package ca.mattmccormick.phone_sense

import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Source
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        )

        assertEquals(6, model.weekBoundaryPositions.size)
        assertEquals(
            List(6) { DayOfWeek.THURSDAY },
            model.weekBoundaryPositions.map { model.dates[it].dayOfWeek },
        )
    }

    private fun day(date: LocalDate, minutes: Int) = DailyUsage(
        date = date,
        totalMinutes = minutes,
        source = Source.COLLECTED,
        collectedAt = Instant.EPOCH,
    )
}
