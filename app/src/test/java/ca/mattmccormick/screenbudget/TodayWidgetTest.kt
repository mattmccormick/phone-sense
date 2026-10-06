package ca.mattmccormick.screenbudget

import android.app.AppOpsManager
import android.os.Process
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val capturedAt = Instant.parse("2026-10-05T17:00:00Z")

    @Test fun progressHandlesNearReachedOverAndZeroAllowance() {
        for ((used, allowance, expected) in listOf(
            Triple(20, 50, "Within your allowance"),
            Triple(40, 50, "Near your allowance"),
            Triple(50, 50, "Allowance reached"),
            Triple(58, 50, "Over your allowance"),
            Triple(5, 0, "Over your allowance"),
        )) {
            val view = todayWidgetViews(context, used, allowance, capturedAt).apply(context, FrameLayout(context))
            assertEquals(expected, view.findViewById<TextView>(R.id.widget_status).text.toString())
            assertEquals("$used min", view.findViewById<TextView>(R.id.widget_usage).text.toString())
            val progress = view.findViewById<ProgressBar>(R.id.widget_progress)
            assertEquals(View.VISIBLE, progress.visibility)
            assertEquals(allowance.coerceAtLeast(1), progress.max)
            assertEquals(if (used >= allowance) progress.max else used, progress.progress)
            assertTrue(view.findViewById<TextView>(R.id.widget_updated).text.startsWith("Updated "))
        }
    }

    @Test fun unavailableRefreshClearsPreviouslyDisplayedBudget() {
        val view = todayWidgetViews(context, 30, 60, capturedAt).apply(context, FrameLayout(context))
        todayWidgetViews(context).reapply(context, view)
        assertEquals("—", view.findViewById<TextView>(R.id.widget_usage).text.toString())
        assertEquals(View.GONE, view.findViewById<ProgressBar>(R.id.widget_progress).visibility)
        assertEquals(View.GONE, view.findViewById<TextView>(R.id.widget_remaining).visibility)
        assertFalse(view.findViewById<TextView>(R.id.widget_updated).text.startsWith("Updated "))
    }

    @Test fun missingGoalStillShowsUsageWithoutInventingAnAllowance() {
        val view = todayWidgetViews(context, 30, capturedAt = capturedAt).apply(context, FrameLayout(context))
        assertEquals("30 min", view.findViewById<TextView>(R.id.widget_usage).text.toString())
        assertEquals("Set a weekly goal in the app", view.findViewById<TextView>(R.id.widget_status).text.toString())
        assertEquals(View.GONE, view.findViewById<ProgressBar>(R.id.widget_progress).visibility)
    }

    @Test fun widgetOpensHomeAndRefreshTargetsOnlyItsOwnReceiver() {
        val open = shadowOf(TodayWidget.openIntent(context)).savedIntent
        assertEquals(AppDestination.HOME.route, open.getStringExtra(EXTRA_INITIAL_DESTINATION))
        assertEquals(ComponentName(context, MainActivity::class.java), open.component)
        val refresh = shadowOf(TodayWidget.refreshIntent(context)).savedIntent
        assertEquals(ComponentName(context, TodayWidget::class.java), refresh.component)
    }

    @Test fun workerPublishesUnavailableStateWhenUsagePermissionIsMissing() = runBlocking {
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED,
        )
        val manager = AppWidgetManager.getInstance(context)
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, TodayWidget::class.java)
            initialLayout = R.layout.today_widget
        }
        shadowOf(manager).addInstalledProvider(info)
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        assertTrue(manager.bindAppWidgetIdIfAllowed(42, info.provider))
        val worker = TestListenableWorkerBuilder<TodayWidgetWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val view = shadowOf(manager).getViewFor(42)
        assertEquals(context.getString(R.string.widget_usage_unavailable),
            view.findViewById<TextView>(R.id.widget_status).text.toString())
        assertEquals("—", view.findViewById<TextView>(R.id.widget_usage).text.toString())
    }
}
