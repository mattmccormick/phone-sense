package ca.mattmccormick.screenbudget

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal fun todayWidgetViews(
    context: Context,
    used: Int? = null,
    allowance: Int? = null,
    capturedAt: Instant? = null,
    message: String? = null,
): RemoteViews = RemoteViews(context.packageName, R.layout.today_widget).apply {
    setOnClickPendingIntent(R.id.widget_root, TodayWidget.openIntent(context))
    setOnClickPendingIntent(R.id.widget_refresh, TodayWidget.refreshIntent(context))
    setTextViewText(R.id.widget_usage, used?.let { context.getString(R.string.widget_minutes, it) } ?: context.getString(R.string.widget_unknown))
    val status = when {
        used == null -> R.string.widget_usage_unavailable
        allowance == null -> R.string.widget_set_goal
        used > allowance -> R.string.widget_over
        used == allowance -> R.string.widget_reached
        used.toLong() * 5 >= allowance.toLong() * 4 -> R.string.widget_near
        else -> R.string.widget_within
    }
    val color = context.getColor(when (status) {
        R.string.widget_over -> R.color.widget_error
        R.string.widget_near, R.string.widget_reached -> R.color.widget_warning
        else -> R.color.widget_accent
    })
    setTextViewText(R.id.widget_status, message ?: context.getString(status))
    setTextColor(R.id.widget_status, color)
    val hasBudget = used != null && allowance != null
    setViewVisibility(R.id.widget_progress, if (hasBudget) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.widget_remaining, if (hasBudget) View.VISIBLE else View.GONE)
    if (used != null && allowance != null) {
        setProgressBar(R.id.widget_progress, allowance.coerceAtLeast(1),
            if (used >= allowance) allowance.coerceAtLeast(1) else used, false)
        setContentDescription(R.id.widget_progress, context.getString(R.string.widget_progress_description, used, allowance))
        setTextViewText(R.id.widget_remaining, context.getString(
            if (used > allowance) R.string.widget_minutes_over else R.string.widget_minutes_left,
            kotlin.math.abs(allowance - used), allowance,
        ))
        setTextColor(R.id.widget_remaining, color)
    }
    val updated = capturedAt?.let {
        val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(context.resources.configuration.locales[0]).withZone(ZoneId.systemDefault())
        context.getString(R.string.widget_updated, formatter.format(it))
    } ?: context.getString(R.string.widget_open_app)
    setTextViewText(R.id.widget_updated, updated)
}
