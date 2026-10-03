package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CollectWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("America/Los_Angeles")
    private val instant = Instant.parse("2026-03-09T15:00:00Z")

    @Test
    fun collectsTodayInTheSystemZoneAndSucceedsWhenDataWasCollected() {
        var requestedDate: LocalDate? = null
        var requestedZone: ZoneId? = null
        val collector = CollectionRunner { today, zone ->
            requestedDate = today
            requestedZone = zone
            CollectResult.Collected(listOf(today.minusDays(1)))
        }

        val result = worker(collector).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(LocalDate.of(2026, 3, 9), requestedDate)
        assertEquals(zone, requestedZone)
    }

    @Test
    fun succeedsWhenThereIsNothingToCollect() {
        val collector = CollectionRunner { _, _ -> CollectResult.NothingToDo }

        val result = worker(collector).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun retriesWhenTheDeviceHasNotBeenUnlocked() {
        val collector = CollectionRunner { _, _ -> CollectResult.NotUnlocked }

        val result = worker(collector).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private fun worker(collector: CollectionRunner): CollectWorker =
        TestListenableWorkerBuilder<CollectWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = CollectWorker(
                    appContext,
                    workerParameters,
                    collector,
                    Clock.fixed(instant, zone),
                )
            })
            .build()
}
