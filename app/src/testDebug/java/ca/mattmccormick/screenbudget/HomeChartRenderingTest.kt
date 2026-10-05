package ca.mattmccormick.screenbudget

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Source
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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

    @Test
    fun recentUsageIsVisibleWhenEarlierWeeksHaveNoData() {
        val today = LocalDate.of(2026, 10, 5)
        val model = chartModel(
            days = listOf(
                DailyUsage(today.minusDays(2), 100, Source.COLLECTED, Instant.EPOCH),
                DailyUsage(today.minusDays(1), 62, Source.COLLECTED, Instant.EPOCH),
                DailyUsage(today, 16, Source.COLLECTED, Instant.EPOCH),
            ),
            goals = emptyList(),
            weekStartDay = DayOfWeek.SATURDAY,
            end = today,
        )
        compose.setContent {
            ScreenBudgetTheme {
                HomeChart(model, Modifier.width(320.dp))
            }
        }

        val chartBounds = compose.onNodeWithContentDescription("Six-week distraction chart")
            .fetchSemanticsNode().boundsInWindow
        val pixels = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }.asImageBitmap().toPixelMap()
        }
        val hasRecentUsage = ((chartBounds.left + chartBounds.width * 3 / 4).toInt() until chartBounds.right.toInt()).any { x ->
            (chartBounds.top.toInt() until chartBounds.bottom.toInt()).any { y ->
                val color = pixels[x, y]
                color.red < 0.25f && color.green in 0.35f..0.55f && color.blue in 0.2f..0.45f
            }
        }
        assertTrue("Recent daily usage must be drawn at the right of the six-week chart", hasRecentUsage)
    }
}
