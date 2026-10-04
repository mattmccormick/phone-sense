package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: UsageDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun goalShowsTodaysDisplayBudgetAndWeeklyProgress() {
        compose.setContent {
            HomeScreen(
                goal = Goal(LocalDate.of(2026, 9, 28), 45),
                usedSoFar = 101,
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = LocalDate.of(2026, 9, 30),
                onSetGoal = {},
            )
        }

        compose.onNodeWithText("Today's remaining budget").assertIsDisplayed()
        compose.onNodeWithText("00:42").assertIsDisplayed()
        compose.onNodeWithText("Goal 00:45").assertIsDisplayed()
        compose.onNodeWithText("101 minutes used this week").assertIsDisplayed()
        compose.onNodeWithText("Day 3 of 7").assertIsDisplayed()
        compose.onNodeWithContentDescription("Six-week distraction chart").assertIsDisplayed()
    }

    @Test
    fun missingGoalButtonOpensGoals() {
        compose.setContent {
            AppNavigationShell(
                home = { openGoals ->
                    HomeScreen(
                        goal = null,
                        usedSoFar = 0,
                        settings = Settings(),
                        onSetGoal = openGoals,
                    )
                },
                dayDetail = { Text("Day detail screen") },
                goals = { Text("Goals screen") },
            )
        }

        compose.onNodeWithText("Set a goal").performClick()

        compose.onNodeWithText("Goals screen").assertIsDisplayed()
    }

    @Test
    fun weekProgressUsesTheConfiguredWeekStartDay() {
        val monday = LocalDate.of(2026, 9, 28)
        database.goalDao().insert(Goal(monday, 45))
        database.usageDao().insert(
            DailyUsage(monday, 30, Source.COLLECTED, Instant.EPOCH),
            emptyList(),
        )
        database.usageDao().insert(
            DailyUsage(monday.plusDays(1), 20, Source.COLLECTED, Instant.EPOCH),
            emptyList(),
        )
        database.usageDao().insert(
            DailyUsage(monday.minusDays(1), 200, Source.COLLECTED, Instant.EPOCH),
            emptyList(),
        )

        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = LocalDate.of(2026, 10, 1),
                onSetGoal = {},
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Goal 00:45").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Goal 00:45").assertIsDisplayed()
        compose.onNodeWithText("50 minutes used this week").assertIsDisplayed()
        compose.onNodeWithText("Day 4 of 7").assertIsDisplayed()
    }
}
