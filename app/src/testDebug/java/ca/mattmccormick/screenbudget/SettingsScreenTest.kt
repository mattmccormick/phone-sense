package ca.mattmccormick.screenbudget

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    @Test
    fun exportJsonLaunchesDocumentPickerWithDatedName() {
        var launchedIntent: Intent? = null
        compose.setContent {
            SettingsScreen(
                settings = Settings(),
                hasUsageAccess = true,
                notificationsEnabled = true,
                launchExport = { launchedIntent = it },
                today = { LocalDate.of(2026, 10, 3) },
            )
        }

        compose.onNodeWithText("Export JSON").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, launchedIntent?.action)
            assertEquals("application/json", launchedIntent?.type)
            assertEquals("screen-budget-2026-10-03.json", launchedIntent?.getStringExtra(Intent.EXTRA_TITLE))
        }
    }

    @Test
    fun exportCsvLaunchesDocumentPickerWithDatedName() {
        var launchedIntent: Intent? = null
        compose.setContent {
            SettingsScreen(
                settings = Settings(),
                hasUsageAccess = true,
                notificationsEnabled = true,
                launchExport = { launchedIntent = it },
                today = { LocalDate.of(2026, 10, 3) },
            )
        }

        compose.onNodeWithText("Export CSV").performScrollTo().performClick()

        compose.runOnIdle {
            assertEquals(Intent.ACTION_CREATE_DOCUMENT, launchedIntent?.action)
            assertEquals("text/csv", launchedIntent?.type)
            assertEquals("screen-budget-2026-10-03.csv", launchedIntent?.getStringExtra(Intent.EXTRA_TITLE))
        }
    }

    @Test
    fun changingNotificationTimeUsesChosenValue() {
        var changedTime: LocalTime? = null
        compose.setContent {
            SettingsScreen(
                settings = Settings(notificationTime = LocalTime.of(7, 0)),
                hasUsageAccess = true,
                notificationsEnabled = true,
                onNotificationTimeChange = {
                    changedTime = it
                },
                showTimePicker = { _, onTimeChosen ->
                    onTimeChosen(LocalTime.of(18, 45))
                },
            )
        }

        compose.onNodeWithText("07:00").performClick()

        compose.runOnIdle {
            assertEquals(LocalTime.of(18, 45), changedTime)
        }
        compose.onNodeWithText("18:45").assertExists()
    }

    @Test
    fun changingWeekStartDayIsReflectedOnHome() {
        compose.setContent {
            var settings by remember { mutableStateOf(Settings(onboardingDone = true)) }
            HomeWithSettings(
                settings = settings,
                onWeekStartDayChange = { settings = settings.copy(weekStartDay = it) },
                mainContent = { currentSettings ->
                    Text(
                        "Week of ${currentWeekStart(
                            LocalDate.of(2026, 10, 1),
                            currentSettings.weekStartDay,
                        )}",
                    )
                },
            )
        }

        compose.onNodeWithText("Week of 2026-09-26").assertExists()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Saturday").performClick()
        compose.onNodeWithText("Monday").performClick()
        compose.onNodeWithText("Back").performClick()

        compose.onNodeWithText("Week of 2026-09-28").assertExists()
    }

    @Test
    fun collectNowRunsCollectorAndShowsResult() {
        val collections = AtomicInteger()
        compose.setContent {
            SettingsScreen(
                settings = Settings(),
                hasUsageAccess = true,
                notificationsEnabled = true,
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
            SettingsScreen(
                settings = Settings(),
                hasUsageAccess = true,
                notificationsEnabled = notificationsEnabled,
                openNotificationSettings = { settingsOpened = true },
            )
        }

        compose.onNodeWithText("Notifications are off. Turn them on to get goal reminders.")
            .assertExists()
        compose.onNodeWithText("Open notification settings").performClick()
        compose.runOnIdle {
            assertEquals(true, settingsOpened)
            notificationsEnabled = true
        }

        compose.onNodeWithText("Notifications are off. Turn them on to get goal reminders.")
            .assertDoesNotExist()
    }

    @Test
    fun importResultOffersWeekStartWithoutApplyingIt() {
        var appliedDay: DayOfWeek? = null
        compose.setContent {
            SettingsScreen(
                settings = Settings(weekStartDay = DayOfWeek.SATURDAY),
                hasUsageAccess = true,
                notificationsEnabled = true,
                importStatus = ImportStatus.Success(ImportResult(3, 1, DayOfWeek.MONDAY)),
                onWeekStartDayChange = { appliedDay = it },
            )
        }

        compose.onNodeWithText("Imported 3 days; skipped 1 collected day").assertExists()
        compose.onNodeWithText("File week starts on Monday").assertExists()
        compose.runOnIdle { assertEquals(null, appliedDay) }

        compose.onNodeWithText("Use Monday as week start").performScrollTo().performClick()

        compose.runOnIdle { assertEquals(DayOfWeek.MONDAY, appliedDay) }
    }

    @Test
    fun importErrorIsShown() {
        compose.setContent {
            SettingsScreen(
                settings = Settings(),
                hasUsageAccess = true,
                notificationsEnabled = true,
                importStatus = ImportStatus.Error("Expected a JSON object"),
            )
        }

        compose.onNodeWithText("Import failed: Expected a JSON object").assertExists()
    }
}
