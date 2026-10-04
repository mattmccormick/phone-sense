package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.mattmccormick.screenbudget.data.SettingsRepository
import java.time.Clock
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

class CollectWorker internal constructor(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val collector: CollectionRunner,
    private val clock: Clock,
    private val usageAccessChecker: UsageAccessChecker,
    private val usageAccessNotifier: UsageAccessNotificationRunner,
    private val dailyNotifier: DailyNotificationRunner,
) : Worker(appContext, workerParameters) {
    constructor(appContext: Context, workerParameters: WorkerParameters) : this(
        appContext,
        workerParameters,
        CollectionDependencies.collector(appContext),
        Clock.systemDefaultZone(),
        CollectionDependencies.usageAccessChecker(appContext),
        CollectionDependencies.usageAccessNotifier(appContext),
        CollectionDependencies.dailyNotifier(appContext),
    )

    override fun doWork(): Result {
        if (!usageAccessChecker.hasUsageAccess()) {
            usageAccessNotifier.usageAccessNeeded()
            return Result.success()
        }

        val zone = clock.zone
        val today = LocalDate.now(clock)
        return when (collector.collect(today, zone)) {
            is CollectResult.Collected, CollectResult.NothingToDo -> {
                dailyNotifier.daily(today)
                Result.success()
            }
            CollectResult.NotUnlocked -> Result.retry()
        }
    }
}

private object CollectionDependencies {
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
            Notifier.daily(context, today, settings, application.database)
        }
    }
}
