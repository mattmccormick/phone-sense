package ca.mattmccormick.phone_sense

import android.app.AppOpsManager
import android.content.Intent
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import ca.mattmccormick.phone_sense.data.Settings as AppSettings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
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
    fun existingAccessCollectsOnceInTheBackground() {
        val calls = AtomicInteger()
        val ranInBackground = AtomicBoolean()
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { true },
                launchSettings = {},
                collectUsage = {
                    calls.incrementAndGet()
                    ranInBackground.set(Looper.myLooper() != Looper.getMainLooper())
                },
                mainContent = { Text("Phone Sense") },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) { calls.get() == 1 }
        compose.runOnIdle {
            assertEquals(1, calls.get())
            assertEquals(true, ranInBackground.get())
        }
    }

    @Test
    fun missingAccessDoesNotCollect() {
        val calls = AtomicInteger()
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { false },
                launchSettings = {},
                collectUsage = { calls.incrementAndGet() },
                mainContent = { Text("Phone Sense") },
            )
        }

        compose.onNodeWithText("See your screen time").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, calls.get()) }
    }

    @Test
    fun withoutAccessStartsOnUsageAccessScreen() {
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { false },
                launchSettings = {},
                mainContent = {
                    AppNavigationShell(
                        dayDetail = { Text("Day detail screen") },
                    )
                },
            )
        }

        compose.onNodeWithText("See your screen time").assertIsDisplayed()
        compose.onNodeWithText("Day").assertDoesNotExist()
        compose.onNodeWithText("Goals").assertDoesNotExist()
    }

    @Test
    fun allowUsageAccessButtonFiresSettingsIntent() {
        var launchedIntent: Intent? = null
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { false },
                launchSettings = { launchedIntent = it },
                mainContent = { Text("Phone Sense") },
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
        var onboardingFinished = false
        val lifecycleOwner = FakeLifecycleOwner()
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { hasAccess },
                launchSettings = {},
                finishOnboarding = { onboardingFinished = true },
                mainContent = { Text("Phone Sense") },
                lifecycleOwner = lifecycleOwner,
            )
        }
        compose.onNodeWithText("See your screen time").assertIsDisplayed()

        hasAccess = true
        compose.runOnIdle { lifecycleOwner.resume() }

        compose.onNodeWithText("See your screen time").assertDoesNotExist()
        compose.onNodeWithText("Phone Sense").assertIsDisplayed()
        compose.runOnIdle { assertEquals(true, onboardingFinished) }
    }

    @Test
    fun nextLaunchWithCompletedSettingsOpensHome() {
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { true },
                launchSettings = {},
                settings = AppSettings(onboardingDone = true),
                mainContent = { Text("Phone Sense") },
            )
        }

        compose.onNodeWithText("See your screen time").assertDoesNotExist()
        compose.onNodeWithText("Phone Sense").assertIsDisplayed()
    }

    private fun fakeUsageEventsSource(hasAccess: () -> Boolean) = UsageEventsSource(
        eventsQuery = UsageEventsQuery { _, _ -> emptyList() },
        usageAccessQuery = UsageAccessQuery { _, _, _ ->
            if (hasAccess()) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED
        },
        uid = 123,
        packageName = "ca.mattmccormick.phone_sense",
    )

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }
}
