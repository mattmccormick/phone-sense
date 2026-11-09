package ca.mattmccormick.phone_sense

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings as AndroidSettings
import androidx.core.content.edit
import ca.mattmccormick.phone_sense.budget.WeeklySummary
import ca.mattmccormick.phone_sense.budget.displayBudget
import ca.mattmccormick.phone_sense.budget.distractionMinutes
import ca.mattmccormick.phone_sense.budget.formatHoursMinutes
import ca.mattmccormick.phone_sense.budget.remainingDailyBudget
import ca.mattmccormick.phone_sense.budget.weekStart
import ca.mattmccormick.phone_sense.data.Settings
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal object Notifier {
    private const val DAILY_NOTIFICATION_ID = 1
    internal const val WEEKLY_NOTIFICATION_ID = 2
    private const val USAGE_ACCESS_NOTIFICATION_ID = 3
    private const val PREFERENCES = "notifications"
    private const val LAST_DAILY_NOTIFICATION = "last_daily_notification"
    private const val LAST_WEEKLY_NOTIFICATION = "last_weekly_notification"

    // The big picture area is about 2:1; these are the renderer's dp-like units.
    private const val CHART_WIDTH = 360f
    private const val CHART_HEIGHT = 180f

    @Synchronized
    fun usageAccessNeeded(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (notificationManager.activeNotifications.any {
                it.id == USAGE_ACCESS_NOTIFICATION_ID
            }
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            USAGE_ACCESS_NOTIFICATION_ID,
            Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, NotificationChannels.USAGE_ACCESS_NEEDED)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Usage access is needed to collect screen time.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(USAGE_ACCESS_NOTIFICATION_ID, notification)
    }

    @Synchronized
    fun daily(
        context: Context,
        today: LocalDate,
        settings: Settings,
        database: UsageDatabase,
        chart: HomeChartModel? = null,
    ) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getString(LAST_DAILY_NOTIFICATION, null) == today.toString()) return

        val currentWeekStart = weekStart(today, settings.weekStartDay)
        val goal = database.goalDao().forWeek(currentWeekStart)
        val (channel, text, destination) = if (goal == null) {
            Triple(
                NotificationChannels.GOAL_NEEDED,
                "Set a goal for this week.",
                AppDestination.HOME,
            )
        } else {
            val excluded = database.appRuleDao().excludedKeys().toSet()
            val dayIndex = ChronoUnit.DAYS.between(currentWeekStart, today).toInt()
            val usedSoFar = if (dayIndex == 0) {
                0
            } else {
                database.usageDao()
                    .daysBetween(currentWeekStart, today.minusDays(1))
                    .sumOf { distractionMinutes(it.day, it.apps, excluded) }
            }
            val budget = displayBudget(
                goal.minutes,
                remainingDailyBudget(goal.minutes, usedSoFar, dayIndex),
            )
            Triple(
                NotificationChannels.DAILY_BUDGET,
                "Max usage today to meet goal: ${formatHoursMinutes(budget)}",
                AppDestination.DAY_DETAIL,
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            DAILY_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_INITIAL_DESTINATION, destination.route),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .withChart(context, chart, text)

        context.getSystemService(NotificationManager::class.java)
            .notify(DAILY_NOTIFICATION_ID, builder.build())
        preferences.edit { putString(LAST_DAILY_NOTIFICATION, today.toString()) }
    }

    @Synchronized
    fun weekly(
        context: Context,
        weekStart: LocalDate,
        summary: WeeklySummary,
        chart: HomeChartModel? = null,
    ) {
        if (wasWeeklyNotificationPosted(context, weekStart)) return
        val achievement = if (summary.achieved) "Goal achieved" else "Goal not achieved"
        val text = "Average: ${formatHoursMinutes(summary.average)} · $achievement · " +
            "Recommended goal: ${formatHoursMinutes(summary.recommendation)}"
        val acceptIntent = PendingIntent.getBroadcast(
            context,
            WEEKLY_NOTIFICATION_ID,
            AcceptGoalReceiver.intent(context, weekStart, summary.recommendation),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, NotificationChannels.WEEKLY_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .addAction(Notification.Action.Builder(null, "Accept", acceptIntent).build())
            .setAutoCancel(true)
            .withChart(context, chart, text)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(WEEKLY_NOTIFICATION_ID, notification)
        markWeeklyNotificationPosted(context, weekStart)
    }

    @Synchronized
    fun weeklyIncomplete(
        context: Context,
        weekStart: LocalDate,
        missingDays: Int,
        chart: HomeChartModel? = null,
    ) {
        if (wasWeeklyNotificationPosted(context, weekStart)) return
        val dayLabel = if (missingDays == 1) "day" else "days"
        val text = "Weekly summary unavailable: $missingDays $dayLabel missing."
        val notification = Notification.Builder(context, NotificationChannels.WEEKLY_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .withChart(context, chart, text)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(WEEKLY_NOTIFICATION_ID, notification)
        markWeeklyNotificationPosted(context, weekStart)
    }

    @Synchronized
    fun forgetDailyPosted(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { remove(LAST_DAILY_NOTIFICATION) }
    }

    @Synchronized
    fun forgetWeeklyPosted(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { remove(LAST_WEEKLY_NOTIFICATION) }
    }

    private fun Notification.Builder.withChart(
        context: Context,
        chart: HomeChartModel?,
        summaryText: String,
    ): Notification.Builder {
        if (chart == null || !chart.hasData) return this
        val picture = renderChartWidget(context, chart, CHART_WIDTH, CHART_HEIGHT)
        return setLargeIcon(picture)
            .setStyle(
                Notification.BigPictureStyle()
                    .bigPicture(picture)
                    // The expanded view shows the picture, so drop the duplicate thumbnail.
                    .bigLargeIcon(null as Icon?)
                    .setSummaryText(summaryText),
            )
    }

    private fun wasWeeklyNotificationPosted(context: Context, weekStart: LocalDate): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LAST_WEEKLY_NOTIFICATION, null) == weekStart.toString()

    private fun markWeeklyNotificationPosted(context: Context, weekStart: LocalDate) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit { putString(LAST_WEEKLY_NOTIFICATION, weekStart.toString()) }
    }
}
