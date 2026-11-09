package ca.mattmccormick.phone_sense

import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private val tooltipDateFormat = DateTimeFormatter.ofPattern("EEE MMM d")

internal fun dayIndexAt(x: Float, left: Float, cell: Float, dayCount: Int): Int? {
    if (cell <= 0f) return null
    return floor((x - left) / cell).toInt().takeIf { it in 0 until dayCount }
}

internal fun tooltipLines(model: HomeChartModel, index: Int): List<String> {
    val minutes = model.dailyMinutes[index]
    val goal = model.goalMinutes[index]
    val difference = if (minutes != null && goal != null) minutes - goal else null
    return listOf(
        model.dates[index].format(tooltipDateFormat) +
            if (model.includesToday && index == model.dates.lastIndex) " · so far" else "",
        when {
            minutes == null -> "No usage data"
            difference == null -> "$minutes min"
            difference > 0 -> "$minutes min · $difference over goal"
            difference < 0 -> "$minutes min · ${abs(difference)} under goal"
            else -> "$minutes min · on goal"
        },
        goal?.let { "Goal $it min/day" } ?: "Goal not set",
        model.weeklyAverageMinutes[index]?.let { "Week average ${it.roundToInt()} min" }
            ?: "Week average unavailable",
    )
}
