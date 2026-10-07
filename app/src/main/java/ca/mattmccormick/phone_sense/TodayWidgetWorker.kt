package ca.mattmccormick.phone_sense

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.mattmccormick.phone_sense.data.SettingsRepository
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
        // App-triggered and periodic refreshes can run together; publish them in read order.
        refreshMutex.withLock {
            val context = applicationContext
            val ids = TodayWidget.ids(context)
            val chartIds = ChartWidget.ids(context)
            if (ids.isEmpty() && chartIds.isEmpty()) return@withLock Result.success()
            val manager = AppWidgetManager.getInstance(context)
            try {
                val source = UsageEventsSource(context)
                val zone = ZoneId.systemDefault()
                val snapshot = if (source.hasUsageAccess()) {
                    (CurrentDayUsageSnapshotReader(source).read(zone) as? CurrentDayUsageSnapshotResult.Available)?.snapshot
                } else null
                if (snapshot == null) {
                    publishTodayWidgets(context, manager, ids, message = context.getString(R.string.widget_usage_unavailable))
                    publishChartWidgets(context, manager, chartIds, message = context.getString(R.string.chart_widget_unavailable))
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
                publishTodayWidgets(context, manager, ids, used, allowance)
                publishChartWidgets(context, manager, chartIds, data.chart)
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("TodayWidget", "Could not refresh widget", error)
                publishTodayWidgets(context, manager, ids, message = context.getString(R.string.widget_refresh_failed))
                publishChartWidgets(context, manager, chartIds, message = context.getString(R.string.chart_widget_failed))
                Result.retry()
            }
        }
    }

    private companion object {
        val refreshMutex = Mutex()
    }
}
