package ca.mattmccormick.phone_sense

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CurrentDayUsageSnapshot(
    val date: LocalDate,
    val capturedAt: Instant,
    val totalMillis: Long,
    val perPackageMillis: Map<String, Long>,
)

sealed interface CurrentDayUsageSnapshotResult {
    data class Available(val snapshot: CurrentDayUsageSnapshot) : CurrentDayUsageSnapshotResult
    data object Unavailable : CurrentDayUsageSnapshotResult
}

class CurrentDayUsageSnapshotReader(
    private val source: UsageEventSource,
    private val aggregate: (List<UsageEvent>, Long, Long) -> DailyUsage = UsageAggregator::aggregate,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun read(zone: ZoneId): CurrentDayUsageSnapshotResult {
        val capturedAt = clock.instant()
        val date = capturedAt.atZone(zone).toLocalDate()
        val start = date.atStartOfDay(zone).toInstant()
        val events = source.events(
            start.minus(USAGE_QUERY_LOOKBACK).toEpochMilli(),
            capturedAt.toEpochMilli(),
        ) ?: return CurrentDayUsageSnapshotResult.Unavailable
        val usage = aggregate(events, start.toEpochMilli(), capturedAt.toEpochMilli())

        return CurrentDayUsageSnapshotResult.Available(
            CurrentDayUsageSnapshot(
                date = date,
                capturedAt = capturedAt,
                totalMillis = usage.totalMillis,
                perPackageMillis = usage.perPackageMillis,
            ),
        )
    }
}
