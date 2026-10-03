package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

fun interface CollectionRunner {
    fun collect(today: LocalDate, zone: ZoneId): CollectResult
}

class CollectWorker internal constructor(
    appContext: Context,
    workerParameters: WorkerParameters,
    private val collector: CollectionRunner,
    private val clock: Clock,
) : Worker(appContext, workerParameters) {
    constructor(appContext: Context, workerParameters: WorkerParameters) : this(
        appContext,
        workerParameters,
        CollectionDependencies.collector(appContext),
        Clock.systemDefaultZone(),
    )

    override fun doWork(): Result {
        val zone = clock.zone
        return when (collector.collect(LocalDate.now(clock), zone)) {
            is CollectResult.Collected, CollectResult.NothingToDo -> Result.success()
            CollectResult.NotUnlocked -> Result.retry()
        }
    }
}

private object CollectionDependencies {
    fun collector(context: Context): Collector = Collector(
        dao = (context.applicationContext as ScreenBudgetApplication).database.usageDao(),
        source = UsageEventsSource(context),
    )
}
