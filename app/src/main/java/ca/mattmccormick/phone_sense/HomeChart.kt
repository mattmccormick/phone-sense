package ca.mattmccormick.phone_sense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val CHART_HEIGHT = 212.dp
private val PLOT_LEFT = 28.dp
private val PLOT_RIGHT_INSET = 4.dp
private val TOOLTIP_PADDING = 8.dp

private fun Density.cellWidth(width: Float, dayCount: Int) =
    (width - PLOT_LEFT.toPx() - PLOT_RIGHT_INSET.toPx()) / dayCount

private fun Density.tooltipBounds(
    lines: List<TextLayoutResult>,
    index: Int,
    cell: Float,
    width: Float,
): Rect {
    val padding = TOOLTIP_PADDING.toPx()
    val boxWidth = lines.maxOf { it.size.width } + padding * 2
    val boxHeight = lines.sumOf { it.size.height } + padding * 2
    val left = (PLOT_LEFT.toPx() + index * cell + cell / 2 - boxWidth / 2)
        .coerceIn(0f, (width - boxWidth).coerceAtLeast(0f))
    return Rect(Offset(left, 0f), Size(boxWidth, boxHeight))
}

@Composable
internal fun HomeChart(
    model: HomeChartModel,
    modifier: Modifier = Modifier,
    onOpenDay: (LocalDate) -> Unit = {},
) {
    if (model.dailyMinutes.all { it == null } && model.goalMinutes.all { it == null }) {
        Text(
            "No usage data yet",
            modifier.fillMaxWidth().height(CHART_HEIGHT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val tooltipStyle = TextStyle(color = colors.inverseOnSurface, fontSize = 11.sp)
    var selectedDate by remember(model) { mutableStateOf<LocalDate?>(null) }
    val selected = selectedDate?.let { date -> model.dates.indexOf(date).takeIf { it >= 0 } }
    val openDay by rememberUpdatedState(onOpenDay)
    fun tooltipLayouts(index: Int) = tooltipLines(model, index).mapIndexed { line, text ->
        textMeasurer.measure(text,
            if (line == 0) tooltipStyle.copy(fontWeight = FontWeight.Medium) else tooltipStyle)
    }
    val boundaries = (listOf(0) + model.weekBoundaryPositions + model.dates.size).distinct().sorted()
    val maximum = maxOf(
        model.dailyMinutes.filterNotNull().maxOrNull() ?: 0,
        model.goalMinutes.filterNotNull().maxOrNull() ?: 0,
        ceil(model.weeklyAverageMinutes.filterNotNull().maxOrNull() ?: 0.0).toInt(),
    )
    val ceiling = (ceil(maximum.coerceAtLeast(60) / 60.0) * 60).toFloat()
    val description = model.dates.indices.joinToString("; ") { index ->
        "${model.dates[index]}: ${model.dailyMinutes[index]?.let { "$it minutes" } ?: "no usage data"}, " +
            "goal ${model.goalMinutes[index]?.let { "$it minutes" } ?: "not set"}, " +
            "weekly average ${model.weeklyAverageMinutes[index]?.let { "%.1f minutes".format(it) } ?: "unavailable"}"
    }
    val progressSentence = if (model.includesToday) " Today and this week's average are in progress." else ""
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT).pointerInput(model) {
            detectTapGestures { tap ->
                val cell = cellWidth(size.width.toFloat(), model.dates.size)
                val tappedTooltip = selectedDate
                    ?.let { date -> model.dates.indexOf(date).takeIf { it >= 0 } }
                    ?.takeIf { index ->
                        tooltipBounds(tooltipLayouts(index), index, cell, size.width.toFloat())
                            .contains(tap)
                    }
                if (tappedTooltip != null) {
                    openDay(model.dates[tappedTooltip])
                } else {
                    val index = dayIndexAt(tap.x, PLOT_LEFT.toPx(), cell, model.dates.size)
                    selectedDate = index?.let(model.dates::get).takeIf { it != selectedDate }
                }
            }
        }.semantics {
            contentDescription = "Six-week distraction chart"
            stateDescription = "$description.$progressSentence"
        }) {
            val left = PLOT_LEFT.toPx()
            val top = 22.dp.toPx()
            val bottom = size.height - 25.dp.toPx()
            val cell = cellWidth(size.width, model.dates.size)
            fun edge(index: Int) = left + index * cell
            fun y(value: Number) = bottom - value.toFloat() / ceiling * (bottom - top)
            fun label(value: String, x: Float, y: Float, centered: Boolean = false) {
                val layout = textMeasurer.measure(value, labelStyle)
                val start = if (centered) x - layout.size.width / 2 else x
                drawText(layout, topLeft = Offset(start.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)), y))
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
            if (model.includesToday) {
                val currentWeek = model.weekBoundaryPositions.last()
                drawRect(colors.primary.copy(alpha = 0.05f), Offset(edge(currentWeek), top),
                    Size(edge(model.dates.size) - edge(currentWeek), bottom - top))
                label("This week", size.width - 28.dp.toPx(), 0f, centered = true)
            }
            selected?.let { index ->
                drawRect(colors.onSurface.copy(alpha = 0.08f), Offset(edge(index), top),
                    Size(cell, bottom - top))
            }
            listOf(0f, ceiling / 2, ceiling).forEach { value ->
                drawLine(colors.outlineVariant, Offset(left, y(value)), Offset(size.width, y(value)))
                label(value.toInt().toString(), 0f, y(value) - 6.dp.toPx())
            }
            model.weekBoundaryPositions.forEach { index ->
                drawLine(colors.outlineVariant, Offset(edge(index), top), Offset(edge(index), bottom))
                label(model.dates[index].format(DateTimeFormatter.ofPattern("MMM d")),
                    edge(index), bottom + 8.dp.toPx(), centered = true)
            }
            model.dailyMinutes.forEachIndexed { index, value ->
                if (value != null) {
                    val barTop = Offset(edge(index) + cell * 0.22f, y(value))
                    val barSize = Size(cell * 0.56f, bottom - y(value))
                    if (model.includesToday && index == model.dates.lastIndex) {
                        drawRect(colors.primary, barTop, barSize, style = Stroke(1.5.dp.toPx()))
                    } else {
                        drawRect(colors.primary.copy(alpha = 0.15f), barTop, barSize)
                    }
                    if (index == selected) {
                        drawRect(colors.onSurface, barTop, barSize, style = Stroke(1.dp.toPx()))
                    }
                }
            }
            boundaries.zipWithNext().forEach { (start, end) ->
                model.weeklyAverageMinutes[start]?.let { average ->
                    drawLine(colors.secondary, Offset(edge(start), y(average)), Offset(edge(end), y(average)),
                        strokeWidth = 2.dp.toPx())
                }
                model.goalMinutes[start]?.let { goal ->
                    drawLine(colors.outline, Offset(edge(start), y(goal)), Offset(edge(end), y(goal)),
                        strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                    model.goalMinutes.getOrNull(end)?.let { next ->
                        drawLine(colors.outline, Offset(edge(end), y(goal)), Offset(edge(end), y(next)),
                            strokeWidth = 1.5.dp.toPx(), pathEffect = dash)
                    }
                }
            }
            // An open endpoint marks the provisional average without reusing the goal's dash pattern.
            if (model.includesToday) {
                model.weeklyAverageMinutes.lastOrNull()?.let { average ->
                    val marker = Offset(edge(model.dates.lastIndex) + cell / 2, y(average))
                    drawCircle(colors.surface, 3.5.dp.toPx(), marker)
                    drawCircle(colors.secondary, 3.5.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
                }
            }
            selected?.let { index ->
                val lines = tooltipLayouts(index)
                val bounds = tooltipBounds(lines, index, cell, size.width)
                val padding = TOOLTIP_PADDING.toPx()
                drawRoundRect(colors.inverseSurface, bounds.topLeft, bounds.size,
                    CornerRadius(8.dp.toPx()))
                var lineTop = bounds.top + padding
                lines.forEach { line ->
                    drawText(line, topLeft = Offset(bounds.left + padding, lineTop))
                    lineTop += line.size.height
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartLegend("Daily usage", bars = true)
            ChartLegend("Weekly goal", dashed = true)
            ChartLegend("Weekly average", average = true)
            if (model.includesToday) {
                ChartLegend("This week · so far", average = true, provisional = true)
            }
        }
        if (model.includesToday) {
            Text("Outlined bar: today, still in progress", style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChartLegend(label: String, bars: Boolean = false, average: Boolean = false, dashed: Boolean = false, provisional: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(20.dp, 16.dp)) {
            if (bars) {
                drawRect(colors.primary.copy(alpha = 0.15f), Offset(0f, 5.dp.toPx()), Size(6.dp.toPx(), 10.dp.toPx()))
                drawRect(colors.primary.copy(alpha = 0.15f), Offset(9.dp.toPx(), 0f), Size(6.dp.toPx(), 15.dp.toPx()))
            } else {
                drawLine(if (average) colors.secondary else colors.outline, Offset(0f, center.y), Offset(size.width, center.y),
                    strokeWidth = 2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null)
                if (provisional) {
                    val marker = Offset(size.width - 4.dp.toPx(), center.y)
                    drawCircle(colors.surface, 3.5.dp.toPx(), marker)
                    drawCircle(colors.secondary, 3.5.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
