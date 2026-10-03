package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.screenbudget.data.DailyUsage as StoredDailyUsage
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollectorTest {
    private val today = LocalDate.of(2026, 3, 9)
    private val zone = ZoneId.of("America/Los_Angeles")
    private val collectedAt = Instant.parse("2026-03-09T12:00:00Z")
    private lateinit var database: UsageDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun collectsAndStoresAllSevenMissingDays() {
        val source = FakeUsageEventsSource { _, _ -> sampleEvents }
        val collector = collector(source) { _, _, _ ->
            DailyUsage(125_000, mapOf("reader" to 60_000, "brief" to 59_999))
        }

        val result = collector.collect(today, zone)

        val expectedDates = (7L downTo 1L).map(today::minusDays)
        assertEquals(CollectResult.Collected(expectedDates), result)
        assertEquals(7, source.queries.size)
        expectedDates.forEach { date ->
            val stored = database.usageDao().day(date)!!
            assertEquals(Source.COLLECTED, stored.day.source)
            assertEquals(2, stored.day.totalMinutes)
            assertEquals(collectedAt, stored.day.collectedAt)
            assertEquals(listOf("reader" to 1), stored.apps.map { it.appKey to it.minutes })
        }
    }

    @Test
    fun doesNotQueryADayThatAlreadyHasACollectedRow() {
        val existingDate = today.minusDays(4)
        insert(existingDate, Source.COLLECTED)
        val source = FakeUsageEventsSource { _, _ -> sampleEvents }

        collector(source).collect(today, zone)

        assertEquals(6, source.queries.size)
        assertEquals(17, database.usageDao().day(existingDate)!!.day.totalMinutes)
    }

    @Test
    fun returnsNothingToDoWhenEveryDayIsAlreadyStored() {
        (7L downTo 1L).forEach { insert(today.minusDays(it), Source.COLLECTED) }

        val result = collector(FakeUsageEventsSource { _, _ -> sampleEvents }).collect(today, zone)

        assertEquals(CollectResult.NothingToDo, result)
    }

    @Test
    fun leavesAManualDayAlone() {
        val manualDate = today.minusDays(3)
        insert(manualDate, Source.MANUAL)
        val source = FakeUsageEventsSource { _, _ -> sampleEvents }

        collector(source).collect(today, zone)

        assertEquals(6, source.queries.size)
        assertEquals(Source.MANUAL, database.usageDao().day(manualDate)!!.day.source)
        assertEquals(17, database.usageDao().day(manualDate)!!.day.totalMinutes)
    }

    @Test
    fun replacesAnImportedDayWithCollectedData() {
        val importedDate = today.minusDays(3)
        insert(importedDate, Source.IMPORTED)
        val source = FakeUsageEventsSource { _, _ -> sampleEvents }

        collector(source).collect(today, zone)

        assertEquals(7, source.queries.size)
        assertEquals(Source.COLLECTED, database.usageDao().day(importedDate)!!.day.source)
    }

    @Test
    fun neverCollectsToday() {
        val source = FakeUsageEventsSource { _, _ -> sampleEvents }

        collector(source).collect(today, zone)

        assertNull(database.usageDao().day(today))
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(false, source.queries.any { (_, end) -> end > todayStart })
    }

    @Test
    fun returnsNotUnlockedAndDoesNotStoreTheDayWhenEventsAreNull() {
        val source = FakeUsageEventsSource { _, _ -> null }

        val result = collector(source).collect(today, zone)

        assertEquals(CollectResult.NotUnlocked, result)
        assertEquals(1, source.queries.size)
        assertEquals(emptyList<LocalDate>(), database.usageDao().datesBetween(today.minusDays(7), today))
    }

    @Test
    fun queriesWithLookbackAndAggregatesOnlyTheLocalDay() {
        val date = today.minusDays(7)
        val source = FakeUsageEventsSource { _, _ -> sampleEvents }
        var aggregateCall: Triple<List<UsageEvent>, Long, Long>? = null
        insert(today.minusDays(6), Source.COLLECTED)
        insert(today.minusDays(5), Source.COLLECTED)
        insert(today.minusDays(4), Source.COLLECTED)
        insert(today.minusDays(3), Source.COLLECTED)
        insert(today.minusDays(2), Source.COLLECTED)
        insert(today.minusDays(1), Source.COLLECTED)

        collector(source) { events, start, end ->
            aggregateCall = Triple(events, start, end)
            DailyUsage(0, emptyMap())
        }.collect(today, zone)

        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        assertEquals(listOf((start.minusSeconds(86_400).toEpochMilli() to end.toEpochMilli())), source.queries)
        assertEquals(Triple(sampleEvents, start.toEpochMilli(), end.toEpochMilli()), aggregateCall)
    }

    private fun collector(
        source: UsageEventSource,
        aggregate: (List<UsageEvent>, Long, Long) -> DailyUsage = { _, _, _ ->
            DailyUsage(0, emptyMap())
        },
    ) = Collector(
        dao = database.usageDao(),
        source = source,
        aggregate = aggregate,
        clock = Clock.fixed(collectedAt, ZoneId.of("UTC")),
    )

    private fun insert(date: LocalDate, source: Source) {
        database.usageDao().insert(
            StoredDailyUsage(date, 17, source, collectedAt.minusSeconds(60)),
            emptyList(),
        )
    }

    private class FakeUsageEventsSource(
        private val result: (Long, Long) -> List<UsageEvent>?,
    ) : UsageEventSource {
        val queries = mutableListOf<Pair<Long, Long>>()

        override fun events(beginMs: Long, endMs: Long): List<UsageEvent>? {
            queries += beginMs to endMs
            return result(beginMs, endMs)
        }
    }

    private companion object {
        val sampleEvents = listOf(UsageEvent(1, "reader", "Main", 1))
    }
}
