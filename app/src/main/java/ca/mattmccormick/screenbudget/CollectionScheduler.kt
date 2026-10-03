package ca.mattmccormick.screenbudget

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

internal const val DAILY_COLLECTION_WORK = "daily-collection"
private val COLLECTION_TIME = LocalTime.of(7, 0)
private val COLLECTION_FLEX = Duration.ofHours(1)

internal fun scheduleDailyCollection(
    workManager: WorkManager,
    clock: Clock,
) {
    workManager.enqueueUniquePeriodicWork(
        DAILY_COLLECTION_WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        dailyCollectionRequest(clock),
    )
}

internal fun dailyCollectionRequest(clock: Clock) =
    PeriodicWorkRequestBuilder<CollectWorker>(
        24,
        TimeUnit.HOURS,
        1,
        TimeUnit.HOURS,
    )
        .setInitialDelay(initialCollectionDelay(clock))
        .build()

private fun initialCollectionDelay(clock: Clock): Duration {
    val now = ZonedDateTime.now(clock)
    var windowEnd = now.toLocalDate().atTime(COLLECTION_TIME).atZone(clock.zone)
    if (!windowEnd.isAfter(now)) windowEnd = windowEnd.plusDays(1)
    val windowStart = windowEnd.minus(COLLECTION_FLEX)
    return if (now.isBefore(windowStart)) Duration.between(now, windowStart) else Duration.ZERO
}
