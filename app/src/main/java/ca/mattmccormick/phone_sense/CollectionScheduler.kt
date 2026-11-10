package ca.mattmccormick.phone_sense

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

internal const val DAILY_COLLECTION_WORK = "daily-collection"
private val COLLECTION_WINDOW = Duration.ofHours(1)

internal fun scheduleDailyCollection(
    workManager: WorkManager,
    clock: Clock,
    notificationTime: LocalTime,
) {
    workManager.enqueueUniquePeriodicWork(
        DAILY_COLLECTION_WORK,
        ExistingPeriodicWorkPolicy.UPDATE,
        dailyCollectionRequest(clock, notificationTime),
    )
}

internal fun dailyCollectionRequest(
    clock: Clock,
    notificationTime: LocalTime,
) = collectionRequest(clock, notificationTime).build()

internal fun pinNextCollection(
    workManager: WorkManager,
    id: UUID,
    clock: Clock,
    notificationTime: LocalTime,
) {
    // The update is not waited on: WorkManager honours the override whether it
    // lands while the worker still runs or after it has finished.
    workManager.updateWork(collectionRequest(clock, notificationTime).setId(id).build())
}

private fun collectionRequest(clock: Clock, notificationTime: LocalTime) =
    PeriodicWorkRequestBuilder<CollectWorker>(24, TimeUnit.HOURS)
        .setNextScheduleTimeOverride(
            nextWindowStart(clock, notificationTime).toInstant().toEpochMilli(),
        )

private fun nextWindowStart(clock: Clock, notificationTime: LocalTime): ZonedDateTime {
    val now = ZonedDateTime.now(clock)
    var start = now.toLocalDate().atTime(notificationTime).atZone(clock.zone)
        .minus(COLLECTION_WINDOW)
    while (!start.isAfter(now)) start = start.plusDays(1)
    return start
}
