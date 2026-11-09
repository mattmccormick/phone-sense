package ca.mattmccormick.phone_sense

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.phone_sense.budget.weekStart
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeChartLoaderTest {
    private val weekStartDay = DayOfWeek.MONDAY
    private val today = LocalDate.of(2026, 10, 7)
    private val pastEnd = chartWindowEnd(today, weekStartDay, 1)
    private lateinit var database: UsageDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun insertDay(date: LocalDate, minutes: Int) = database.usageDao().insert(
        DailyUsage(date, minutes, Source.COLLECTED, Instant.parse("2026-10-08T07:00:00Z")),
        emptyList(),
    )

    private fun snapshot(date: LocalDate, minutes: Int) = CurrentDayUsageSnapshot(
        date = date,
        capturedAt = Instant.parse("2026-10-07T18:00:00Z"),
        totalMillis = minutes * 60_000L,
        perPackageMillis = emptyMap(),
    )

    private fun load(end: LocalDate, currentSnapshot: CurrentDayUsageSnapshot? = null) =
        loadHomeChart(
            database.usageDao(),
            database.goalDao(),
            database.appRuleDao(),
            weekStartDay,
            end,
            currentSnapshot,
        )

    @Test
    fun aPastWindowEndsOnItsLastWholeWeekAndLeavesTodayOut() {
        insertDay(pastEnd, 30)
        insertDay(today, 90)

        val page = load(pastEnd, snapshot(today, 120))

        assertEquals(pastEnd, page.chart.dates.last())
        assertEquals(LocalDate.of(2026, 8, 24), page.chart.dates.first())
        assertEquals(30, page.chart.dailyMinutes.last())
        assertFalse(page.chart.dates.contains(today))
    }

    @Test
    fun theLatestWindowStillTakesTodayFromTheSnapshot() {
        insertDay(today, 90)

        val page = load(today, snapshot(today, 120))

        assertEquals(today, page.chart.dates.last())
        assertEquals(120, page.chart.dailyMinutes.last())
    }

    @Test
    fun aPastWindowShowsOnlyTheGoalsOfItsOwnWeeks() {
        val lastWindowWeek = weekStart(pastEnd, weekStartDay)
        database.goalDao().insert(Goal(lastWindowWeek, 45))
        database.goalDao().insert(Goal(weekStart(today, weekStartDay), 20))

        val goals = load(pastEnd).chart.goalMinutes

        assertEquals(List(7) { 45 }, (35..41).map(goals::get))
        assertFalse(goals.contains(20))
    }

    @Test
    fun earlierDataIsAvailableWhenADayPrecedesTheWindow() {
        insertDay(pastEnd.minusDays(42), 10)

        assertTrue(load(pastEnd).earlierDataAvailable)
    }

    @Test
    fun earlierDataIsUnavailableWhenTheOldestDayIsInsideTheWindow() {
        insertDay(pastEnd.minusDays(41), 10)
        insertDay(pastEnd, 10)

        assertFalse(load(pastEnd).earlierDataAvailable)
    }

    @Test
    fun earlierDataIsUnavailableWithoutAnyDay() {
        assertFalse(load(today).earlierDataAvailable)
    }
}
