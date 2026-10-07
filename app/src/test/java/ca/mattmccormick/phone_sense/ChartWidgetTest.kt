package ca.mattmccormick.phone_sense

import android.app.AppOpsManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Source
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val today = LocalDate.of(2026, 10, 5)
    private fun model() = chartModel(
        listOf(DailyUsage(today.minusDays(1), 100, Source.COLLECTED, Instant.EPOCH),
            DailyUsage(today, 16, Source.COLLECTED, Instant.EPOCH)),
        listOf(Goal(today.minusDays(2), 60)), DayOfWeek.SATURDAY, today,
    )

    @Test fun recentUsageAndWeeklyGoalAreVisibleAtMinimumAndLargeSizes() {
        for ((width, height) in listOf(196f to 94f, 400f to 240f)) {
            val bitmap = renderChartWidget(context, model(), width, height)
            val accent = context.getColor(R.color.widget_accent)
            val average = context.getColor(R.color.chart_widget_average)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            // Only the rightmost chart week contains data; missing history must not hide it.
            val rightSide = (0 until bitmap.height * 2 / 3).flatMap { y ->
                (bitmap.width * 3 / 4 until bitmap.width).map { x -> pixels[y * bitmap.width + x] }
            }
            assertTrue("Today outline should be visible", rightSide.any { pixel ->
                Color.alpha(pixel) > 60 && kotlin.math.abs(Color.red(pixel) - Color.red(accent)) <= 3 &&
                    kotlin.math.abs(Color.green(pixel) - Color.green(accent)) <= 3 &&
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(accent)) <= 3
            })
            assertTrue("Weekly average should be visible", rightSide.contains(average))
            assertTrue("Goal should be visible", rightSide.contains(context.getColor(R.color.widget_secondary)))
        }
    }

    @Test fun unavailableStateClearsRenderedChartAndExplainsHowToRestoreIt() {
        val size = SizeF(280f, 220f)
        val view = chartWidgetViews(context, size, model()).apply(context, FrameLayout(context))
        assertEquals(View.VISIBLE, view.findViewById<ImageView>(R.id.chart_widget_image).visibility)
        assertTrue(view.contentDescription.contains("2026-10-05: usage 16 min"))
        val message = context.getString(R.string.chart_widget_unavailable)
        chartWidgetViews(context, size, message = message).reapply(context, view)
        assertEquals(View.GONE, view.findViewById<ImageView>(R.id.chart_widget_image).visibility)
        assertNull((view.findViewById<ImageView>(R.id.chart_widget_image).drawable as? BitmapDrawable)?.bitmap)
        assertEquals(message, view.findViewById<TextView>(R.id.chart_widget_message).text.toString())
        assertFalse(view.contentDescription.contains("usage 16 min"))
    }

    @Test fun emptyHistoryShowsMessageWhileGoalOnlyHistoryStillShowsChart() {
        val size = SizeF(280f, 220f)
        val empty = chartModel(emptyList(), emptyList(), DayOfWeek.SATURDAY, today)
        val view = chartWidgetViews(context, size, empty).apply(context, FrameLayout(context))
        assertEquals(context.getString(R.string.chart_widget_empty),
            view.findViewById<TextView>(R.id.chart_widget_message).text.toString())
        val goal = chartModel(emptyList(), listOf(Goal(today.minusDays(2), 60)), DayOfWeek.SATURDAY, today)
        chartWidgetViews(context, size, goal).reapply(context, view)
        assertEquals(View.VISIBLE, view.findViewById<ImageView>(R.id.chart_widget_image).visibility)
    }

    @Test @Config(sdk = [30]) fun olderLaunchersRenderEachInstanceAtItsOwnSize() {
        val manager = AppWidgetManager.getInstance(context)
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, ChartWidget::class.java)
            initialLayout = R.layout.chart_widget
        }
        shadowOf(manager).addInstalledProvider(info)
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        for ((id, width) in listOf(51 to 220, 52 to 400)) {
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, info.provider))
            manager.updateAppWidgetOptions(id, Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220)
            })
        }
        publishChartWidgets(context, manager, intArrayOf(51, 52), model())
        val small = shadowOf(manager).getViewFor(51).findViewById<ImageView>(R.id.chart_widget_image).drawable
        val large = shadowOf(manager).getViewFor(52).findViewById<ImageView>(R.id.chart_widget_image).drawable
        assertTrue(large.intrinsicWidth > small.intrinsicWidth)
    }

    @Test fun sharedWorkerRefreshesChartWhenThereIsNoTodayWidget() = runBlocking {
        val manager = AppWidgetManager.getInstance(context)
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, ChartWidget::class.java)
            initialLayout = R.layout.chart_widget
        }
        shadowOf(manager).addInstalledProvider(info)
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        assertTrue(manager.bindAppWidgetIdIfAllowed(53, info.provider))
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName, AppOpsManager.MODE_IGNORED,
        )
        assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<TodayWidgetWorker>(context).build().doWork())
        assertEquals(context.getString(R.string.chart_widget_unavailable),
            shadowOf(manager).getViewFor(53).findViewById<TextView>(R.id.chart_widget_message).text.toString())
    }
}
