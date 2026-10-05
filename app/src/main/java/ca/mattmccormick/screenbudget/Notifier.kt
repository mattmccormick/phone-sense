package ca.mattmccormick.screenbudget

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import ca.mattmccormick.screenbudget.budget.WeeklySummary
import ca.mattmccormick.screenbudget.budget.displayBudget
import ca.mattmccormick.screenbudget.budget.distractionMinutes
import ca.mattmccormick.screenbudget.budget.formatHoursMinutes
import ca.mattmccormick.screenbudget.budget.remainingDailyBudget
import ca.mattmccormick.screenbudget.budget.weekStart
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal object Notifier {
    private const val DAILY_NOTIFICATION_ID = 1
    internal const val WEEKLY_NOTIFICATION_ID = 2
    private const val USAGE_ACCESS_NOTIFICATION_ID = 3
    private const val PREFERENCES = "notifications"
    private const val LAST_DAILY_NOTIFICATION = "last_daily_notification"
    private const val LAST_WEEKLY_NOTIFICATION = "last_weekly_notification"

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
        val notification = Notification.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(DAILY_NOTIFICATION_ID, notification)
        preferences.edit().putString(LAST_DAILY_NOTIFICATION, today.toString()).apply()
    }

    @Synchronized
    fun weekly(context: Context, weekStart: LocalDate, summary: WeeklySummary) {
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
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(WEEKLY_NOTIFICATION_ID, notification)
        markWeeklyNotificationPosted(context, weekStart)
    }

    @Synchronized
    fun weeklyIncomplete(context: Context, weekStart: LocalDate, missingDays: Int) {
        if (wasWeeklyNotificationPosted(context, weekStart)) return
        val dayLabel = if (missingDays == 1) "day" else "days"
        val notification = Notification.Builder(context, NotificationChannels.WEEKLY_SUMMARY)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText("Weekly summary unavailable: $missingDays $dayLabel missing.")
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(WEEKLY_NOTIFICATION_ID, notification)
        markWeeklyNotificationPosted(context, weekStart)
    }

    private fun wasWeeklyNotificationPosted(context: Context, weekStart: LocalDate): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LAST_WEEKLY_NOTIFICATION, null) == weekStart.toString()

    private fun markWeeklyNotificationPosted(context: Context, weekStart: LocalDate) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LAST_WEEKLY_NOTIFICATION, weekStart.toString())
            .apply()
    }
}
