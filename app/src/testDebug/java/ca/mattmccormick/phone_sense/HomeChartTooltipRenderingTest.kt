package ca.mattmccormick.phone_sense

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Source
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// ScreenBudgetTheme's light inverseSurface, the tooltip panel's fill.
private val panelFill = Color(0xFF2D3730)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeChartTooltipRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.of(2026, 11, 7)

    private fun model(end: LocalDate = today) = chartModel(
        days = (0L..20L).map {
            DailyUsage(end.minusDays(it), 40 + (it * 7 % 60).toInt(), Source.COLLECTED, Instant.EPOCH)
        },
        goals = listOf(Goal(LocalDate.of(2026, 11, 2), 90)),
        weekStartDay = DayOfWeek.MONDAY,
        end = end,
        today = end,
    )

    private fun chart() = compose.onNodeWithContentDescription("Six-week distraction chart")

    private fun tapAcross(fraction: Float) {
        val bounds = chart().fetchSemanticsNode().boundsInRoot
        chart().performTouchInput { click(Offset(bounds.width * fraction, bounds.height / 2)) }
        compose.waitForIdle()
    }

    // The panel fill is the only part of the tooltip that no other chart element draws.
    private fun tooltipPixels(): Int {
        val pixels = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }.asImageBitmap().toPixelMap()
        }
        val fill = panelFill.toArgb()
        return (0 until pixels.width).sumOf { x ->
            (0 until pixels.height).count { y -> pixels[x, y].toArgb() == fill }
        }
    }

    @Test
    fun showsTheTappedDaysPanelAndClearsItOnASecondTap() {
        compose.setContent { ScreenBudgetTheme(darkTheme = false) { HomeChart(model(), Modifier.width(360.dp)) } }

        assertEquals(0, tooltipPixels())
        tapAcross(0.75f)
        assertTrue("A tap on a day must open its tooltip", tooltipPixels() > 1000)
        tapAcross(0.75f)
        assertEquals("A second tap on the same day must close the tooltip", 0, tooltipPixels())
    }

    @Test
    fun clearsTheTooltipOnATapOutsideThePlot() {
        compose.setContent { ScreenBudgetTheme(darkTheme = false) { HomeChart(model(), Modifier.width(360.dp)) } }

        tapAcross(0.75f)
        tapAcross(0.01f)
        assertEquals("A tap left of the plot must close the tooltip", 0, tooltipPixels())
    }

    @Test
    fun clearsTheTooltipWhenTheChartIsRefreshed() {
        var end by mutableStateOf(today)
        compose.setContent { ScreenBudgetTheme(darkTheme = false) { HomeChart(model(end), Modifier.width(360.dp)) } }

        tapAcross(0.75f)
        compose.runOnUiThread { end = today.plusDays(1) }
        compose.waitForIdle()
        assertEquals("A refreshed chart must close the tooltip", 0, tooltipPixels())
    }
}
