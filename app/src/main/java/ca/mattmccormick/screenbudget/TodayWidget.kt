package ca.mattmccormick.screenbudget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TodayWidgetWorker>(15, TimeUnit.MINUTES).build(),
        )
        refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == REFRESH_ACTION) refresh(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK)
    }

    companion object {
        internal const val PERIODIC_WORK = "today-widget-periodic"
        internal const val REFRESH_WORK = "today-widget-refresh"
        private const val REFRESH_ACTION = "ca.mattmccormick.screenbudget.REFRESH_WIDGET"

        internal fun ids(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodayWidget::class.java))

        internal fun refresh(context: Context) {
            if (ids(context).isEmpty()) return
            WorkManager.getInstance(context).enqueueUniqueWork(
                REFRESH_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<TodayWidgetWorker>().build(),
            )
        }

        internal fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, TodayWidget::class.java).setAction(REFRESH_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        internal fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_INITIAL_DESTINATION, AppDestination.HOME.route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
