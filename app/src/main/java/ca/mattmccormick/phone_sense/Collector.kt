package ca.mattmccormick.phone_sense

import ca.mattmccormick.phone_sense.data.AppUsage
import ca.mattmccormick.phone_sense.data.DailyUsage as StoredDailyUsage
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDao
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

internal val USAGE_QUERY_LOOKBACK: Duration = Duration.ofHours(24)

sealed interface CollectResult {
    data class Collected(val dates: List<LocalDate>) : CollectResult
    data object NothingToDo : CollectResult
    data object NotUnlocked : CollectResult
}

class Collector(
    private val dao: UsageDao,
    private val source: UsageEventSource,
    private val aggregate: (List<UsageEvent>, Long, Long) -> DailyUsage = UsageAggregator::aggregate,
    private val clock: Clock = Clock.systemUTC(),
) : CollectionRunner {
    override fun collect(today: LocalDate, zone: ZoneId): CollectResult {
        val firstDate = today.minusDays(DAYS_TO_COLLECT)
        val protectedDates = dao.daysBetween(firstDate, today.minusDays(1))
            .filter { it.day.source != Source.IMPORTED }
            .mapTo(mutableSetOf()) { it.day.date }
        val collectedDates = mutableListOf<LocalDate>()

        for (offset in DAYS_TO_COLLECT downTo 1) {
            val date = today.minusDays(offset)
            if (date in protectedDates) continue

            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val events = source.events(
                start.minus(USAGE_QUERY_LOOKBACK).toEpochMilli(),
                end.toEpochMilli(),
            ) ?: return CollectResult.NotUnlocked
            val usage = aggregate(events, start.toEpochMilli(), end.toEpochMilli())

            dao.insert(
                StoredDailyUsage(
                    date = date,
                    totalMinutes = usage.totalMillis.toMinutes(),
                    source = Source.COLLECTED,
                    collectedAt = clock.instant(),
                    includesAllApps = true,
                ),
                usage.perPackageMillis.mapNotNull { (packageName, millis) ->
                    val minutes = millis.toMinutes()
                    if (minutes < 1) null else AppUsage(date, packageName, minutes)
                },
            )
            collectedDates += date
        }

        return if (collectedDates.isEmpty()) {
            CollectResult.NothingToDo
        } else {
            CollectResult.Collected(collectedDates)
        }
    }

    private fun Long.toMinutes(): Int = (this / MILLIS_PER_MINUTE).toInt()

    private companion object {
        const val DAYS_TO_COLLECT = 7L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
