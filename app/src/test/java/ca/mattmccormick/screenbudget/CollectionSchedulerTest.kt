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
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
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

        scheduleDailyCollection(workManager, clock)
        scheduleDailyCollection(workManager, clock)

        val work = workManager.getWorkInfosForUniqueWork(DAILY_COLLECTION_WORK).get()
        assertEquals(1, work.size)
    }

    @Test
    fun initialDelayStartsTheFirstFlexWindowEndingAtTheNextSevenAm() {
        val zone = ZoneId.of("America/Los_Angeles")
        val now = Instant.parse("2026-03-09T15:30:00Z")
        val clock = Clock.fixed(now, zone)

        val request = dailyCollectionRequest(clock)

        assertEquals(TimeUnit.HOURS.toMillis(24), request.workSpec.intervalDuration)
        assertEquals(TimeUnit.HOURS.toMillis(1), request.workSpec.flexDuration)
        val firstRun = now.plusMillis(request.workSpec.initialDelay).atZone(zone)
        assertEquals(6, firstRun.hour)
        assertEquals(0, firstRun.minute)
        assertEquals(
            firstRun.plus(Duration.ofHours(1)).toLocalDateTime(),
            firstRun.toLocalDate().atTime(7, 0),
        )
    }
}
