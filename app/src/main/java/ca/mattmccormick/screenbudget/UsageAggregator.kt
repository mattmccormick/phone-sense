package ca.mattmccormick.screenbudget

data class DailyUsage(
    val totalMillis: Long,
    val perPackageMillis: Map<String, Long>,
)

private data class Interval(val startMs: Long, val endMs: Long)

object UsageAggregator {
    private const val ACTIVITY_RESUMED = 1
    private const val ACTIVITY_PAUSED = 2
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_SHOWN = 17
    private const val ACTIVITY_STOPPED = 23
    private const val DEVICE_SHUTDOWN = 26
    private const val DEVICE_STARTUP = 27

    fun aggregate(events: List<UsageEvent>, startMs: Long, endMs: Long): DailyUsage {
        val activityIsOpen = mutableMapOf<Pair<String, String>, Boolean>()
        val packageStartedAt = mutableMapOf<String, Long>()
        val packageTotals = mutableMapOf<String, Long>()
        val intervals = mutableListOf<Interval>()

        fun recordInterval(packageName: String, startedAt: Long, stoppedAt: Long) {
            if (stoppedAt <= startedAt) return
            packageTotals.merge(packageName, stoppedAt - startedAt, Long::plus)
            intervals += Interval(startedAt, stoppedAt)
        }

        fun closeOpenIntervals(stoppedAt: Long) {
            packageStartedAt.forEach { (packageName, startedAt) ->
                recordInterval(packageName, startedAt, stoppedAt.coerceAtMost(endMs))
            }
            activityIsOpen.replaceAll { _, _ -> false }
            packageStartedAt.clear()
        }

        events.sortedBy { it.timestampMs }.forEach { event ->
            val activity = event.packageName to event.className
            when (event.type) {
                ACTIVITY_RESUMED -> {
                    val startedAt = event.timestampMs.coerceAtLeast(startMs)
                    val packageHasOtherOpenActivity = activityIsOpen.any {
                        (key, isOpen) -> isOpen && key.first == event.packageName && key != activity
                    }
                    activityIsOpen[activity] = true
                    if (!packageHasOtherOpenActivity) {
                        packageStartedAt[event.packageName] = startedAt
                    }
                }

                ACTIVITY_PAUSED, ACTIVITY_STOPPED -> when (activityIsOpen[activity]) {
                    true -> {
                        activityIsOpen[activity] = false
                        if (activityIsOpen.none { (key, isOpen) -> isOpen && key.first == event.packageName }) {
                            val startedAt = packageStartedAt.remove(event.packageName)!!
                            val stoppedAt = event.timestampMs.coerceAtMost(endMs)
                            recordInterval(event.packageName, startedAt, stoppedAt)
                        }
                    }

                    null -> if (activityIsOpen.none { it.key.first == event.packageName }) {
                        activityIsOpen[activity] = false
                        val stoppedAt = event.timestampMs.coerceAtMost(endMs)
                        recordInterval(event.packageName, startMs, stoppedAt)
                    }

                    false -> Unit
                }

                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN ->
                    closeOpenIntervals(event.timestampMs)

                DEVICE_STARTUP -> {
                    activityIsOpen.replaceAll { _, _ -> false }
                    packageStartedAt.clear()
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
