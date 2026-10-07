package ca.mattmccormick.phone_sense

import android.content.Context
import ca.mattmccormick.phone_sense.budget.distractionMinutes
import ca.mattmccormick.phone_sense.budget.weekStart
import ca.mattmccormick.phone_sense.budget.weeklySummary
import ca.mattmccormick.phone_sense.data.Settings
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.LocalDate

internal class WeeklyNotificationRunnerImpl(
    private val context: Context,
    private val settings: Settings,
    private val database: UsageDatabase,
) : WeeklyNotificationRunner {
    override fun weekly(today: LocalDate) {
        val previousWeekStart = weekStart(today.minusDays(1), settings.weekStartDay)
        val days = database.usageDao().daysBetween(previousWeekStart, today.minusDays(1))
        if (days.size < 7) {
            Notifier.weeklyIncomplete(context, today, 7 - days.size)
            return
        }

        val goal = database.goalDao().forWeek(previousWeekStart) ?: return
        val excluded = database.appRuleDao().excludedKeys().toSet()
        val summary = weeklySummary(
            days.map { distractionMinutes(it.day, it.apps, excluded) },
            goal.minutes,
            settings.reductionPercent,
        )
        Notifier.weekly(context, today, summary)
    }
}
