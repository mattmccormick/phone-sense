package ca.mattmccormick.screenbudget

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
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.UsageDatabase
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
                    mapOf("ca.mattmccormick.screenbudget" to 90 * 60_000L, "reader" to 30 * 60_000L),
                ))
            }
            compose.setContent {
                ScreenBudgetTheme {
                    AppNavigationShell(
                        initialDestination = AppDestination.DAY_DETAIL,
                        home = { onSetGoal -> HomeRoute(database.usageDao(), database.goalDao(), database.appRuleDao(),
                            settings, onSetGoal, today = { today }, readCurrentDay = read) },
                        dayDetail = { DayDetailScreen(database.usageDao(), database.appRuleDao(),
                            AppInfoSource { AppInfo(if (it == "ca.mattmccormick.screenbudget") "Screen Budget" else it, null) },
                            today = today, readCurrentDay = read, loadDispatcher = Dispatchers.Unconfined) },
                        goals = {},
                    )
                }
            }
            compose.onNodeWithContentDescription("Next day").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("120 min").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Total usage · all apps").assertIsDisplayed()
            compose.onNodeWithText("30 min counts toward your allowance").assertIsDisplayed()
            compose.onNodeWithContentDescription("Exclude Screen Budget").assertIsOn().performClick()
            compose.waitUntil(5_000) { "ca.mattmccormick.screenbudget" !in database.appRuleDao().excludedKeys() }
            compose.onNodeWithText("120 min").assertIsDisplayed()
            compose.onNodeWithText("120 min counts toward your allowance").assertIsDisplayed()
            compose.onNodeWithText("Home").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("120").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("120 minutes counted today").assertIsDisplayed()
            compose.onNodeWithText("Day").performClick()
            compose.onNodeWithContentDescription("Next day").performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("120 min").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("Exclude Screen Budget").assertIsOff()
        } finally { database.close() }
    }
}
