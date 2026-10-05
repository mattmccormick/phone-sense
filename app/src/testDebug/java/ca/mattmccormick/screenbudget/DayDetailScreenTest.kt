package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.screenbudget.data.AppUsage
import ca.mattmccormick.screenbudget.data.AppRule
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalTestApi::class)
class DayDetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var database: UsageDatabase
    private val today = LocalDate.of(2026, 10, 2)
    private val resolver = AppInfoSource { packageName ->
        AppInfo(
            label = mapOf(
                "com.example.reader" to "Reader",
                "com.example.video" to "Video",
            ).getValue(packageName),
            icon = null,
        )
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
    fun storedDayShowsTotalAndAppsByDescendingMinutes() {
        val date = today.minusDays(1)
        database.usageDao().insert(
            DailyUsage(date, 75, Source.COLLECTED, Instant.parse("2026-10-02T07:00:00Z")),
            listOf(
                AppUsage(date, "com.example.video", 30),
                AppUsage(date, "com.example.reader", 45),
            ),
        )

        setScreen()

        compose.waitUntilAtLeastOneExists(
            hasText("75 min"),
            timeoutMillis = 5_000,
        )
        compose.onNodeWithText("2026-10-01").assertIsDisplayed()
        compose.onNodeWithContentDescription("Total usage: 75 minutes").assertIsDisplayed()
        compose.onNodeWithText("Reader").assertIsDisplayed()
        compose.onNodeWithText("45 min").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reader icon").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Video"))
        compose.onNodeWithText("Video").assertIsDisplayed()
        compose.onNodeWithText("30 min").assertIsDisplayed()
    }

    @Test
    fun collectedDayCannotBeEditedByHand() {
        val date = today.minusDays(1)
        database.usageDao().insert(
            DailyUsage(date, 75, Source.COLLECTED, Instant.parse("2026-10-02T07:00:00Z")),
            emptyList(),
        )

        setScreen()

        compose.waitUntilAtLeastOneExists(hasText("75 min"), timeoutMillis = 5_000)
        compose.onNodeWithText("Enter by hand").assertDoesNotExist()
    }

    @Test
    fun missingDayShowsNotCollectedYet() {
        setScreen()

        compose.waitUntilAtLeastOneExists(
            hasText("Not collected yet"),
            timeoutMillis = 5_000,
        )
        compose.onNodeWithText("Not collected yet").assertIsDisplayed()
    }

    @Test
    fun missingDayCanBeEnteredByHand() {
        setScreen()

        compose.waitUntilAtLeastOneExists(
            hasText("Enter by hand"),
            timeoutMillis = 5_000,
        )
        compose.onNodeWithText("Enter by hand").assertIsDisplayed()
    }

    @Test
    fun savingManualEntryWritesTheDayAndItsApps() {
        val date = today.minusDays(1)
        setScreen()
        compose.waitUntilAtLeastOneExists(hasText("Enter by hand"), timeoutMillis = 5_000)

        compose.onNodeWithText("Enter by hand").performClick()
        compose.onNodeWithText("Total minutes").performTextInput("60")
        compose.onNodeWithText("App name").performTextInput("Reader")
        compose.onNodeWithText("App minutes").performTextInput("25")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        compose.waitUntil(timeoutMillis = 5_000) { database.usageDao().day(date) != null }
        val stored = database.usageDao().day(date)!!
        assertEquals(60, stored.day.totalMinutes)
        assertEquals(Source.MANUAL, stored.day.source)
        assertEquals(listOf(AppUsage(date, "label:Reader", 25)), stored.apps)
        compose.waitUntilAtLeastOneExists(hasText("Reader"), timeoutMillis = 5_000)
    }

    @Test
    fun appMinutesGreaterThanTheTotalAreRefused() {
        val date = today.minusDays(1)
        setScreen()
        compose.waitUntilAtLeastOneExists(hasText("Enter by hand"), timeoutMillis = 5_000)

        compose.onNodeWithText("Enter by hand").performClick()
        compose.onNodeWithText("Total minutes").performTextInput("30")
        compose.onNodeWithText("App name").performTextInput("Reader")
        compose.onNodeWithText("App minutes").performTextInput("31")
        compose.onNodeWithText("Save").performScrollTo().performClick()

        compose.onNodeWithText("App minutes cannot exceed total minutes").assertIsDisplayed()
        assertNull(database.usageDao().day(date))
    }

    @Test
    fun collectionCompletionRefreshesTheDisplayedDay() {
        val allowCollection = CountDownLatch(1)
        val date = today.minusDays(1)
        compose.setContent {
            MaterialTheme {
                ScreenBudgetApp(
                    usageEventsSource = UsageEventsSource(
                        eventsQuery = UsageEventsQuery { _, _ -> emptyList() },
                        usageAccessQuery = UsageAccessQuery { _, _, _ ->
                            android.app.AppOpsManager.MODE_ALLOWED
                        },
                        uid = 123,
                        packageName = "ca.mattmccormick.screenbudget",
                    ),
                    launchSettings = {},
                    settings = ca.mattmccormick.screenbudget.data.Settings(onboardingDone = true),
                    collectUsage = {
                        check(allowCollection.await(5, TimeUnit.SECONDS))
                        database.usageDao().insert(
                            DailyUsage(
                                date,
                                42,
                                Source.COLLECTED,
                                Instant.parse("2026-10-02T07:00:00Z"),
                            ),
                            emptyList(),
                        )
                    },
                    mainContent = { collectionVersion ->
                        DayDetailScreen(
                            dao = database.usageDao(),
                            appRuleDao = database.appRuleDao(),
                            appInfoSource = resolver,
                            today = today,
                            refreshKey = collectionVersion,
                            loadDispatcher = Dispatchers.Unconfined,
                        )
                    },
                )
            }
        }
        compose.waitUntilAtLeastOneExists(
            hasText("Not collected yet"),
            timeoutMillis = 5_000,
        )

        allowCollection.countDown()

        compose.waitUntilAtLeastOneExists(hasText("42 min"), timeoutMillis = 5_000)
        compose.onNodeWithContentDescription("Total usage: 42 minutes").assertIsDisplayed()
    }

    @Test
    fun backAndForwardButtonsChangeTheDate() {
        setScreen()

        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithText("2026-09-30").assertIsDisplayed()

        compose.onNodeWithContentDescription("Next day").performClick()
        compose.onNodeWithText("2026-10-01").assertIsDisplayed()
    }

    @Test
    fun todayIsTheForwardButtonsUpperLimit() {
        setScreen()

        val forward = compose.onNodeWithContentDescription("Next day")
        forward.assertIsEnabled()
        forward.performClick()
        compose.onNodeWithText("2026-10-02").assertIsDisplayed()
        forward.assertIsNotEnabled()
    }

    @Test
    fun tappingToggleWritesRuleAndShowsExcludedState() {
        val date = today.minusDays(1)
        database.usageDao().insert(
            DailyUsage(date, 45, Source.COLLECTED, Instant.parse("2026-10-02T07:00:00Z")),
            listOf(AppUsage(date, "com.example.reader", 45)),
        )
        setScreen()
        compose.waitUntilAtLeastOneExists(hasText("Reader"), timeoutMillis = 5_000)

        val toggle = compose.onNodeWithContentDescription("Exclude Reader")
        toggle.performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            database.appRuleDao().all().isNotEmpty()
        }
        assertEquals(
            listOf(AppRule("com.example.reader", "Reader", excluded = true)),
            database.appRuleDao().all(),
        )
        toggle.assertIsOn()
    }

    @Test
    fun tappingToggleAgainClearsExcludedState() {
        val date = today.minusDays(1)
        database.usageDao().insert(
            DailyUsage(date, 45, Source.COLLECTED, Instant.parse("2026-10-02T07:00:00Z")),
            listOf(AppUsage(date, "com.example.reader", 45)),
        )
        setScreen()
        compose.waitUntilAtLeastOneExists(hasText("Reader"), timeoutMillis = 5_000)
        val toggle = compose.onNodeWithContentDescription("Exclude Reader")
        toggle.performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            database.appRuleDao().all().singleOrNull()?.excluded == true
        }

        toggle.performClick()

        compose.waitUntil(timeoutMillis = 5_000) {
            database.appRuleDao().all().singleOrNull()?.excluded == false
        }
        assertEquals(
            listOf(AppRule("com.example.reader", "Reader", excluded = false)),
            database.appRuleDao().all(),
        )
        toggle.assertIsOff()
    }

    @Test
    fun todayShowsLiveAppsWithoutSavingAnIncompleteDay() {
        database.appRuleDao().insert(AppRule("com.example.reader", "Reader", true))
        var reads = 0
        setScreen(readCurrentDay = { reads++; snapshot(75) })
        compose.waitUntilAtLeastOneExists(hasText("Not collected yet"), 5_000)
        assertEquals(0, reads)
        compose.onNodeWithContentDescription("Next day").performClick()

        compose.waitUntilAtLeastOneExists(hasText("75 min"), 5_000)
        compose.onNodeWithText("Reader").assertIsDisplayed()
        compose.onNodeWithText("45 min").assertIsDisplayed()
        compose.onNodeWithText("Video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Exclude Reader").assertIsOn()
        compose.onNodeWithText("Enter by hand").assertDoesNotExist()
        assertNull(database.usageDao().day(today))
    }

    @Test
    fun returningToForegroundRefreshesTodayAndUnavailableReadsKeepSameDaySnapshot() {
        val owner = TestLifecycleOwner()
        var result: CurrentDayUsageSnapshotResult = snapshot(75)
        setScreen(readCurrentDay = { result }, lifecycleOwner = owner)
        compose.onNodeWithContentDescription("Next day").performClick()
        compose.waitUntilAtLeastOneExists(hasText("75 min"), 5_000)

        result = snapshot(95)
        compose.runOnIdle { owner.resume() }
        compose.waitUntilAtLeastOneExists(hasText("95 min"), 5_000)

        result = CurrentDayUsageSnapshotResult.Unavailable
        compose.runOnIdle { owner.resume() }
        compose.onNodeWithContentDescription("Total usage: 95 minutes").assertIsDisplayed()
        assertNull(database.usageDao().day(today))

        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Not collected yet"), 5_000)
        compose.onNodeWithContentDescription("Total usage: 95 minutes").assertDoesNotExist()
    }

    @Test
    fun liveTodayReplacesStoredTotalWithoutOverwritingIt() {
        database.usageDao().insert(DailyUsage(today, 500, Source.IMPORTED, Instant.EPOCH), emptyList())
        setScreen(readCurrentDay = { snapshot(75) })
        compose.onNodeWithContentDescription("Next day").performClick()
        compose.waitUntilAtLeastOneExists(hasText("75 min"), 5_000)
        compose.onNodeWithContentDescription("Total usage: 500 minutes").assertDoesNotExist()
        assertEquals(500, database.usageDao().day(today)!!.day.totalMinutes)
    }

    @Test
    fun snapshotForAnotherDateIsNotShownAsToday() {
        setScreen(readCurrentDay = { snapshot(75, today.plusDays(1)) })
        compose.onNodeWithContentDescription("Next day").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Not collected yet"), 5_000)
        compose.onNodeWithContentDescription("Total usage: 75 minutes").assertDoesNotExist()
    }

    private fun snapshot(minutes: Int, date: LocalDate = today) =
        CurrentDayUsageSnapshotResult.Available(
            CurrentDayUsageSnapshot(
                date, Instant.EPOCH, minutes * 60_000L,
                mapOf(
                    "com.example.reader" to 45 * 60_000L,
                    "com.example.video" to (minutes - 45) * 60_000L,
                ),
            ),
        )

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle = registry
        fun resume() {
            registry.currentState = Lifecycle.State.STARTED
            registry.currentState = Lifecycle.State.RESUMED
        }
    }

    private fun setScreen(
        readCurrentDay: (ZoneId) -> CurrentDayUsageSnapshotResult = { CurrentDayUsageSnapshotResult.Unavailable },
        lifecycleOwner: LifecycleOwner? = null,
    ) {
        compose.setContent {
            MaterialTheme {
                AppNavigationShell(
                    initialDestination = AppDestination.DAY_DETAIL,
                    dayDetail = {
                        DayDetailScreen(
                            dao = database.usageDao(),
                            appRuleDao = database.appRuleDao(),
                            appInfoSource = resolver,
                            today = today,
                            readCurrentDay = readCurrentDay,
                            lifecycleOwner = lifecycleOwner ?: LocalLifecycleOwner.current,
                            loadDispatcher = Dispatchers.Unconfined,
                        )
                    },
                    goals = { Text("Goals screen") },
                )
            }
        }
    }
}
