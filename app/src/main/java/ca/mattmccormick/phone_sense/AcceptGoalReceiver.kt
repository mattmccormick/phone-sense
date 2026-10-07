package ca.mattmccormick.phone_sense

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.LocalDate
import kotlin.concurrent.thread

internal class AcceptGoalReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        thread(name = "accept-weekly-goal") {
            try {
                val database = (context.applicationContext as ScreenBudgetApplication).database
                accept(context, database, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    internal fun accept(context: Context, database: UsageDatabase, intent: Intent) {
        val weekStart = intent.getStringExtra(EXTRA_WEEK_START)?.let(LocalDate::parse) ?: return
        val minutes = intent.getIntExtra(EXTRA_MINUTES, -1).takeIf { it >= 0 } ?: return
        database.goalDao().insert(Goal(weekStart, minutes))
        context.getSystemService(NotificationManager::class.java)
            .cancel(Notifier.WEEKLY_NOTIFICATION_ID)
    }

    companion object {
        private const val EXTRA_WEEK_START = "week_start"
        private const val EXTRA_MINUTES = "minutes"

        fun intent(context: Context, weekStart: LocalDate, minutes: Int): Intent =
            Intent(context, AcceptGoalReceiver::class.java)
                .putExtra(EXTRA_WEEK_START, weekStart.toString())
                .putExtra(EXTRA_MINUTES, minutes)
    }
}
