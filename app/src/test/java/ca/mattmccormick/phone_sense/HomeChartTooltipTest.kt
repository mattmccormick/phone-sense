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

class HomeChartTooltipTest {
    private val today = LocalDate.of(2026, 11, 7)

    private fun day(date: LocalDate, minutes: Int) = DailyUsage(
        date = date,
        totalMinutes = minutes,
        source = Source.COLLECTED,
        collectedAt = Instant.EPOCH,
    )

    private fun model() = chartModel(
        days = listOf(
            day(LocalDate.of(2026, 10, 28), 40),
            day(LocalDate.of(2026, 11, 4), 72),
            day(LocalDate.of(2026, 11, 5), 95),
            day(today, 30),
        ),
        goals = listOf(Goal(LocalDate.of(2026, 11, 2), 90)),
        weekStartDay = DayOfWeek.MONDAY,
        end = today,
    )

    private fun tooltipOn(date: LocalDate): List<String> {
        val model = model()
        return tooltipLines(model, model.dates.indexOf(date))
    }

    @Test
    fun mapsTapsInsideThePlotToTheirDay() {
        assertEquals(0, dayIndexAt(x = 28f, left = 28f, cell = 10f, dayCount = 42))
        assertEquals(0, dayIndexAt(x = 37.9f, left = 28f, cell = 10f, dayCount = 42))
        assertEquals(1, dayIndexAt(x = 38f, left = 28f, cell = 10f, dayCount = 42))
        assertEquals(41, dayIndexAt(x = 447.9f, left = 28f, cell = 10f, dayCount = 42))
    }

    @Test
    fun mapsTapsOutsideThePlotToNoDay() {
        assertNull(dayIndexAt(x = 27.9f, left = 28f, cell = 10f, dayCount = 42))
        assertNull(dayIndexAt(x = 0f, left = 28f, cell = 10f, dayCount = 42))
        assertNull(dayIndexAt(x = 448f, left = 28f, cell = 10f, dayCount = 42))
        assertNull(dayIndexAt(x = 100f, left = 28f, cell = 0f, dayCount = 42))
    }

    @Test
    fun readsOutADayUnderTheWeeksGoal() {
        assertEquals(
            listOf("Wed Nov 4", "72 min · 18 under goal", "Goal 90 min/day", "Week average 66 min"),
            tooltipOn(LocalDate.of(2026, 11, 4)),
        )
    }

    @Test
    fun readsOutADayOverTheWeeksGoal() {
        assertEquals(
            listOf("Thu Nov 5", "95 min · 5 over goal", "Goal 90 min/day", "Week average 66 min"),
            tooltipOn(LocalDate.of(2026, 11, 5)),
        )
    }

    @Test
    fun marksTodaysMinutesAsStillCounting() {
        assertEquals(
            listOf("Sat Nov 7 · so far", "30 min · 60 under goal", "Goal 90 min/day", "Week average 66 min"),
            tooltipOn(today),
        )
    }

    @Test
    fun saysSoForADayWithoutUsage() {
        assertEquals(
            listOf("Fri Nov 6", "No usage data", "Goal 90 min/day", "Week average 66 min"),
            tooltipOn(LocalDate.of(2026, 11, 6)),
        )
    }

    @Test
    fun leavesOutTheComparisonForAWeekWithoutAGoal() {
        assertEquals(
            listOf("Wed Oct 28", "40 min", "Goal not set", "Week average 40 min"),
            tooltipOn(LocalDate.of(2026, 10, 28)),
        )
    }

    @Test
    fun saysSoForAWeekWithoutAverageOrGoal() {
        assertEquals(
            listOf("Mon Oct 19", "No usage data", "Goal not set", "Week average unavailable"),
            tooltipOn(LocalDate.of(2026, 10, 19)),
        )
    }
}
