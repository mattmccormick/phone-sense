package ca.mattmccormick.screenbudget

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAggregatorTest {
    @Test
    fun resumedUntilPausedCountsForPackage() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(type = 1, packageName = "example.app", className = "Main", timestampMs = 1_000),
                UsageEvent(type = 2, packageName = "example.app", className = "Main", timestampMs = 4_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(3_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 3_000L), usage.perPackageMillis)
    }

    @Test
    fun overlappingActivitiesOfOnePackageCountOnce() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(1, "example.app", "First", 1_000),
                UsageEvent(1, "example.app", "Second", 2_000),
                UsageEvent(2, "example.app", "First", 4_000),
                UsageEvent(2, "example.app", "Second", 6_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(5_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 5_000L), usage.perPackageMillis)
    }

    @Test
    fun intervalsAreClippedToWindow() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(1, "starts.early", "Main", -1_000),
                UsageEvent(2, "starts.early", "Main", 3_000),
                UsageEvent(1, "ends.late", "Main", 7_000),
                UsageEvent(2, "ends.late", "Main", 12_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(6_000, usage.totalMillis)
        assertEquals(
            mapOf("starts.early" to 3_000L, "ends.late" to 3_000L),
            usage.perPackageMillis,
        )
    }

    @Test
    fun openIntervalClosesAtWindowEnd() {
        val usage = UsageAggregator.aggregate(
            events = listOf(UsageEvent(1, "example.app", "Main", 4_000)),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(6_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 6_000L), usage.perPackageMillis)
    }

    @Test
    fun totalIsUnionOfOverlappingPackages() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(1, "first.app", "Main", 1_000),
                UsageEvent(1, "second.app", "Main", 3_000),
                UsageEvent(2, "first.app", "Main", 5_000),
                UsageEvent(2, "second.app", "Main", 7_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(6_000, usage.totalMillis)
        assertEquals(
            mapOf("first.app" to 4_000L, "second.app" to 4_000L),
            usage.perPackageMillis,
        )
    }

    @Test
    fun stoppedEventClosesInterval() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(1, "example.app", "Main", 2_000),
                UsageEvent(23, "example.app", "Main", 8_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(6_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 6_000L), usage.perPackageMillis)
    }

    @Test
    fun repeatedResumeReplacesStartTimestamp() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(1, "example.app", "Main", 1_000),
                UsageEvent(1, "example.app", "Main", 3_000),
                UsageEvent(2, "example.app", "Main", 7_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(4_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 4_000L), usage.perPackageMillis)
    }

    @Test
    fun pauseForClosedActivityIsIgnored() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(1, "example.app", "Main", 1_000),
                UsageEvent(2, "example.app", "Main", 4_000),
                UsageEvent(2, "example.app", "Main", 7_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(3_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 3_000L), usage.perPackageMillis)
    }

    @Test
    fun firstPauseForPackageCountsFromWindowStart() {
        val usage = UsageAggregator.aggregate(
            events = listOf(UsageEvent(2, "example.app", "Main", 4_000)),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(4_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 4_000L), usage.perPackageMillis)
    }

    @Test
    fun unseenPauseAfterPackageWasClosedIsIgnored() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(1, "example.app", "Main", 1_000),
                UsageEvent(2, "example.app", "Main", 4_000),
                UsageEvent(2, "example.app", "Other", 7_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(3_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 3_000L), usage.perPackageMillis)
    }

    @Test
    fun eventsAreProcessedInTimestampOrder() {
        val usage = UsageAggregator.aggregate(
            events = listOf(
                UsageEvent(2, "example.app", "Main", 4_000),
                UsageEvent(1, "example.app", "Main", 1_000),
            ),
            startMs = 0,
            endMs = 10_000,
        )

        assertEquals(3_000, usage.totalMillis)
        assertEquals(mapOf("example.app" to 3_000L), usage.perPackageMillis)
    }
}
