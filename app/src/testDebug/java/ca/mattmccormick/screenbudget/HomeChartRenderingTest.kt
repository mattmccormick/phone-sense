package ca.mattmccormick.screenbudget

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
            MaterialTheme(colorScheme = lightColorScheme(primary = Color.Red)) {
                HomeChart(model, Modifier.width(320.dp))
            }
        }

        val pixels = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also {
                view.draw(Canvas(it))
            }.asImageBitmap().toPixelMap()
        }
        val hasRecentUsage = (pixels.width * 3 / 4 until pixels.width).any { x ->
            (0 until pixels.height).any { y ->
                val color = pixels[x, y]
                color.red > 0.8f && color.green < 0.2f && color.blue < 0.2f
            }
        }
        assertTrue("Recent daily usage must be drawn at the right of the six-week chart", hasRecentUsage)
    }
}
