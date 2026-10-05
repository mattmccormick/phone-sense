package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.screenbudget.data.AppRule
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Goal
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDatabase
import ca.mattmccormick.screenbudget.export.ExportAppRule
import ca.mattmccormick.screenbudget.export.ExportAppUsage
import ca.mattmccormick.screenbudget.export.ExportDailyUsage
import ca.mattmccormick.screenbudget.export.ExportDocument
import ca.mattmccormick.screenbudget.export.ExportGoal
import ca.mattmccormick.screenbudget.export.ImportService
import ca.mattmccormick.screenbudget.export.encodeExport
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
    fun goalShowsFixedAllowanceAndTodaysProgress() {
        compose.setContent {
            HomeScreen(
                goal = Goal(LocalDate.of(2026, 9, 28), 45),
                usedSoFar = 101,
                chart = chartModel(
                    listOf(DailyUsage(LocalDate.of(2026, 9, 30), 10, Source.COLLECTED, Instant.EPOCH)),
                    listOf(Goal(LocalDate.of(2026, 9, 28), 45)), DayOfWeek.MONDAY, LocalDate.of(2026, 9, 30),
                ),
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = LocalDate.of(2026, 9, 30),
                onSetGoal = {},
            )
        }

        compose.onNodeWithText("This week’s goal").assertIsDisplayed()
        compose.onNodeWithText("45").assertIsDisplayed()
        compose.onNodeWithText("44").assertIsDisplayed()
        compose.onNodeWithContentDescription("10 minutes counted today").assertIsDisplayed()
        compose.onNodeWithText("34 min left today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Six-week distraction chart").assertExists()
    }

    @Test
    fun missingGoalShowsInlineInput() {
        compose.setContent {
            HomeScreen(goal = null, usedSoFar = 0, settings = Settings(), onSetGoal = {})
        }
        compose.onNodeWithText("Daily average (minutes)").assertIsDisplayed()
        compose.onNodeWithText("Set goal").assertIsDisplayed()
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
                today = { LocalDate.of(2026, 10, 1) },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Daily allowance").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Daily allowance").assertIsDisplayed()
        compose.onNodeWithText("Usage unavailable").assertIsDisplayed()
        compose.onAllNodesWithText("45")[1].assertIsDisplayed()
    }

    @Test
    fun successfulImportRefreshesAnAlreadyLoadedHomeScreen() {
        val today = LocalDate.of(2026, 10, 1)
        val weekStart = LocalDate.of(2026, 9, 28)
        val refreshKey = mutableIntStateOf(0)
        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = { today },
                refreshKey = refreshKey.intValue,
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Set goal").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Set goal").assertIsDisplayed()
        compose.onNodeWithText("No usage data yet").assertExists()

        ImportService(database).importJson(
            encodeExport(
                ExportDocument(
                    exportedAt = "2026-10-03T18:30:00Z",
                    weekStartDay = "MONDAY",
                    dailyUsage = listOf(ExportDailyUsage(today.toString(), 320, "COLLECTED")),
                    appUsage = listOf(
                        ExportAppUsage(today.toString(), "com.example.reader", 300),
                        ExportAppUsage(today.toString(), "com.example.system", 20),
                    ),
                    appRules = listOf(
                        ExportAppRule("com.example.system", "System", excluded = true),
                    ),
                    goals = listOf(ExportGoal(weekStart.toString(), 60)),
                ),
            ),
        )
        compose.runOnIdle { refreshKey.intValue++ }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("300")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Daily allowance").assertIsDisplayed()
        compose.onNodeWithText("240 min over today").assertIsDisplayed()
        compose.onNodeWithText("No usage data yet").assertDoesNotExist()
        compose.onNodeWithContentDescription("Six-week distraction chart").assertExists()
    }

    @Test
    fun currentDaySnapshotReplacesImportedTodayAndAppliesExclusions() {
        val today = LocalDate.of(2026, 10, 1)
        val monday = LocalDate.of(2026, 9, 28)
        database.goalDao().insert(Goal(monday, 120))
        database.usageDao().insert(
            DailyUsage(monday, 30, Source.COLLECTED, Instant.EPOCH),
            emptyList(),
        )
        database.usageDao().insert(
            DailyUsage(today, 500, Source.IMPORTED, Instant.EPOCH),
            emptyList(),
        )
        database.appRuleDao().insert(AppRule("com.example.excluded", "Excluded", true))

        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = { today },
                readCurrentDay = {
                    snapshot(today, totalMinutes = 120, excludedMinutes = 30)
                },
            )
        }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("90")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("90").assertIsDisplayed()
        compose.onNodeWithText("No usage data yet").assertDoesNotExist()
    }

    @Test
    fun returningToForegroundRefreshesCurrentDayUsage() {
        val today = LocalDate.of(2026, 10, 1)
        val lifecycleOwner = FakeLifecycleOwner()
        var currentMinutes = 30
        database.goalDao().insert(Goal(LocalDate.of(2026, 9, 28), 60))

        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = { today },
                readCurrentDay = { snapshot(today, currentMinutes) },
                lifecycleOwner = lifecycleOwner,
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("30")
                .fetchSemanticsNodes().isNotEmpty()
        }

        currentMinutes = 50
        compose.runOnIdle { lifecycleOwner.resume() }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("50")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun unavailableRefreshAfterMidnightDoesNotReuseYesterdaysSnapshot() {
        val monday = LocalDate.of(2026, 10, 5)
        var currentDate = monday
        var available = true
        val refreshKey = mutableIntStateOf(0)
        database.goalDao().insert(Goal(monday, 60))

        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = { currentDate },
                readCurrentDay = {
                    if (available) snapshot(monday, 40)
                    else CurrentDayUsageSnapshotResult.Unavailable
                },
                refreshKey = refreshKey.intValue,
            )
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("40")
                .fetchSemanticsNodes().isNotEmpty()
        }

        currentDate = monday.plusDays(1)
        available = false
        compose.runOnIdle { refreshKey.intValue++ }

        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Usage unavailable")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("40").assertDoesNotExist()
    }

    private fun snapshot(
        date: LocalDate,
        totalMinutes: Int,
        excludedMinutes: Int = 0,
    ): CurrentDayUsageSnapshotResult.Available = CurrentDayUsageSnapshotResult.Available(
        CurrentDayUsageSnapshot(
            date = date,
            capturedAt = date.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            totalMillis = totalMinutes * 60_000L,
            perPackageMillis = buildMap {
                put("com.example.used", (totalMinutes - excludedMinutes) * 60_000L)
                if (excludedMinutes > 0) {
                    put("com.example.excluded", excludedMinutes * 60_000L)
                }
            },
        ),
    )

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }
}
