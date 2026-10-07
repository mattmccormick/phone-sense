package ca.mattmccormick.phone_sense

import android.content.Context
import androidx.compose.material3.Text
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Settings
import ca.mattmccormick.phone_sense.data.GoalDao
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeeklyGoalEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: UsageDatabase
    private lateinit var goalDao: GoalDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
        goalDao = database.goalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun newWeekPromptsOnHomeAndSavingPreservesPreviousGoal() {
        val previousWeek = LocalDate.of(2026, 9, 28)
        val newWeek = previousWeek.plusWeeks(1)
        goalDao.insert(Goal(previousWeek, 45))
        var today = newWeek.minusDays(1)
        val refresh = androidx.compose.runtime.mutableIntStateOf(0)
        compose.setContent {
            AppNavigationShell(
                home = {
                    HomeRoute(database.usageDao(), goalDao, database.appRuleDao(),
                        Settings(weekStartDay = DayOfWeek.MONDAY),
                        today = { today }, refreshKey = refresh.intValue)
                },
                dayDetail = { Text("Day detail screen") },
            )
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Daily allowance").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Daily average (minutes)").assertDoesNotExist()
        compose.onNodeWithText("Goals").assertDoesNotExist()
        compose.runOnIdle { today = newWeek; refresh.intValue++ }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Set goal").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Daily average (minutes)").performTextInput("35")
        compose.onNodeWithText("Set goal").performClick()
        compose.waitUntil(5_000) { goalDao.forWeek(newWeek) != null }
        assertEquals(Goal(newWeek, 35), goalDao.forWeek(newWeek))
        assertEquals(Goal(previousWeek, 45), goalDao.forWeek(previousWeek))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Daily allowance").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Set goal").assertDoesNotExist()
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Home").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Daily allowance").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Set goal").assertDoesNotExist()
    }

    @Test
    fun blankMinutesAreRefused() {
        var writtenGoal: Goal? = null
        compose.setContent {
            WeeklyGoalEntry(LocalDate.of(2026, 10, 5), { writtenGoal = it })
        }

        compose.onNodeWithText("Set goal").performClick()

        compose.onNodeWithText("Enter a positive number of minutes").assertIsDisplayed()
        compose.runOnIdle { assertNull(writtenGoal) }
    }

    @Test
    fun zeroMinutesAreRefused() {
        var writtenGoal: Goal? = null
        compose.setContent {
            WeeklyGoalEntry(LocalDate.of(2026, 10, 5), { writtenGoal = it })
        }

        compose.onNodeWithText("Daily average (minutes)").performTextInput("0")
        compose.onNodeWithText("Set goal").performClick()

        compose.onNodeWithText("Enter a positive number of minutes").assertIsDisplayed()
        compose.runOnIdle { assertNull(writtenGoal) }
    }
}
