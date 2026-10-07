package ca.mattmccormick.phone_sense

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenBudgetThemeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun followsSystemAppearance() {
        var light = Color.Unspecified
        var dark = Color.Unspecified
        compose.setContent {
            val configuration = LocalConfiguration.current
            for (night in listOf(false, true)) {
                CompositionLocalProvider(LocalConfiguration provides Configuration(configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                }) {
                    ScreenBudgetTheme {
                        if (night) dark = MaterialTheme.colorScheme.surface
                        else light = MaterialTheme.colorScheme.surface
                    }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(light.luminance() > 0.9f)
            assertTrue(dark.luminance() < 0.1f)
        }
    }
}
