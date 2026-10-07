package ca.mattmccormick.phone_sense

import android.app.AppOpsManager
import android.os.Process
import android.os.Bundle
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun compactAndSmallLayoutsShowBudgetStates() {
        for (small in listOf(false, true)) {
            for ((used, allowance, expected) in listOf(
                Triple(22, 60, "38 min left"), Triple(48, 60, "12 min left"),
                Triple(60, 60, "0 min left"), Triple(68, 60, "8 min over"),
                Triple(5, 0, "5 min over"), Triple(0, 0, "0 min left"),
            )) {
                val view = todayWidgetViews(context, used, allowance, small).apply(context, FrameLayout(context))
                assertEquals(if (small) "$used / $allowance" else expected,
                    view.findViewById<TextView>(R.id.widget_value).text.toString())
                val over = used > allowance
                val progress = view.findViewById<ProgressBar>(if (over) R.id.widget_bar_over else R.id.widget_bar)
                assertEquals(View.VISIBLE, progress.visibility)
                assertEquals(allowance.coerceAtLeast(1), progress.max)
                assertEquals(if (used >= allowance) progress.max else used, progress.progress)
                assertTrue(view.contentDescription.contains(expected))
                if (small) assertEquals(if (over) "min · ${used - allowance} over" else "min",
                    view.findViewById<TextView>(R.id.widget_detail).text.toString())
                else assertEquals("$used / $allowance min", view.findViewById<TextView>(R.id.widget_usage).text.toString())
            }
        }
    }

    @Test fun unavailableRefreshClearsPreviouslyDisplayedBudget() {
        for (small in listOf(false, true)) {
            val view = todayWidgetViews(context, 30, 60, small).apply(context, FrameLayout(context))
            todayWidgetViews(context, small = small).reapply(context, view)
            assertEquals("—", view.findViewById<TextView>(R.id.widget_value).text.toString())
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_progress).visibility)
            assertEquals("Open app", view.findViewById<TextView>(R.id.widget_detail).text.toString())
            assertFalse(view.contentDescription.contains("60"))
        }
    }

    @Test fun missingGoalStillShowsUsageWithoutInventingAnAllowance() {
        for (small in listOf(false, true)) {
            val view = todayWidgetViews(context, 30, small = small).apply(context, FrameLayout(context))
            assertEquals("30 min", view.findViewById<TextView>(R.id.widget_value).text.toString())
            assertEquals("Set goal", view.findViewById<TextView>(R.id.widget_detail).text.toString())
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_progress).visibility)
        }
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun smallestLayoutFitsLongNumbersAtOneCellSize() {
        val view = todayWidgetViews(context, 1440, 1200, small = true).apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        val width = (82 * density).toInt()
        val height = (96 * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        val value = view.findViewById<TextView>(R.id.widget_value)
        assertEquals(1, value.lineCount)
        assertTrue(value.layout.getLineWidth(0) <= value.width)
        val bar = view.findViewById<View>(R.id.widget_progress)
        assertTrue("bar=${bar.top}..${bar.bottom}, height=$height padding=${view.paddingBottom} value=${value.top}..${value.bottom}",
            bar.bottom <= height - view.paddingBottom)
    }

    @Test @Config(sdk = [30]) fun olderLaunchersChooseLayoutPerWidgetWidth() {
        val manager = AppWidgetManager.getInstance(context)
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, TodayWidget::class.java)
            initialLayout = R.layout.today_widget
        }
        shadowOf(manager).addInstalledProvider(info)
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        for ((id, width) in listOf(41 to 82, 42 to 180)) {
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, info.provider))
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            })
        }
        publishTodayWidgets(context, manager, intArrayOf(41, 42), 22, 60)
        assertEquals("22 / 60", shadowOf(manager).getViewFor(41).findViewById<TextView>(R.id.widget_value).text.toString())
        assertEquals("38 min left", shadowOf(manager).getViewFor(42).findViewById<TextView>(R.id.widget_value).text.toString())
    }

    @Test fun widgetOpensHome() {
        val open = shadowOf(TodayWidget.openIntent(context)).savedIntent
        assertEquals(AppDestination.HOME.route, open.getStringExtra(EXTRA_INITIAL_DESTINATION))
        assertEquals(ComponentName(context, MainActivity::class.java), open.component)
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
            view.findViewById<TextView>(R.id.widget_detail).text.toString())
        assertEquals("—", view.findViewById<TextView>(R.id.widget_value).text.toString())
    }
}
