package ca.mattmccormick.screenbudget

import android.app.AppOpsManager
import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun withoutAccessStartsOnUsageAccessScreen() {
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { false },
                launchSettings = {},
                mainContent = { Text("Screen Budget") },
            )
        }

        compose.onNodeWithText("See your screen time").assertIsDisplayed()
        compose.onNodeWithText("Screen Budget").assertDoesNotExist()
    }

    @Test
    fun allowUsageAccessButtonFiresSettingsIntent() {
        var launchedIntent: Intent? = null
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { false },
                launchSettings = { launchedIntent = it },
                mainContent = { Text("Screen Budget") },
            )
        }

        compose.onNodeWithText("Allow usage access").performClick()

        compose.runOnIdle {
            assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, launchedIntent?.action)
        }
    }

    @Test
    fun returningWithAccessReplacesOnboardingWithMainScreen() {
        var hasAccess = false
        val lifecycleOwner = FakeLifecycleOwner()
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { hasAccess },
                launchSettings = {},
                mainContent = { Text("Screen Budget") },
                lifecycleOwner = lifecycleOwner,
            )
        }
        compose.onNodeWithText("See your screen time").assertIsDisplayed()

        hasAccess = true
        compose.runOnIdle { lifecycleOwner.resume() }

        compose.onNodeWithText("See your screen time").assertDoesNotExist()
        compose.onNodeWithText("Screen Budget").assertIsDisplayed()
    }

    @Test
    fun existingAccessSkipsUsageAccessScreen() {
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { true },
                launchSettings = {},
                mainContent = { Text("Screen Budget") },
            )
        }

        compose.onNodeWithText("See your screen time").assertDoesNotExist()
        compose.onNodeWithText("Screen Budget").assertIsDisplayed()
    }

    private fun fakeUsageEventsSource(hasAccess: () -> Boolean) = UsageEventsSource(
        eventsQuery = UsageEventsQuery { _, _ -> emptyList() },
        usageAccessQuery = UsageAccessQuery { _, _, _ ->
            if (hasAccess()) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED
        },
        uid = 123,
        packageName = "ca.mattmccormick.screenbudget",
    )

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }
}
