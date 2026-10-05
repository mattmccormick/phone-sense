package ca.mattmccormick.screenbudget

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CurrentDayUsageSnapshotReaderTest {
    private val zone = ZoneId.of("America/Los_Angeles")
    private val now = Instant.parse("2026-10-03T19:00:00Z")

    @Test
    fun queriesWithLookbackAndAggregatesOnlyMidnightThroughCapturedInstant() {
        val source = FakeUsageEventSource(listOf(event(1, "reader", now.toEpochMilli())))
        var aggregateCall: Triple<List<UsageEvent>, Long, Long>? = null
        val reader = reader(source) { events, start, end ->
            aggregateCall = Triple(events, start, end)
            DailyUsage(25, mapOf("reader" to 25))
        }

        val result = reader.read(zone)

        val start = Instant.parse("2026-10-03T07:00:00Z")
        assertEquals(
            listOf(start.minusSeconds(86_400).toEpochMilli() to now.toEpochMilli()),
            source.queries,
        )
        assertEquals(Triple(source.result, start.toEpochMilli(), now.toEpochMilli()), aggregateCall)
        assertEquals(
            CurrentDayUsageSnapshotResult.Available(
                CurrentDayUsageSnapshot(
                    date = now.atZone(zone).toLocalDate(),
                    capturedAt = now,
                    totalMillis = 25,
                    perPackageMillis = mapOf("reader" to 25),
                ),
            ),
            result,
        )
    }

    @Test
    fun clipsSessionsSpanningMidnightAndCapturedInstant() {
        val midnight = Instant.parse("2026-10-03T07:00:00Z").toEpochMilli()
        val events = listOf(
            event(1, "reader", midnight - 3_600_000),
            event(2, "reader", now.toEpochMilli() + 3_600_000),
        )

        val result = available(reader(FakeUsageEventSource(events)).read(zone))

        assertEquals(12 * 3_600_000L, result.totalMillis)
        assertEquals(mapOf("reader" to 12 * 3_600_000L), result.perPackageMillis)
    }

    @Test
    fun countsAnOngoingSessionThroughCapturedInstant() {
        val resumedAt = now.minusSeconds(90 * 60L)
        val events = listOf(event(1, "reader", resumedAt.toEpochMilli()))

        val result = available(reader(FakeUsageEventSource(events)).read(zone))

        assertEquals(90 * 60_000L, result.totalMillis)
        assertEquals(mapOf("reader" to 90 * 60_000L), result.perPackageMillis)
    }

    @Test
    fun repeatedReadsQueryFreshEventsWithoutPersistingPriorResults() {
        var invocation = 0L
        val source = FakeUsageEventSource(emptyList())
        val reader = reader(source) { _, _, _ ->
            invocation += 1
            DailyUsage(invocation, mapOf("reader" to invocation))
        }

        val first = available(reader.read(zone))
        val second = available(reader.read(zone))

        assertEquals(1, first.totalMillis)
        assertEquals(2, second.totalMillis)
        assertEquals(2, source.queries.size)
    }

    @Test
    fun derivesDateAndMidnightFromSuppliedTimeZone() {
        val capturedAt = Instant.parse("2026-10-03T00:30:00Z")
        val source = FakeUsageEventSource(emptyList())
        val clock = Clock.fixed(capturedAt, ZoneOffset.UTC)
        val reader = CurrentDayUsageSnapshotReader(source, clock = clock)

        val losAngeles = available(reader.read(ZoneId.of("America/Los_Angeles")))
        val tokyo = available(reader.read(ZoneId.of("Asia/Tokyo")))

        assertNotEquals(losAngeles.date, tokyo.date)
        assertEquals(Instant.parse("2026-10-02T07:00:00Z").minusSeconds(86_400).toEpochMilli(), source.queries[0].first)
        assertEquals(Instant.parse("2026-10-02T15:00:00Z").minusSeconds(86_400).toEpochMilli(), source.queries[1].first)
    }

    @Test
    fun capturesCurrentInstantOnlyOncePerRead() {
        val first = Instant.parse("2026-10-03T19:00:00Z")
        val second = first.plusSeconds(1)
        val clock = AdvancingClock(mutableListOf(first, second))
        val source = FakeUsageEventSource(emptyList())

        val result = available(CurrentDayUsageSnapshotReader(source, clock = clock).read(zone))

        assertEquals(first, result.capturedAt)
        assertEquals(first.toEpochMilli(), source.queries.single().second)
        assertEquals(1, clock.calls)
    }

    @Test
    fun distinguishesUnavailableEventsFromValidZeroUsage() {
        val unavailable = reader(FakeUsageEventSource(null)).read(zone)
        val zero = reader(FakeUsageEventSource(emptyList())).read(zone)

        assertEquals(CurrentDayUsageSnapshotResult.Unavailable, unavailable)
        assertEquals(0, available(zero).totalMillis)
        assertEquals(emptyMap<String, Long>(), available(zero).perPackageMillis)
    }

    private fun reader(
        source: UsageEventSource,
        aggregate: (List<UsageEvent>, Long, Long) -> DailyUsage = UsageAggregator::aggregate,
    ) = CurrentDayUsageSnapshotReader(source, aggregate, Clock.fixed(now, ZoneOffset.UTC))

    private fun available(result: CurrentDayUsageSnapshotResult): CurrentDayUsageSnapshot =
        (result as CurrentDayUsageSnapshotResult.Available).snapshot

    private fun event(type: Int, packageName: String, timestampMs: Long) =
        UsageEvent(type, packageName, "Main", timestampMs)

    private class FakeUsageEventSource(val result: List<UsageEvent>?) : UsageEventSource {
        val queries = mutableListOf<Pair<Long, Long>>()

        override fun events(beginMs: Long, endMs: Long): List<UsageEvent>? {
            queries += beginMs to endMs
            return result
        }
    }

    private class AdvancingClock(private val instants: MutableList<Instant>) : Clock() {
        var calls = 0

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant {
            calls += 1
            return instants.removeAt(0)
        }
    }
}
