package ca.mattmccormick.phone_sense

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

internal object NotificationChannels {
    const val DAILY_BUDGET = "daily_budget"
    const val WEEKLY_SUMMARY = "weekly_summary"
    const val GOAL_NEEDED = "goal_needed"
    const val USAGE_ACCESS_NEEDED = "usage_access_needed"

    fun create(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    DAILY_BUDGET,
                    context.getString(R.string.daily_budget_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    WEEKLY_SUMMARY,
                    context.getString(R.string.weekly_summary_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    GOAL_NEEDED,
                    context.getString(R.string.goal_needed_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    USAGE_ACCESS_NEEDED,
                    context.getString(R.string.usage_access_needed_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }
}
