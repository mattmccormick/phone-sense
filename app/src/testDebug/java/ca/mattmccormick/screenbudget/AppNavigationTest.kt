package ca.mattmccormick.screenbudget

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun navigationMovesFromDayDetailToGoalsAndBack() {
        compose.setContent {
            AppNavigationShell(
                initialDestination = AppDestination.DAY_DETAIL,
                dayDetail = { Text("Day detail screen") },
                goals = { Text("Goals screen") },
            )
        }

        compose.onNodeWithText("Day detail screen").assertIsDisplayed()
        compose.onNodeWithText("Goals screen").assertDoesNotExist()

        compose.onNodeWithText("Goals").performClick()
        compose.onNodeWithText("Goals screen").assertIsDisplayed()

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Day detail screen").assertIsDisplayed()
    }

    @Test
    fun goalsIntentSelectsGoalsAsTheInitialDestination() {
        val intent = Intent().putExtra(
            EXTRA_INITIAL_DESTINATION,
            AppDestination.GOALS.route,
        )
        val destination = AppDestination.from(intent)

        compose.setContent {
            AppNavigationShell(
                initialDestination = destination,
                dayDetail = { Text("Day detail screen") },
                goals = { Text("Goals screen") },
            )
        }

        assertEquals(AppDestination.GOALS, destination)
        compose.onNodeWithText("Goals screen").assertIsDisplayed()
        compose.onNodeWithText("Day detail screen").assertDoesNotExist()
    }
}
