package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.compose.material3.Text
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.GoalDao
import ca.mattmccormick.screenbudget.data.UsageDatabase
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
class GoalsScreenTest {
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
    fun showsStoredGoalsAndWritesCurrentWeekUsingSettings() {
        goalDao.insert(Goal(LocalDate.of(2026, 9, 26), 45))
        goalDao.insert(Goal(LocalDate.of(2026, 9, 19), 50))
        compose.setContent {
            AppNavigationShell(
                initialDestination = AppDestination.GOALS,
                dayDetail = { Text("Day detail screen") },
                goals = {
                    GoalsRoute(
                        goalDao = goalDao,
                        settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                        today = LocalDate.of(2026, 10, 1),
                    )
                },
            )
        }

        compose.onNodeWithText("2026-09-26: 45 minutes").assertIsDisplayed()
        compose.onNodeWithText("2026-09-19: 50 minutes").assertIsDisplayed()
        compose.onNodeWithText("Daily minutes").performTextInput("35")
        compose.onNodeWithText("Set goal").performClick()

        compose.waitUntil {
            goalDao.forWeek(LocalDate.of(2026, 9, 28)) != null
        }
        assertEquals(
            Goal(LocalDate.of(2026, 9, 28), 35),
            goalDao.forWeek(LocalDate.of(2026, 9, 28)),
        )
        compose.onNodeWithText("Day").performClick()
        compose.onNodeWithText("Goals").performClick()
        compose.onNodeWithText("2026-09-28: 35 minutes").assertIsDisplayed()
    }

    @Test
    fun blankMinutesAreRefused() {
        var writtenGoal: Goal? = null
        compose.setContent {
            GoalsScreen(emptyList(), Settings(), { writtenGoal = it })
        }

        compose.onNodeWithText("Set goal").performClick()

        compose.onNodeWithText("Enter a positive number of minutes").assertIsDisplayed()
        compose.runOnIdle { assertNull(writtenGoal) }
    }

    @Test
    fun zeroMinutesAreRefused() {
        var writtenGoal: Goal? = null
        compose.setContent {
            GoalsScreen(emptyList(), Settings(), { writtenGoal = it })
        }

        compose.onNodeWithText("Daily minutes").performTextInput("0")
        compose.onNodeWithText("Set goal").performClick()

        compose.onNodeWithText("Enter a positive number of minutes").assertIsDisplayed()
        compose.runOnIdle { assertNull(writtenGoal) }
    }
}
