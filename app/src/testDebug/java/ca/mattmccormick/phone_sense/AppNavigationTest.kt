package ca.mattmccormick.phone_sense

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.time.LocalDate
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
    fun navigationMovesBetweenHomeAndDayWithoutGoalsTab() {
        compose.setContent {
            AppNavigationShell(
                initialDestination = AppDestination.DAY_DETAIL,
                dayDetail = { Text("Day detail screen") },
                home = { Text("Home screen") },
            )
        }

        compose.onNodeWithText("Day detail screen").assertIsDisplayed()
        compose.onNodeWithText("Home screen").assertDoesNotExist()

        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Goals").assertDoesNotExist()
        compose.onNodeWithText("Home screen").assertIsDisplayed()

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Day detail screen").assertIsDisplayed()
    }

    @Test
    fun openingADayFromHomeShowsThatDateAndTheDayTabStillOpensToday() {
        val chosen = LocalDate.of(2026, 11, 3)
        compose.setContent {
            AppNavigationShell(
                dayDetail = { date -> Text("Day detail for ${date ?: "today"}") },
                home = { onOpenDay ->
                    Text("Open a day", Modifier.clickable { onOpenDay(chosen) })
                },
            )
        }

        compose.onNodeWithText("Open a day").performClick()
        compose.onNodeWithText("Day detail for 2026-11-03").assertIsDisplayed()

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Day detail for today").assertIsDisplayed()
    }

    @Test
    fun legacyGoalsIntentOpensHome() {
        val intent = Intent().putExtra(
            EXTRA_INITIAL_DESTINATION,
            "goals",
        )
        val destination = AppDestination.from(intent)

        compose.setContent {
            AppNavigationShell(
                initialDestination = destination,
                dayDetail = { Text("Day detail screen") },
                home = { Text("Home screen") },
            )
        }

        assertEquals(AppDestination.HOME, destination)
        compose.onNodeWithText("Home screen").assertIsDisplayed()
        compose.onNodeWithText("Day detail screen").assertDoesNotExist()
    }
}
