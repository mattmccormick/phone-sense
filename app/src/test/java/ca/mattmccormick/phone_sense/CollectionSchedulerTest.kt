package ca.mattmccormick.phone_sense

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Clock as WorkClock
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.Worker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private val ZONE = ZoneId.of("America/Los_Angeles")
private val NOTIFICATION_TIME = LocalTime.of(7, 0)

private fun at(text: String) = LocalDateTime.parse(text).atZone(ZONE).toInstant()

private fun clockAt(text: String) = Clock.fixed(at(text), ZONE)

private class SettableWorkClock(var instant: Instant) : WorkClock {
    override fun currentTimeMillis() = instant.toEpochMilli()
}

private class PinningWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val clock: Clock,
) : Worker(appContext, workerParameters) {
    override fun doWork(): Result {
        pinNextCollection(
            WorkManager.getInstance(applicationContext),
            id,
            clock,
            NOTIFICATION_TIME,
        )
        return Result.success()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollectionSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun initialiseWorkManager(
        clock: SettableWorkClock = SettableWorkClock(Instant.now()),
        workerFactory: WorkerFactory? = null,
    ): WorkManager {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setClock(clock)
            .apply { workerFactory?.let(::setWorkerFactory) }
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        return WorkManager.getInstance(context)
    }

    private fun nextRun(workManager: WorkManager): LocalDateTime {
        val work = workManager.getWorkInfosForUniqueWork(DAILY_COLLECTION_WORK).get().single()
        return LocalDateTime.ofInstant(
            Instant.ofEpochMilli(work.nextScheduleTimeMillis),
            ZONE,
        )
    }

    @Test
    fun schedulingTwiceKeepsOnePeriodicRequest() {
        val workManager = initialiseWorkManager()
        val clock = clockAt("2026-03-09T12:30")

        scheduleDailyCollection(workManager, clock, NOTIFICATION_TIME)
        scheduleDailyCollection(workManager, clock, NOTIFICATION_TIME)

        val work = workManager.getWorkInfosForUniqueWork(DAILY_COLLECTION_WORK).get()
        assertEquals(1, work.size)
    }

    @Test
    fun changingTheNotificationTimeMovesTheNextRun() {
        val workManager = initialiseWorkManager(SettableWorkClock(at("2026-03-09T12:30")))
        val clock = clockAt("2026-03-09T12:30")
        scheduleDailyCollection(workManager, clock, NOTIFICATION_TIME)

        scheduleDailyCollection(workManager, clock, LocalTime.of(18, 45))

        assertEquals(LocalDateTime.parse("2026-03-09T17:45"), nextRun(workManager))
    }

    @Test
    fun theRequestKeepsTheDailyIntervalForReboots() {
        val request = dailyCollectionRequest(clockAt("2026-03-09T12:30"), NOTIFICATION_TIME)

        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
    }

    @Test
    fun workEnqueuedAfterTheWindowRunsAtTheNextWindowStart() {
        val workManager = initialiseWorkManager(SettableWorkClock(at("2026-03-09T12:30")))

        scheduleDailyCollection(workManager, clockAt("2026-03-09T12:30"), NOTIFICATION_TIME)

        assertEquals(LocalDateTime.parse("2026-03-10T06:00"), nextRun(workManager))
    }

    @Test
    fun workEnqueuedBeforeTheWindowRunsAtTheWindowStartThatDay() {
        val workManager = initialiseWorkManager(SettableWorkClock(at("2026-03-09T05:00")))

        scheduleDailyCollection(workManager, clockAt("2026-03-09T05:00"), NOTIFICATION_TIME)

        assertEquals(LocalDateTime.parse("2026-03-09T06:00"), nextRun(workManager))
    }

    @Test
    fun aRunThatFinishesLateStillSchedulesTheNextWindowStart() {
        assertEquals(
            LocalDateTime.parse("2026-03-11T06:00"),
            nextRunAfterARunFinishingAt("2026-03-10T11:30"),
        )
    }

    @Test
    fun aRunThatFinishesBeforeTheWindowSchedulesTheWindowThatDay() {
        assertEquals(
            LocalDateTime.parse("2026-03-10T06:00"),
            nextRunAfterARunFinishingAt("2026-03-10T05:00"),
        )
    }

    private fun nextRunAfterARunFinishingAt(finished: String): LocalDateTime {
        val enqueued = "2026-03-09T12:30"
        val workClock = SettableWorkClock(at(enqueued))
        val workManager = initialiseWorkManager(workClock, object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker =
                PinningWorker(appContext, workerParameters, clockAt(finished))
        })

        scheduleDailyCollection(workManager, clockAt(enqueued), NOTIFICATION_TIME)
        val id = workManager.getWorkInfosForUniqueWork(DAILY_COLLECTION_WORK).get().single().id
        workClock.instant = at(finished)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setPeriodDelayMet(id)

        return nextRun(workManager)
    }
}
