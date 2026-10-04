package ca.mattmccormick.screenbudget

import android.app.NotificationManager
import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.DayOfWeek
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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
    fun weekStartPostsTheWeeklyNotificationInsteadOfTheDailyNotification() {
        var dailyNotifications = 0
        var weeklyNotifications = 0
        val collector = CollectionRunner { _, _ -> CollectResult.NothingToDo }

        worker(
            collector = collector,
            weekStartDay = DayOfWeek.MONDAY,
            dailyNotifier = DailyNotificationRunner { dailyNotifications++ },
            weeklyNotifier = WeeklyNotificationRunner { weeklyNotifications++ },
        ).doWork()

        assertEquals(0, dailyNotifications)
        assertEquals(1, weeklyNotifications)
    }

    @Test
    fun retriesWhenTheDeviceHasNotBeenUnlocked() {
        val collector = CollectionRunner { _, _ -> CollectResult.NotUnlocked }

        val result = worker(collector).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun missingUsageAccessPostsAnAlertThatOpensUsageAccessSettings() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
        val collector = CollectionRunner { _, _ -> error("Collection should not run without access") }
        val usageAccessNotifier = UsageAccessNotificationRunner {
            Notifier.usageAccessNeeded(context)
        }

        worker(
            collector = collector,
            hasUsageAccess = UsageAccessChecker { false },
            usageAccessNotifier = usageAccessNotifier,
        ).doWork()

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(NotificationChannels.USAGE_ACCESS_NEEDED, notification.channelId)
        assertEquals(
            AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS,
            shadowOf(notification.contentIntent).savedIntent.action,
        )
    }

    @Test
    fun missingUsageAccessSucceedsWithoutCollecting() {
        val collector = CollectionRunner { _, _ -> error("Collection should not run without access") }

        val result = worker(
            collector = collector,
            hasUsageAccess = UsageAccessChecker { false },
        ).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun doesNotPostTheUsageAccessAlertAgainWhileItIsShowing() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
        val collector = CollectionRunner { _, _ -> error("Collection should not run without access") }
        val usageAccessNotifier = UsageAccessNotificationRunner {
            Notifier.usageAccessNeeded(context)
        }
        val firstWorker = worker(collector, UsageAccessChecker { false }, usageAccessNotifier)
        val secondWorker = worker(collector, UsageAccessChecker { false }, usageAccessNotifier)

        firstWorker.doWork()
        val firstNotification = shadowOf(notificationManager).allNotifications.single()
        secondWorker.doWork()

        assertSame(firstNotification, shadowOf(notificationManager).allNotifications.single())
    }

    @Test
    fun postsAtMostOneDailyNotificationWhenTheWorkerRunsTwice() {
        val database = Room.inMemoryDatabaseBuilder(context, UsageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
        context.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().clear().commit()
        database.goalDao().insert(Goal(LocalDate.of(2026, 3, 7), 60))
        val dailyNotifier = DailyNotificationRunner { today ->
            Notifier.daily(
                context,
                today,
                Settings(weekStartDay = DayOfWeek.SATURDAY),
                database,
            )
        }
        val collector = CollectionRunner { _, _ -> CollectResult.NothingToDo }

        worker(collector, dailyNotifier = dailyNotifier).doWork()
        assertEquals(1, shadowOf(notificationManager).size())
        notificationManager.cancelAll()
        worker(collector, dailyNotifier = dailyNotifier).doWork()

        assertEquals(0, shadowOf(notificationManager).size())
        database.close()
    }

    private fun worker(
        collector: CollectionRunner,
        hasUsageAccess: UsageAccessChecker = UsageAccessChecker { true },
        usageAccessNotifier: UsageAccessNotificationRunner = UsageAccessNotificationRunner {},
        weekStartDay: DayOfWeek = DayOfWeek.SATURDAY,
        dailyNotifier: DailyNotificationRunner = DailyNotificationRunner {},
        weeklyNotifier: WeeklyNotificationRunner = WeeklyNotificationRunner {},
    ): CollectWorker =
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
                    hasUsageAccess,
                    usageAccessNotifier,
                    weekStartDay,
                    dailyNotifier,
                    weeklyNotifier,
                )
            })
            .build()
}
