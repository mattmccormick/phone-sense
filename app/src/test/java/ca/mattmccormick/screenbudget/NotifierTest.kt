package ca.mattmccormick.screenbudget

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.screenbudget.budget.WeeklySummary
import ca.mattmccormick.screenbudget.data.AppUsage
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 3, 9)
    private lateinit var database: UsageDatabase
    private lateinit var notificationManager: NotificationManager

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, UsageDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
        context.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun dailyNotificationShowsTheDisplayedBudgetForTheCurrentWeeksGoal() {
        val weekStart = LocalDate.of(2026, 3, 7)
        database.goalDao().insert(Goal(weekStart, 60))
        database.usageDao().insert(
            DailyUsage(weekStart, 200, Source.COLLECTED, Instant.EPOCH),
            listOf(AppUsage(weekStart, "example.app", 200)),
        )

        Notifier.daily(
            context,
            today,
            Settings(weekStartDay = DayOfWeek.SATURDAY),
            database,
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals("Screen Budget", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "Max usage today to meet goal: 00:44",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun dailyNotificationWithoutACurrentGoalOpensGoals() {
        Notifier.daily(
            context,
            today,
            Settings(weekStartDay = DayOfWeek.SATURDAY),
            database,
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(
            "Set a goal for this week.",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(
            AppDestination.GOALS,
            AppDestination.from(shadowOf(notification.contentIntent).savedIntent),
        )
    }

    @Test
    fun dailyBudgetUsesTheDailyChannel() {
        val weekStart = LocalDate.of(2026, 3, 7)
        database.goalDao().insert(Goal(weekStart, 60))

        Notifier.daily(
            context,
            today,
            Settings(weekStartDay = DayOfWeek.SATURDAY),
            database,
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(NotificationChannels.DAILY_BUDGET, notification.channelId)
    }

    @Test
    fun weeklySummaryUsesTheWeeklyChannel() {
        Notifier.weekly(
            context,
            today,
            WeeklySummary(average = 60, achieved = true, recommendation = 54),
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(NotificationChannels.WEEKLY_SUMMARY, notification.channelId)
    }

    @Test
    fun weeklySummaryShowsAverageAchievementAndRecommendation() {
        Notifier.weekly(
            context,
            today,
            WeeklySummary(average = 125, achieved = true, recommendation = 112),
        )

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(
            "Average: 02:05 · Goal achieved · Recommended goal: 01:52",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
    }

    @Test
    fun acceptingTheRecommendationWritesTheNewGoalAndDismissesTheNotification() {
        Notifier.weekly(
            context,
            today,
            WeeklySummary(average = 125, achieved = true, recommendation = 112),
        )
        val notification = shadowOf(notificationManager).allNotifications.single()
        val action = notification.actions.single()

        assertEquals("Accept", action.title.toString())
        AcceptGoalReceiver().accept(
            context,
            database,
            shadowOf(action.actionIntent).savedIntent,
        )

        assertEquals(Goal(today, 112), database.goalDao().forWeek(today))
        assertEquals(0, shadowOf(notificationManager).size())
    }

    @Test
    fun incompleteWeekShowsTheMissingDayCountWithoutARecommendation() {
        val previousWeekStart = today.minusWeeks(1)
        database.goalDao().insert(Goal(previousWeekStart, 120))
        repeat(5) { offset ->
            val date = previousWeekStart.plusDays(offset.toLong())
            database.usageDao().insert(
                DailyUsage(date, 100, Source.COLLECTED, Instant.EPOCH),
                emptyList(),
            )
        }

        WeeklyNotificationRunnerImpl(
            context,
            Settings(weekStartDay = DayOfWeek.MONDAY),
            database,
        ).weekly(today)

        val notification = shadowOf(notificationManager).allNotifications.single()
        assertEquals(
            "Weekly summary unavailable: 2 days missing.",
            notification.extras.getString(Notification.EXTRA_TEXT),
        )
        assertEquals(0, notification.actions?.size ?: 0)
    }
}
