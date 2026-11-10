package ca.mattmccormick.phone_sense

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.mattmccormick.phone_sense.data.SettingsRepository
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

fun interface CollectionRunner {
    fun collect(today: LocalDate, zone: ZoneId): CollectResult
}

fun interface DailyNotificationRunner {
    fun daily(today: LocalDate)
}

fun interface UsageAccessChecker {
    fun hasUsageAccess(): Boolean
}

fun interface UsageAccessNotificationRunner {
    fun usageAccessNeeded()
}

fun interface WeeklyNotificationRunner {
    fun weekly(today: LocalDate)
}

class CollectWorker internal constructor(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val collector: CollectionRunner,
    private val clock: Clock,
    private val usageAccessChecker: UsageAccessChecker,
    private val usageAccessNotifier: UsageAccessNotificationRunner,
    private val weekStartDay: DayOfWeek,
    private val dailyNotifier: DailyNotificationRunner,
    private val weeklyNotifier: WeeklyNotificationRunner,
) : Worker(appContext, workerParameters) {
    constructor(appContext: Context, workerParameters: WorkerParameters) : this(
        appContext,
        workerParameters,
        CollectionDependencies.collector(appContext),
        Clock.systemDefaultZone(),
        CollectionDependencies.usageAccessChecker(appContext),
        CollectionDependencies.usageAccessNotifier(appContext),
        CollectionDependencies.settings(appContext).weekStartDay,
        CollectionDependencies.dailyNotifier(appContext),
        CollectionDependencies.weeklyNotifier(appContext),
    )

    override fun doWork(): Result = runCollection()

    private fun runCollection(): Result {
        if (!usageAccessChecker.hasUsageAccess()) {
            usageAccessNotifier.usageAccessNeeded()
            return Result.success()
        }

        val zone = clock.zone
        val today = LocalDate.now(clock)
        return when (collector.collect(today, zone)) {
            is CollectResult.Collected, CollectResult.NothingToDo -> {
                if (today.dayOfWeek == weekStartDay) {
                    weeklyNotifier.weekly(today)
                } else {
                    dailyNotifier.daily(today)
                }
                Result.success()
            }
            CollectResult.NotUnlocked -> Result.retry()
        }
    }
}

internal object CollectionDependencies {
    fun collector(context: Context): Collector = Collector(
        dao = (context.applicationContext as ScreenBudgetApplication).database.usageDao(),
        source = UsageEventsSource(context),
    )

    fun usageAccessChecker(context: Context): UsageAccessChecker {
        val source = UsageEventsSource(context)
        return UsageAccessChecker(source::hasUsageAccess)
    }

    fun usageAccessNotifier(context: Context): UsageAccessNotificationRunner =
        UsageAccessNotificationRunner { Notifier.usageAccessNeeded(context) }

    fun dailyNotifier(context: Context): DailyNotificationRunner {
        val application = context.applicationContext as ScreenBudgetApplication
        return DailyNotificationRunner { today ->
            val settings = runBlocking {
                SettingsRepository(context.settingsDataStore).settings.first()
            }
            Notifier.daily(
                context,
                today,
                settings,
                application.database,
                dailyChart(context, application.database, settings.weekStartDay, today),
            )
        }
    }

    private fun dailyChart(
        context: Context,
        database: UsageDatabase,
        weekStartDay: DayOfWeek,
        today: LocalDate,
    ): HomeChartModel? = try {
        val source = UsageEventsSource(context)
        // Collection stops at yesterday, so today's bar comes from the live snapshot.
        val snapshot = if (source.hasUsageAccess()) {
            (CurrentDayUsageSnapshotReader(source).read(ZoneId.systemDefault())
                as? CurrentDayUsageSnapshotResult.Available)?.snapshot
        } else null
        loadHomeData(database.usageDao(), database.goalDao(), database.appRuleDao(),
            weekStartDay, today, snapshot).chart
    } catch (error: Exception) {
        // The budget line is the point of the notification; post it without the chart.
        Log.w("Notifier", "Could not build the daily chart", error)
        null
    }

    fun weeklyNotifier(context: Context): WeeklyNotificationRunner {
        val application = context.applicationContext as ScreenBudgetApplication
        return WeeklyNotificationRunnerImpl(context, settings(context), application.database)
    }

    fun settings(context: Context) = runBlocking {
        SettingsRepository(context.settingsDataStore).settings.first()
    }
}
