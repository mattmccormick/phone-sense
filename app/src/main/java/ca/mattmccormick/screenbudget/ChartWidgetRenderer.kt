package ca.mattmccormick.screenbudget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

// RemoteViews cannot host the Compose Canvas used by Home; draw the same model into an image.
internal fun renderChartWidget(context: Context, model: HomeChartModel, width: Float, height: Float): Bitmap {
    val scale = context.resources.displayMetrics.density.coerceAtMost(1.5f)
    val bitmap = Bitmap.createBitmap((width * scale).toInt(), (height * scale).toInt(), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap).apply { scale(scale, scale) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val accent = context.getColor(R.color.widget_accent)
    val averageColor = context.getColor(R.color.chart_widget_average)
    val secondary = context.getColor(R.color.widget_secondary)
    val track = context.getColor(R.color.widget_track)
    val background = context.getColor(R.color.widget_background)
    val boundaries = (listOf(0) + model.weekBoundaryPositions + model.dates.size).distinct().sorted()
    val maximum = maxOf(model.dailyMinutes.filterNotNull().maxOrNull() ?: 0,
        model.goalMinutes.filterNotNull().maxOrNull() ?: 0,
        ceil(model.weeklyAverageMinutes.filterNotNull().maxOrNull() ?: 0.0).toInt())
    val ceiling = (ceil(maximum.coerceAtLeast(60) / 60.0) * 60).toFloat()
    val left = 25f
    val top = 16f
    val bottom = height - 37f
    val cell = (width - left - 2) / model.dates.size
    fun edge(index: Int) = left + index * cell
    fun y(value: Number) = bottom - value.toFloat() / ceiling * (bottom - top)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Int, stroke: Float = 1f, dashed: Boolean = false) {
        paint.color = color
        paint.alpha = 255
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.pathEffect = if (dashed) DashPathEffect(floatArrayOf(4f, 3f), 0f) else null
        canvas.drawLine(x1, y1, x2, y2, paint)
        paint.pathEffect = null
    }
    fun label(text: String, x: Float, baseline: Float, centered: Boolean = false) {
        paint.color = secondary
        paint.alpha = 255
        paint.style = Paint.Style.FILL
        paint.textSize = 9f
        val start = if (centered) x - paint.measureText(text) / 2 else x
        canvas.drawText(text, start.coerceIn(0f, (width - paint.measureText(text)).coerceAtLeast(0f)), baseline, paint)
    }
    paint.color = accent
    paint.alpha = 12
    canvas.drawRect(edge(model.weekBoundaryPositions.last()), top, width, bottom, paint)
    label(context.getString(R.string.chart_widget_this_week), width, 9f, centered = true)
    listOf(0f, ceiling / 2, ceiling).forEach {
        line(left, y(it), width, y(it), track)
        label(it.toInt().toString(), 0f, y(it) + 3f)
    }
    model.weekBoundaryPositions.forEach { index ->
        line(edge(index), top, edge(index), bottom, track)
        label(model.dates[index].format(DateTimeFormatter.ofPattern("MMM d")), edge(index), bottom + 13f, centered = true)
    }
    model.dailyMinutes.forEachIndexed { index, value ->
        if (value != null) {
            paint.color = accent
            paint.alpha = if (index == model.dates.lastIndex) 255 else 55
            paint.style = if (index == model.dates.lastIndex) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = 1.3f
            canvas.drawRect(edge(index) + cell * .22f, y(value), edge(index) + cell * .78f, bottom, paint)
        }
    }
    boundaries.zipWithNext().forEach { (start, end) ->
        model.weeklyAverageMinutes[start]?.let { line(edge(start), y(it), edge(end), y(it), averageColor, 2f) }
        model.goalMinutes[start]?.let { goal ->
            line(edge(start), y(goal), edge(end), y(goal), secondary, 1.3f, dashed = true)
            model.goalMinutes.getOrNull(end)?.let { next ->
                line(edge(end), y(goal), edge(end), y(next), secondary, 1.3f, dashed = true)
            }
        }
    }
    model.weeklyAverageMinutes.lastOrNull()?.let { average ->
        val x = edge(model.dates.lastIndex) + cell / 2
        paint.style = Paint.Style.FILL
        paint.color = background
        canvas.drawCircle(x, y(average), 3f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = averageColor
        canvas.drawCircle(x, y(average), 3f, paint)
    }
    val legendY = height - 5
    val legendCell = width / 3
    line(0f, legendY - 3, 13f, legendY - 3, accent, 4f)
    label(context.getString(R.string.chart_widget_usage), 18f, legendY)
    line(legendCell, legendY - 3, legendCell + 13, legendY - 3, secondary, 1.3f, dashed = true)
    label(context.getString(R.string.chart_widget_goal), legendCell + 18, legendY)
    line(legendCell * 2, legendY - 3, legendCell * 2 + 13, legendY - 3, averageColor, 2f)
    label(context.getString(R.string.chart_widget_average), legendCell * 2 + 18, legendY)
    return bitmap
}
