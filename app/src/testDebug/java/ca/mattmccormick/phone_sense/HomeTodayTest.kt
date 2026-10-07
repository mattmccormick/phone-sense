package ca.mattmccormick.phone_sense

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeTodayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun statusTransitionsIncludeExactLimitAndOvershoot() {
        val used = mutableIntStateOf(39)
        compose.setContent { ScreenBudgetTheme { HomeToday(used.intValue, 50) } }
        compose.onNodeWithText("Within your allowance").assertIsDisplayed()
        compose.runOnIdle { used.intValue = 40 }
        compose.onNodeWithText("Near your allowance").assertIsDisplayed()
        compose.runOnIdle { used.intValue = 50 }
        compose.onNodeWithText("Allowance reached").assertIsDisplayed()
        compose.onNodeWithText("0 min left today").assertIsDisplayed()
        compose.runOnIdle { used.intValue = 58 }
        compose.onNodeWithText("Over your allowance").assertIsDisplayed()
        compose.onNodeWithText("8 min over today").assertIsDisplayed()
    }

    @Test fun exhaustedWeeklyAllowanceHandlesZeroWithoutDivisionByZero() {
        compose.setContent { ScreenBudgetTheme { HomeToday(5, 0) } }
        compose.onNodeWithText("5 min over today").assertIsDisplayed()
    }

    @Test fun missingUsageIsNotPresentedAsZero() {
        compose.setContent { ScreenBudgetTheme { HomeToday(null, 50) } }
        compose.onNodeWithText("Usage unavailable").assertIsDisplayed()
        compose.onNodeWithText("50 min left today").assertDoesNotExist()
    }
}
