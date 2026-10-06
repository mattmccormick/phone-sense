package ca.mattmccormick.screenbudget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.mattmccormick.screenbudget.data.SettingsRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class TodayWidgetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Manual and periodic refreshes can run together; publish them in read order.
        refreshMutex.withLock {
            val context = applicationContext
            val ids = TodayWidget.ids(context)
            if (ids.isEmpty()) return@withLock Result.success()
            val manager = AppWidgetManager.getInstance(context)
            try {
                val source = UsageEventsSource(context)
                val zone = ZoneId.systemDefault()
                val snapshot = if (source.hasUsageAccess()) {
                    (CurrentDayUsageSnapshotReader(source).read(zone) as? CurrentDayUsageSnapshotResult.Available)?.snapshot
                } else null
                if (snapshot == null) {
                    manager.updateAppWidget(ids, todayWidgetViews(context, message = context.getString(R.string.widget_usage_unavailable)))
                    return@withLock Result.success()
                }
                val database = (context as ScreenBudgetApplication).database
                // Keep earlier days current even when the app hasn't been opened since midnight.
                Collector(database.usageDao(), source).collect(snapshot.date, zone)
                val settings = SettingsRepository(context.settingsDataStore).settings.first()
                val data = loadHomeData(database.usageDao(), database.goalDao(), database.appRuleDao(),
                    settings.weekStartDay, snapshot.date, snapshot)
                val used = data.chart.dailyMinutes.last()
                val allowance = todayAllowance(data.goal, data.usedSoFar, used, snapshot.date, settings.weekStartDay)
                if (snapshot.date != LocalDate.now(zone)) return@withLock Result.retry()
                manager.updateAppWidget(ids, todayWidgetViews(context, used, allowance, snapshot.capturedAt))
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("TodayWidget", "Could not refresh widget", error)
                manager.updateAppWidget(ids, todayWidgetViews(context, message = context.getString(R.string.widget_refresh_failed)))
                Result.retry()
            }
        }
    }

    private companion object {
        val refreshMutex = Mutex()
    }
}
