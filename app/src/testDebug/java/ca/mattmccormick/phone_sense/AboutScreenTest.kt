package ca.mattmccormick.phone_sense

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.mattmccormick.phone_sense.data.Settings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aboutIsReachableFromSettingsAndListsLicenses() {
        compose.setContent {
            HomeWithSettings(settings = Settings()) { Text("Home") }
        }

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("About").performScrollTo().performClick()

        compose.onNodeWithText("Phone Sense").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("GNU General Public License v3.0 or later")
            .performScrollTo()
            .assertIsDisplayed()
        dependencyLicenses.forEach { dependency ->
            compose.onNodeWithText("${dependency.dependency}: ${dependency.license}")
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
}
