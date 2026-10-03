package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.room.Room
import androidx.work.Worker
import androidx.work.WorkerParameters
import ca.mattmccormick.screenbudget.data.UsageDatabase
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
    @Volatile
    private var database: UsageDatabase? = null

    fun collector(context: Context): Collector = Collector(
        dao = database(context).usageDao(),
        source = UsageEventsSource(context),
    )

    private fun database(context: Context): UsageDatabase = database ?: synchronized(this) {
        database ?: Room.databaseBuilder(
            context.applicationContext,
            UsageDatabase::class.java,
            "screen-budget.db",
        ).build().also { database = it }
    }
}
