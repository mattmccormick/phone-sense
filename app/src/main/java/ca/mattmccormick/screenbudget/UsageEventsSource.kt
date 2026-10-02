package ca.mattmccormick.screenbudget

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

class UsageEventsSource internal constructor(
    private val eventsQuery: UsageEventsQuery,
    private val usageAccessQuery: UsageAccessQuery,
    private val uid: Int,
    private val packageName: String,
) {
    constructor(context: Context) : this(
        eventsQuery = AndroidUsageEventsQuery(
            context.getSystemService(UsageStatsManager::class.java),
        ),
        usageAccessQuery = AndroidUsageAccessQuery(
            context.getSystemService(AppOpsManager::class.java),
        ),
        uid = Process.myUid(),
        packageName = context.packageName,
    )

    fun hasUsageAccess(): Boolean = usageAccessQuery.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        uid,
        packageName,
    ) == AppOpsManager.MODE_ALLOWED

    fun events(beginMs: Long, endMs: Long): List<UsageEvent>? =
        eventsQuery.queryEvents(beginMs, endMs)?.map { event ->
            UsageEvent(
                type = event.type,
                packageName = event.packageName,
                className = event.className,
                timestampMs = event.timestampMs,
            )
        }
}

internal data class PlatformUsageEvent(
    val type: Int,
    val packageName: String,
    val className: String,
    val timestampMs: Long,
)

internal fun interface UsageEventsQuery {
    fun queryEvents(beginMs: Long, endMs: Long): List<PlatformUsageEvent>?
}

internal fun interface UsageAccessQuery {
    fun checkOpNoThrow(op: String, uid: Int, packageName: String): Int
}

private class AndroidUsageEventsQuery(
    private val usageStatsManager: UsageStatsManager,
) : UsageEventsQuery {
    override fun queryEvents(beginMs: Long, endMs: Long): List<PlatformUsageEvent>? {
        val events = usageStatsManager.queryEvents(beginMs, endMs) ?: return null
        return events.readAll()
    }

    private fun UsageEvents.readAll(): List<PlatformUsageEvent> = buildList {
        val event = UsageEvents.Event()
        while (hasNextEvent()) {
            getNextEvent(event)
            add(
                PlatformUsageEvent(
                    type = event.eventType,
                    packageName = event.packageName.orEmpty(),
                    className = event.className.orEmpty(),
                    timestampMs = event.timeStamp,
                ),
            )
        }
    }
}

private class AndroidUsageAccessQuery(
    private val appOpsManager: AppOpsManager,
) : UsageAccessQuery {
    @Suppress("DEPRECATION")
    override fun checkOpNoThrow(op: String, uid: Int, packageName: String): Int =
        appOpsManager.checkOpNoThrow(op, uid, packageName)
}
