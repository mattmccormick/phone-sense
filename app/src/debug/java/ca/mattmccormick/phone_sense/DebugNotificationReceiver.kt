package ca.mattmccormick.phone_sense

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import kotlin.concurrent.thread

internal class DebugNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        thread(name = "debug-notification") {
            try {
                post(context, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal fun post(context: Context, intent: Intent) {
        val today = LocalDate.now()
        when (intent.action) {
            ACTION_DAILY -> {
                Notifier.forgetDailyPosted(context)
                CollectionDependencies.dailyNotifier(context).daily(today)
            }
            ACTION_WEEKLY -> {
                Notifier.forgetWeeklyPosted(context)
                CollectionDependencies.weeklyNotifier(context).weekly(today)
            }
        }
    }

    companion object {
        const val ACTION_DAILY = "ca.mattmccormick.phone_sense.debug.POST_DAILY_NOTIFICATION"
        const val ACTION_WEEKLY = "ca.mattmccormick.phone_sense.debug.POST_WEEKLY_NOTIFICATION"
    }
}
