package ca.mattmccormick.phone_sense

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.phone_sense.data.AppRule
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Settings
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDatabase
import ca.mattmccormick.phone_sense.export.ExportAppRule
import ca.mattmccormick.phone_sense.export.ExportAppUsage
import ca.mattmccormick.phone_sense.export.ExportDailyUsage
import ca.mattmccormick.phone_sense.export.ExportDocument
import ca.mattmccormick.phone_sense.export.ExportGoal
import ca.mattmccormick.phone_sense.export.ImportService
import ca.mattmccormick.phone_sense.export.encodeExport
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The default 320x470dp window clips the trend header off the bottom, where the pager lives.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: UsageDatabase

    private fun insertDay(date: LocalDate, minutes: Int) = database.usageDao().insert(
        DailyUsage(date, minutes, Source.COLLECTED, Instant.EPOCH),
        emptyList(),
    )

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

    // Sunday weeks put page 1 of this Tuesday on Sep 6 to Oct 17.
    private fun pagedHome(today: LocalDate, refreshKey: MutableIntState = mutableIntStateOf(0)) {
        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.SUNDAY),
                today = { today },
                refreshKey = refreshKey.intValue,
            )
        }
        awaitText("Last 6 weeks")
    }

    private fun chart() = compose.onNodeWithContentDescription("Six-week distraction chart")

    private fun trendTop() = compose.onNodeWithText("Your trend").fetchSemanticsNode().boundsInWindow.top

    private fun awaitText(text: String) = compose.waitUntil(timeoutMillis = 5_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun awaitChartState(text: String) = compose.waitUntil(timeoutMillis = 5_000) {
        compose.onAllNodesWithContentDescription("Six-week distraction chart")
            .fetchSemanticsNodes()
            .any { it.config[SemanticsProperties.StateDescription].contains(text) }
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun resume() {
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

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
                    listOf(Goal(LocalDate.of(2026, 9, 28), 45)), DayOfWeek.MONDAY,
                    LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 30),
                ),
                usedToday = 10,
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
        insertDay(monday, 30)
        insertDay(monday.plusDays(1), 20)
        insertDay(monday.minusDays(1), 200)

        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                today = { LocalDate.of(2026, 10, 1) },
            )
        }

        awaitText("Daily allowance")
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
        awaitText("Set goal")
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

        awaitText("300")
        compose.onNodeWithText("Daily allowance").assertIsDisplayed()
        compose.onNodeWithText("240 min over today").assertIsDisplayed()
        awaitChartState("$today: 300 minutes")
        compose.onNodeWithText("No usage data yet").assertDoesNotExist()
    }

    @Test
    fun currentDaySnapshotReplacesImportedTodayAndAppliesExclusions() {
        val today = LocalDate.of(2026, 10, 1)
        val monday = LocalDate.of(2026, 9, 28)
        database.goalDao().insert(Goal(monday, 120))
        insertDay(monday, 30)
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

        awaitText("90")
        compose.onNodeWithText("90").assertIsDisplayed()
        compose.onNodeWithText("No usage data yet").assertDoesNotExist()
    }

    @Test
    fun firstCompositionReadsTheCurrentDayOnce() {
        val today = LocalDate.of(2026, 10, 20)
        val reads = AtomicInteger()
        insertDay(today, 10)

        compose.setContent {
            HomeRoute(
                usageDao = database.usageDao(),
                goalDao = database.goalDao(),
                appRuleDao = database.appRuleDao(),
                settings = Settings(weekStartDay = DayOfWeek.SUNDAY),
                today = { today },
                readCurrentDay = {
                    reads.incrementAndGet()
                    snapshot(today, 10)
                },
            )
        }
        awaitText("Last 6 weeks")

        assertEquals("A resume replayed to a new observer must not load again", 1, reads.get())
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
        awaitText("30")

        currentMinutes = 50
        compose.runOnIdle { lifecycleOwner.resume() }

        awaitText("50")
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
        awaitText("40")

        currentDate = monday.plusDays(1)
        available = false
        compose.runOnIdle { refreshKey.intValue++ }

        awaitText("Usage unavailable")
        compose.onNodeWithText("40").assertDoesNotExist()
    }

    @Test
    fun theArrowsMoveTheChartAWeekAtATime() {
        val today = LocalDate.of(2026, 10, 20)
        insertDay(today, 10)
        insertDay(LocalDate.of(2026, 7, 1), 20)

        pagedHome(today)
        compose.onNodeWithContentDescription("Later week").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Earlier week").performClick()

        awaitText("Sep 6 to Oct 17")
        compose.onNodeWithContentDescription("Later week").assertIsEnabled()
        compose.onNodeWithContentDescription("Earlier week").performClick()

        awaitText("Aug 30 to Oct 10")
        compose.onNodeWithContentDescription("Later week").performClick()

        awaitText("Sep 6 to Oct 17")
    }

    @Test
    fun theEarlierArrowStopsAtTheOldestDay() {
        val today = LocalDate.of(2026, 10, 20)
        insertDay(today, 10)
        insertDay(LocalDate.of(2026, 9, 7), 20)

        pagedHome(today)
        compose.onNodeWithContentDescription("Earlier week").assertIsEnabled()
        compose.onNodeWithContentDescription("Earlier week").performClick()

        awaitText("Sep 6 to Oct 17")
        compose.onNodeWithContentDescription("Earlier week").assertIsNotEnabled()
    }

    @Test
    fun backToThisWeekReturnsFromAPastWindow() {
        val today = LocalDate.of(2026, 10, 20)
        insertDay(today, 10)
        insertDay(LocalDate.of(2026, 7, 1), 20)

        pagedHome(today)
        compose.onNodeWithText("Back to this week").assertDoesNotExist()
        compose.onNodeWithContentDescription("Earlier week").performClick()
        awaitText("Sep 6 to Oct 17")
        compose.onNodeWithContentDescription("Earlier week").performClick()
        awaitText("Aug 30 to Oct 10")

        compose.onNodeWithText("Back to this week").performClick()

        awaitText("Last 6 weeks")
        compose.onNodeWithText("Back to this week").assertDoesNotExist()
    }

    @Test
    fun aHorizontalSwipeOnTheChartPagesByOneWeek() {
        val today = LocalDate.of(2026, 10, 20)
        (0L..60L).forEach { insertDay(today.minusDays(it), 10) }

        pagedHome(today)
        chart().performTouchInput { swipeRight() }

        awaitText("Sep 6 to Oct 17")
        chart().performTouchInput { swipeLeft() }

        awaitText("Last 6 weeks")
    }

    @Test
    @Config(qualifiers = "w411dp-h700dp")
    fun aVerticalDragOnTheChartScrollsInsteadOfPaging() {
        val today = LocalDate.of(2026, 10, 20)
        (0L..60L).forEach { insertDay(today.minusDays(it), 10) }

        pagedHome(today)
        val top = trendTop()
        chart().performTouchInput { swipeUp() }
        compose.waitForIdle()

        assertTrue("A vertical drag must scroll the screen, top was $top", trendTop() < top)
        compose.onNodeWithText("Last 6 weeks").assertExists()
    }

    @Test
    fun aRefreshRedrawsThePastWindowItIsShowing() {
        val today = LocalDate.of(2026, 10, 20)
        val refreshKey = mutableIntStateOf(0)
        insertDay(today, 10)
        insertDay(LocalDate.of(2026, 7, 1), 20)
        insertDay(LocalDate.of(2026, 10, 1), 30)

        pagedHome(today, refreshKey)
        compose.onNodeWithContentDescription("Earlier week").performClick()
        awaitText("Sep 6 to Oct 17")

        insertDay(LocalDate.of(2026, 10, 2), 75)
        compose.runOnIdle { refreshKey.intValue++ }

        awaitChartState("2026-10-02: 75 minutes")
        compose.onNodeWithText("Sep 6 to Oct 17").assertIsDisplayed()
    }
}
