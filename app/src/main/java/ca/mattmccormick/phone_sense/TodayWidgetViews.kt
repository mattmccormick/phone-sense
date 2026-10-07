package ca.mattmccormick.phone_sense

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews

internal fun publishTodayWidgets(
    context: Context,
    manager: AppWidgetManager,
    ids: IntArray,
    used: Int? = null,
    allowance: Int? = null,
    message: String? = null,
) {
    val compact = todayWidgetViews(context, used, allowance, message = message)
    val small = todayWidgetViews(context, used, allowance, small = true, message = message)
    if (Build.VERSION.SDK_INT >= 31) {
        // The launcher selects the layout immediately during resizing, without another data read.
        manager.updateAppWidget(ids, RemoteViews(mapOf(SizeF(56f, 80f) to small, SizeF(160f, 80f) to compact)))
    } else {
        ids.forEach { id ->
            val width = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            manager.updateAppWidget(id, if (width < 160) small else compact)
        }
    }
}

internal fun todayWidgetViews(
    context: Context,
    used: Int? = null,
    allowance: Int? = null,
    small: Boolean = false,
    message: String? = null,
): RemoteViews = RemoteViews(context.packageName, if (small) R.layout.today_widget_small else R.layout.today_widget).apply {
    setOnClickPendingIntent(R.id.widget_root, TodayWidget.openIntent(context))
    val hasBudget = used != null && allowance != null
    val over = used != null && allowance != null && used > allowance
    val ratio = if (hasBudget) context.getString(R.string.widget_ratio, used, allowance) else ""
    val value = when {
        used == null -> context.getString(R.string.widget_unknown)
        allowance == null -> context.getString(R.string.widget_minutes, used)
        small -> ratio
        else -> context.getString(if (over) R.string.widget_minutes_over else R.string.widget_minutes_left,
            kotlin.math.abs(allowance - used))
    }
    val detail = when {
        used == null -> message ?: context.getString(R.string.widget_usage_unavailable)
        allowance == null -> context.getString(R.string.widget_set_goal)
        small && over -> context.getString(R.string.widget_small_over, used - allowance)
        small -> context.getString(R.string.widget_unit)
        else -> ""
    }
    setTextViewText(R.id.widget_value, value)
    setTextColor(R.id.widget_value, context.getColor(if (over && !small) R.color.widget_error else R.color.widget_text))
    setTextViewText(R.id.widget_detail, detail)
    setViewVisibility(R.id.widget_detail, if (detail.isEmpty()) View.GONE else View.VISIBLE)
    if (!small) setTextViewText(R.id.widget_usage,
        if (hasBudget) context.getString(R.string.widget_ratio_minutes, used, allowance) else "")
    setViewVisibility(R.id.widget_progress, if (hasBudget) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.widget_bar, if (over) View.GONE else View.VISIBLE)
    setViewVisibility(R.id.widget_bar_over, if (over) View.VISIBLE else View.GONE)
    if (used != null && allowance != null) {
        val maximum = allowance.coerceAtLeast(1)
        val progress = if (used >= allowance) maximum else used
        for (id in listOf(R.id.widget_bar, R.id.widget_bar_over)) setProgressBar(id, maximum, progress, false)
        setContentDescription(R.id.widget_root,
            context.getString(R.string.widget_progress_description, used, allowance) + ". " +
                context.getString(if (over) R.string.widget_minutes_over else R.string.widget_minutes_left,
                    kotlin.math.abs(allowance - used)))
    } else {
        setContentDescription(R.id.widget_root, "$value. $detail")
    }
}
