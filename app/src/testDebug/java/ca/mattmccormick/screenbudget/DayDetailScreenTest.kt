package ca.mattmccormick.screenbudget

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.screenbudget.data.AppUsage
import ca.mattmccormick.screenbudget.data.AppRule
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        compose.onNodeWithText("75 min").assertIsDisplayed()
        compose.onNodeWithText("Reader").assertIsDisplayed()
        compose.onNodeWithText("45 min").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reader icon").assertIsDisplayed()
        compose.onNodeWithText("Video").assertIsDisplayed()
        compose.onNodeWithText("30 min").assertIsDisplayed()
        assertTrue(
            compose.onNodeWithText("Reader").getUnclippedBoundsInRoot().top <
                compose.onNodeWithText("Video").getUnclippedBoundsInRoot().top,
        )
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
        compose.onNodeWithText("42 min").assertIsDisplayed()
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

    private fun setScreen() {
        compose.setContent {
            MaterialTheme {
                DayDetailScreen(
                    dao = database.usageDao(),
                    appRuleDao = database.appRuleDao(),
                    appInfoSource = resolver,
                    today = today,
                    loadDispatcher = Dispatchers.Unconfined,
                )
            }
        }
    }
}
