package ca.mattmccormick.phone_sense

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal object WidgetUpdates {
    // Retain existing names and worker class for schedules persisted by older app versions.
    const val PERIODIC_WORK = "today-widget-periodic"
    const val REFRESH_WORK = "today-widget-refresh"

    fun enable(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TodayWidgetWorker>(15, TimeUnit.MINUTES).build(),
        )
        refresh(context)
    }

    fun refresh(context: Context) {
        if (!hasWidgets(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            REFRESH_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<TodayWidgetWorker>().build(),
        )
    }

    fun disableIfUnused(context: Context) {
        if (hasWidgets(context)) return
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(REFRESH_WORK)
    }

    private fun hasWidgets(context: Context) =
        TodayWidget.ids(context).isNotEmpty() || ChartWidget.ids(context).isNotEmpty()
}
