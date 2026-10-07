package ca.mattmccormick.phone_sense

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Settings
import ca.mattmccormick.phone_sense.data.UsageDatabase
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageExclusionsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun formerlyHiddenAppCanCountTowardAllowanceWithoutChangingTotal() {
        val database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UsageDatabase::class.java)
            .addCallback(UsageDatabase.DEFAULT_APP_RULES).allowMainThreadQueries().build()
        try {
            val today = LocalDate.of(2026, 10, 5)
            val settings = Settings(weekStartDay = DayOfWeek.MONDAY)
            database.goalDao().insert(Goal(today, 60))
            val read: (ZoneId) -> CurrentDayUsageSnapshotResult = {
                CurrentDayUsageSnapshotResult.Available(CurrentDayUsageSnapshot(
                    today, Instant.EPOCH, 120 * 60_000L,
                    mapOf("ca.mattmccormick.phone_sense" to 90 * 60_000L, "reader" to 30 * 60_000L),
                ))
            }
            lateinit var back: OnBackPressedDispatcher
            compose.setContent {
                back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
                ScreenBudgetTheme {
                    HomeWithSettings(
                        settings = settings,
                        appSettings = { back -> AppAllowanceSettings(database.usageDao(), database.appRuleDao(),
                            AppInfoSource { AppInfo(if (it == "ca.mattmccormick.phone_sense") "Phone Sense" else it, null) },
                            onBack = back, readCurrentDay = read) },
                    ) {
                    AppNavigationShell(
                        initialDestination = AppDestination.DAY_DETAIL,
                        home = { HomeRoute(database.usageDao(), database.goalDao(), database.appRuleDao(),
                            settings, today = { today }, readCurrentDay = read) },
                        dayDetail = { DayDetailScreen(database.usageDao(), database.appRuleDao(),
                            AppInfoSource { AppInfo(if (it == "ca.mattmccormick.phone_sense") "Phone Sense" else it, null) },
                            today = today, readCurrentDay = read, loadDispatcher = Dispatchers.Unconfined) },
                    )
                    }
                }
            }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("120 min").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Total usage · all apps").assertIsDisplayed()
            compose.onNodeWithContentDescription("Counted toward allowance: 30 minutes").assertIsDisplayed()
            compose.onNodeWithContentDescription("Phone Sense: excluded from allowance").assertIsDisplayed()
            compose.onNodeWithText("Settings").performClick()
            compose.onNodeWithText("Apps counted").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Phone Sense").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Count reader toward allowance").assertIsOn()
            compose.onNodeWithContentDescription("Count Phone Sense toward allowance").assertIsOff().performClick()
            compose.waitUntil(5_000) { "ca.mattmccormick.phone_sense" !in database.appRuleDao().excludedKeys() }
            compose.runOnIdle { back.onBackPressed() }
            compose.runOnIdle { back.onBackPressed() }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("120 min").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Total usage: 120 minutes").assertIsDisplayed()
            compose.onNodeWithContentDescription("Counted toward allowance: 120 minutes").assertIsDisplayed()
            compose.onNodeWithText("Home").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("120").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("120 minutes counted today").assertIsDisplayed()
            compose.onNodeWithText("Day").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("120 min").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Phone Sense: included in allowance").assertIsDisplayed()
        } finally { database.close() }
    }
}
