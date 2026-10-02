package ca.mattmccormick.screenbudget

data class DailyUsage(
    val totalMillis: Long,
    val perPackageMillis: Map<String, Long>,
)

private data class Interval(val startMs: Long, val endMs: Long)

object UsageAggregator {
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val ACTIVITY_STOPPED = 23

    fun aggregate(events: List<UsageEvent>, startMs: Long, endMs: Long): DailyUsage {
        val activeActivities = mutableSetOf<Pair<String, String>>()
        val packageStartedAt = mutableMapOf<String, Long>()
        val packageTotals = mutableMapOf<String, Long>()
        val intervals = mutableListOf<Interval>()

        fun recordInterval(packageName: String, startedAt: Long, stoppedAt: Long) {
            if (stoppedAt <= startedAt) return
            packageTotals.merge(packageName, stoppedAt - startedAt, Long::plus)
            intervals += Interval(startedAt, stoppedAt)
        }

        events.sortedBy { it.timestampMs }.forEach { event ->
            val activity = event.packageName to event.className
            when (event.type) {
                ACTIVITY_RESUMED -> if (activeActivities.add(activity)) {
                    packageStartedAt.putIfAbsent(event.packageName, event.timestampMs.coerceAtLeast(startMs))
                }

                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> if (activeActivities.remove(activity) &&
                    activeActivities.none { it.first == event.packageName }
                ) {
                    val startedAt = packageStartedAt.remove(event.packageName)!!
                    val stoppedAt = event.timestampMs.coerceAtMost(endMs)
                    recordInterval(event.packageName, startedAt, stoppedAt)
                }
            }
        }

        packageStartedAt.forEach { (packageName, startedAt) ->
            recordInterval(packageName, startedAt, endMs)
        }

        var totalMillis = 0L
        var coveredUntil = startMs
        intervals.sortedBy { it.startMs }.forEach { interval ->
            val uncoveredStart = maxOf(interval.startMs, coveredUntil)
            if (interval.endMs > uncoveredStart) {
                totalMillis += interval.endMs - uncoveredStart
                coveredUntil = interval.endMs
            }
        }

        return DailyUsage(totalMillis, packageTotals)
    }
}
