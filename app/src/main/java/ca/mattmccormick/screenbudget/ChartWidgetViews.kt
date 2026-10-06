package ca.mattmccormick.screenbudget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews

internal fun publishChartWidgets(
    context: Context,
    manager: AppWidgetManager,
    ids: IntArray,
    model: HomeChartModel? = null,
    message: String? = null,
) {
    ids.forEach { id ->
        val options = manager.getAppWidgetOptions(id)
        val fallback = SizeF(
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280).coerceAtLeast(220).toFloat(),
            options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220).coerceAtLeast(160).toFloat(),
        )
        val sizes = if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                ?.filter { it.width >= 220 && it.height >= 160 }?.distinct()?.take(4).orEmpty()
        } else emptyList()
        val views = if (Build.VERSION.SDK_INT >= 31 && sizes.isNotEmpty()) {
            RemoteViews(sizes.associateWith { chartWidgetViews(context, it, model, message) })
        } else chartWidgetViews(context, fallback, model, message)
        manager.updateAppWidget(id, views)
    }
}

internal fun chartWidgetViews(
    context: Context,
    size: SizeF,
    model: HomeChartModel? = null,
    message: String? = null,
): RemoteViews = RemoteViews(context.packageName, R.layout.chart_widget).apply {
    setOnClickPendingIntent(R.id.chart_widget_root, TodayWidget.openIntent(context))
    val hasData = model != null && (model.dailyMinutes.any { it != null } || model.goalMinutes.any { it != null })
    setViewVisibility(R.id.chart_widget_image, if (hasData) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.chart_widget_message, if (hasData) View.GONE else View.VISIBLE)
    setViewVisibility(R.id.chart_widget_note, if (hasData) View.VISIBLE else View.GONE)
    setTextViewText(R.id.chart_widget_message, message ?: context.getString(R.string.chart_widget_empty))
    // Always replace the image, including failures, so inaccessible stale data is also cleared.
    setImageViewBitmap(R.id.chart_widget_image, if (hasData) {
        renderChartWidget(context, model, (size.width - 24).coerceIn(196f, 600f),
            (size.height - 66).coerceIn(94f, 400f))
    } else null)
    val description = if (hasData) model.dates.indices.joinToString("; ") { index ->
        context.getString(R.string.chart_widget_day_description, model.dates[index].toString(),
            model.dailyMinutes[index]?.toString() ?: "—", model.goalMinutes[index]?.toString() ?: "—",
            model.weeklyAverageMinutes[index]?.let { "%.1f".format(it) } ?: "—")
    } else message ?: context.getString(R.string.chart_widget_empty)
    setContentDescription(R.id.chart_widget_root,
        "${context.getString(R.string.chart_widget_description)}. $description")
}
