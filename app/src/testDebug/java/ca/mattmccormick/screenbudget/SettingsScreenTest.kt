package ca.mattmccormick.screenbudget

import android.content.Intent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.export.ImportResult
import ca.mattmccormick.screenbudget.export.ImportStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var back: OnBackPressedDispatcher

    @Test
    fun completedOnboardingKeepsSettingsAndNavigationUsable() {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            HomeWithSettings(
                settings = Settings(onboardingDone = true),
                mainContent = {
                    AppNavigationShell(
                        home = { Text("Home screen") },
                        dayDetail = { Text("Day detail screen") },
                    )
                },
            )
        }

        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Day").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed().performClick()
        compose.onNodeWithText("Home screen").assertIsDisplayed()

        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Day detail screen").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Data").performClick()
        compose.onNodeWithText("Export JSON").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("About").assertIsDisplayed()
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("Day detail screen").assertIsDisplayed()
        compose.onNodeWithText("Goals").assertDoesNotExist()
    }

    @Test
    fun exportJsonLaunchesDocumentPickerWithDatedName() {
        var launchedIntent: Intent? = null
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DataSettingsScreen(
                launchExport = { launchedIntent = it },
                today = { LocalDate.of(2026, 10, 3) },
            )
        }

        compose.onNodeWithText("Export JSON").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, launchedIntent?.action)
            assertEquals("application/json", launchedIntent?.type)
            assertEquals("phone-sense-2026-10-03.json", launchedIntent?.getStringExtra(Intent.EXTRA_TITLE))
        }
    }

    @Test
    fun exportCsvLaunchesDocumentPickerWithDatedName() {
        var launchedIntent: Intent? = null
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DataSettingsScreen(
                launchExport = { launchedIntent = it },
                today = { LocalDate.of(2026, 10, 3) },
            )
        }

        compose.onNodeWithText("Export CSV").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, launchedIntent?.action)
            assertEquals("text/csv", launchedIntent?.type)
            assertEquals("phone-sense-2026-10-03.csv", launchedIntent?.getStringExtra(Intent.EXTRA_TITLE))
        }
    }

    @Test
    fun hiddenOptionsPreserveCurrentSettings() {
        val original = Settings(weekStartDay = DayOfWeek.MONDAY, notificationTime = LocalTime.of(18, 45), reductionPercent = 25)
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            HomeWithSettings(settings = original) { current ->
                Text("${current.weekStartDay} ${current.notificationTime} ${current.reductionPercent}")
            }
        }
        compose.onNodeWithText("Settings").performClick()
        listOf("Week starts on", "Notification time", "Recommendation reduction", "Export JSON", "Import JSON", "Back")
            .forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        compose.runOnIdle { back.onBackPressed() }
        compose.onNodeWithText("MONDAY 18:45 25").assertIsDisplayed()
    }

    @Test
    fun collectNowRunsCollectorAndShowsResult() {
        val collections = AtomicInteger()
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DataSettingsScreen(
                collectNow = {
                    collections.incrementAndGet()
                    CollectResult.Collected(
                        listOf(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1)),
                    )
                },
            )
        }

        compose.onNodeWithText("Collect now").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { collections.get() == 1 }
        compose.onNodeWithText(
            "Collected 2 days through 2026-10-01",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun notificationBannerAppearsOnlyWhenPermissionIsMissing() {
        var notificationsEnabled by mutableStateOf(false)
        var settingsOpened = false
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            SettingsScreen(
                hasUsageAccess = true,
                notificationsEnabled = notificationsEnabled,
                openNotificationSettings = { settingsOpened = true },
            )
        }

        compose.onNodeWithText("Off · turn on for goal reminders")
            .assertExists()
        compose.onNodeWithText("Notifications").performClick()
        compose.runOnIdle {
            assertEquals(true, settingsOpened)
            notificationsEnabled = true
        }

        compose.onNodeWithText("Off · turn on for goal reminders")
            .assertDoesNotExist()
    }

    @Test
    fun importResultDoesNotOfferWeekStartChanges() {
        compose.setContent {
            DataSettingsScreen(importStatus = ImportStatus.Success(ImportResult(3, 1, DayOfWeek.MONDAY)))
        }
        compose.onNodeWithText("Imported 3 days; skipped 1 collected day").assertExists()
        compose.onNodeWithText("Use Monday as week start").assertDoesNotExist()
    }

    @Test
    fun importErrorIsShown() {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            DataSettingsScreen(
                importStatus = ImportStatus.Error("Expected a JSON object"),
            )
        }

        compose.onNodeWithText("Import failed: Expected a JSON object").assertExists()
    }
}
