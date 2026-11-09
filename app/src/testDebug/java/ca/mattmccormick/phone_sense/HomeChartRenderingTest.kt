package ca.mattmccormick.phone_sense

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Source
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeChartRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun chartBounds() = compose.onNodeWithContentDescription("Six-week distraction chart")
        .fetchSemanticsNode().boundsInWindow

    private fun decorPixels() = compose.runOnIdle {
        val view = compose.activity.window.decorView
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
            view.draw(Canvas(it))
        }.asImageBitmap().toPixelMap()
    }

    // Solid primary is the outlined bar for today: every other primary mark is drawn at 15% or less.
    private fun hasOutlinedBar(fromFractionOfWidth: Float = 0f): Boolean {
        val bounds = chartBounds()
        val pixels = decorPixels()
        return ((bounds.left + bounds.width * fromFractionOfWidth).toInt() until bounds.right.toInt()).any { x ->
            (bounds.top.toInt() until bounds.bottom.toInt()).any { y ->
                val color = pixels[x, y]
                color.red < 0.25f && color.green in 0.35f..0.55f && color.blue in 0.2f..0.45f
            }
        }
    }

    private fun threeRecordedDays(end: LocalDate) = listOf(
        DailyUsage(end.minusDays(2), 100, Source.COLLECTED, Instant.EPOCH),
        DailyUsage(end.minusDays(1), 62, Source.COLLECTED, Instant.EPOCH),
        DailyUsage(end, 16, Source.COLLECTED, Instant.EPOCH),
    )

    @Test
    fun recentUsageIsVisibleWhenEarlierWeeksHaveNoData() {
        val today = LocalDate.of(2026, 10, 5)
        val model = chartModel(
            days = threeRecordedDays(today),
            goals = emptyList(),
            weekStartDay = DayOfWeek.SATURDAY,
            end = today,
            today = today,
        )
        compose.setContent {
            ScreenBudgetTheme {
                HomeChart(model, Modifier.width(320.dp))
            }
        }

        assertTrue("Recent daily usage must be drawn at the right of the six-week chart",
            hasOutlinedBar(fromFractionOfWidth = 0.75f))
    }

    @Test
    fun pastWindowDropsTheMarkersThatOnlyMakeSenseForThisWeek() {
        val end = LocalDate.of(2026, 9, 19)
        val model = chartModel(
            days = threeRecordedDays(end),
            goals = emptyList(),
            weekStartDay = DayOfWeek.SATURDAY,
            end = end,
            today = LocalDate.of(2026, 10, 5),
        )
        compose.setContent {
            ScreenBudgetTheme {
                HomeChart(model, Modifier.width(320.dp))
            }
        }

        val state = compose.onNodeWithContentDescription("Six-week distraction chart")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]
        assertTrue("The window's last day must still be described, got: $state",
            state.contains("$end: 16 minutes"))
        assertFalse("A past window holds nothing in progress, got: $state", state.contains("in progress"))
        compose.onNodeWithText("Outlined bar: today, still in progress").assertDoesNotExist()
        compose.onNodeWithText("This week · so far").assertDoesNotExist()
        assertFalse("A window that ends in the past has no bar for today to outline", hasOutlinedBar())
    }
}
