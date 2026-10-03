package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollectionSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val configuration = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun schedulingTwiceKeepsOnePeriodicRequest() {
        val clock = Clock.fixed(
            Instant.parse("2026-03-09T15:00:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )

        scheduleDailyCollection(workManager, clock, LocalTime.of(7, 0))
        scheduleDailyCollection(workManager, clock, LocalTime.of(7, 0))

        val work = workManager.getWorkInfosForUniqueWork(DAILY_COLLECTION_WORK).get()
        assertEquals(1, work.size)
    }

    @Test
    fun changingNotificationTimeReplacesThePeriodicRequest() {
        val clock = Clock.fixed(
            Instant.parse("2026-03-09T15:00:00Z"),
            ZoneId.of("America/Los_Angeles"),
        )
        scheduleDailyCollection(workManager, clock, LocalTime.of(7, 0))
        val firstId = workManager.getWorkInfosForUniqueWork(DAILY_COLLECTION_WORK).get().single().id

        scheduleDailyCollection(workManager, clock, LocalTime.of(18, 45))

        val replacement = workManager.getWorkInfosForUniqueWork(DAILY_COLLECTION_WORK).get()
        assertEquals(1, replacement.size)
        assertNotEquals(firstId, replacement.single().id)
    }

    @Test
    fun flexWindowEndsAtTheChosenNotificationTime() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = Instant.parse("2026-03-09T15:30:00Z")
        val clock = Clock.fixed(now, zone)
        val notificationTime = LocalTime.of(18, 45)

        val request = dailyCollectionRequest(clock, notificationTime)

        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
        assertEquals(TimeUnit.HOURS.toMillis(1), request.workSpec.flexDuration)
        val firstRun = now.plusMillis(request.workSpec.initialDelay).atZone(zone)
        assertEquals(17, firstRun.hour)
        assertEquals(45, firstRun.minute)
        assertEquals(
            firstRun.plus(Duration.ofHours(1)).toLocalDateTime(),
            firstRun.toLocalDate().atTime(notificationTime),
        )
    }
}
