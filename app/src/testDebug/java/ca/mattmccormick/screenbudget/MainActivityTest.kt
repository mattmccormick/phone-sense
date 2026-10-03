package ca.mattmccormick.screenbudget

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Looper
import android.provider.Settings
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import ca.mattmccormick.screenbudget.data.Settings as AppSettings
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun scheduleStepShowsValuesFromSettings() {
        compose.setContent {
            ScheduleOnboardingScreen(
                settings = AppSettings(),
                onFinish = { _, _ -> },
            )
        }

        compose.onNodeWithText("Saturday").assertIsDisplayed()
        compose.onNodeWithText("07:00").assertIsDisplayed()
    }

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
                mainContent = { Text("Screen Budget") },
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
                mainContent = { Text("Screen Budget") },
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
        compose.onNodeWithText("Choose your notifications").assertIsDisplayed()
        compose.onNodeWithText("• One daily remaining budget notification at your chosen time")
            .assertIsDisplayed()
        compose.onNodeWithText("• One weekly summary with a recommended goal").assertIsDisplayed()
        compose.onNodeWithText("• A prompt when a goal is needed").assertIsDisplayed()
        compose.onNodeWithText("• An alert when usage access is needed").assertIsDisplayed()
        compose.onNodeWithText("Screen Budget").assertDoesNotExist()
    }

    @Test
    fun nextLaunchWithCompletedSettingsOpensHome() {
        compose.setContent {
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { true },
                launchSettings = {},
                settings = AppSettings(onboardingDone = true),
                mainContent = { Text("Screen Budget") },
            )
        }

        compose.onNodeWithText("See your screen time").assertDoesNotExist()
        compose.onNodeWithText("Choose your notifications").assertDoesNotExist()
        compose.onNodeWithText("Choose your schedule").assertDoesNotExist()
        compose.onNodeWithText("Screen Budget").assertIsDisplayed()
    }

    @Test
    fun api33TurnOnNotificationsCreatesFourChannelsAndRequestsPermission() {
        var requestedPermission: String? = null
        var declined: Boolean? = null
        lateinit var notificationManager: NotificationManager
        compose.setContent {
            val context = LocalContext.current
            notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { true },
                launchSettings = {},
                createNotificationChannels = { NotificationChannels.create(context) },
                requestNotificationPermission = { requestedPermission = it },
                recordNotificationChoice = { declined = it },
                mainContent = { Text("Screen Budget") },
            )
        }

        compose.onNodeWithText("Turn on notifications").performClick()

        compose.runOnIdle {
            assertTrue(Build.VERSION.SDK_INT >= 33)
            assertEquals(Manifest.permission.POST_NOTIFICATIONS, requestedPermission)
            assertEquals(false, declined)
            assertChannels(notificationManager)
        }
        compose.onNodeWithText("Choose your schedule").assertIsDisplayed()
        compose.onNodeWithText("Screen Budget").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [32])
    fun api26To32TurnOnNotificationsOnlyCreatesChannelsAndContinues() {
        var requestedPermission: String? = null
        var completed = false
        lateinit var notificationManager: NotificationManager
        compose.setContent {
            val context = LocalContext.current
            notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { true },
                launchSettings = {},
                createNotificationChannels = { NotificationChannels.create(context) },
                requestNotificationPermission = { requestedPermission = it },
                recordNotificationChoice = { completed = true },
                mainContent = { Text("Screen Budget") },
            )
        }

        compose.onNodeWithText("Turn on notifications").performClick()

        compose.runOnIdle {
            assertEquals(null, requestedPermission)
            assertTrue(completed)
            assertChannels(notificationManager)
        }
        compose.onNodeWithText("Choose your schedule").assertIsDisplayed()
        compose.onNodeWithText("Screen Budget").assertDoesNotExist()
    }

    @Test
    fun notNowCreatesFourChannelsContinuesAndRecordsDecline() {
        var declined: Boolean? = null
        var finishedWeekStart: DayOfWeek? = null
        var finishedNotificationTime: LocalTime? = null
        lateinit var notificationManager: NotificationManager
        compose.setContent {
            val context = LocalContext.current
            notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ScreenBudgetApp(
                usageEventsSource = fakeUsageEventsSource { true },
                launchSettings = {},
                createNotificationChannels = { NotificationChannels.create(context) },
                recordNotificationChoice = { declined = it },
                finishOnboarding = { weekStart, notificationTime ->
                    finishedWeekStart = weekStart
                    finishedNotificationTime = notificationTime
                },
                mainContent = { Text("Screen Budget") },
            )
        }

        compose.onNodeWithText("Not now").performClick()

        compose.runOnIdle {
            assertEquals(true, declined)
            assertChannels(notificationManager)
        }
        compose.onNodeWithText("Choose your schedule").assertIsDisplayed()
        compose.onNodeWithText("Finish").performClick()
        compose.runOnIdle {
            assertEquals(DayOfWeek.SATURDAY, finishedWeekStart)
            assertEquals(LocalTime.of(7, 0), finishedNotificationTime)
        }
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

    private fun assertChannels(notificationManager: NotificationManager) {
        assertEquals(
            setOf(
                NotificationChannels.DAILY_BUDGET,
                NotificationChannels.WEEKLY_SUMMARY,
                NotificationChannels.GOAL_NEEDED,
                NotificationChannels.USAGE_ACCESS_NEEDED,
            ),
            notificationManager.notificationChannels.map { it.id }.toSet(),
        )
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }
}
