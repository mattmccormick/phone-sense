package ca.mattmccormick.screenbudget.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageDaoTest {
    private lateinit var database: UsageDatabase
    private lateinit var dao: UsageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.usageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertDayWithAppsReadsTheDayAndItsAppsBack() {
        val day = DailyUsage(
            date = LocalDate.of(2026, 9, 28),
            totalMinutes = 75,
            source = Source.COLLECTED,
            collectedAt = Instant.parse("2026-09-29T07:15:00Z"),
        )
        val apps = listOf(
            AppUsage(day.date, "com.example.reader", 45),
            AppUsage(day.date, "com.example.video", 30),
        )

        dao.insert(day, apps)

        assertEquals(DayWithApps(day, apps), dao.day(day.date))
    }

    @Test
    fun insertingSameDateReplacesDayAndRemovesOldAppRows() {
        val date = LocalDate.of(2026, 9, 28)
        dao.insert(
            DailyUsage(date, 75, Source.COLLECTED, Instant.parse("2026-09-29T07:15:00Z")),
            listOf(
                AppUsage(date, "com.example.reader", 45),
                AppUsage(date, "com.example.video", 30),
            ),
        )
        val replacement = DailyUsage(
            date,
            20,
            Source.MANUAL,
            Instant.parse("2026-09-29T08:00:00Z"),
        )
        val replacementApps = listOf(AppUsage(date, "com.example.messages", 20))

        dao.insert(replacement, replacementApps)

        assertEquals(DayWithApps(replacement, replacementApps), dao.day(date))
    }

    @Test
    fun datesBetweenListsOnlyDatesWithRows() {
        val september28 = LocalDate.of(2026, 9, 28)
        val september30 = LocalDate.of(2026, 9, 30)
        val october1 = LocalDate.of(2026, 10, 1)
        listOf(september28, september30, october1).forEach { date ->
            dao.insert(
                DailyUsage(date, 10, Source.IMPORTED, Instant.parse("2026-10-02T07:00:00Z")),
                emptyList(),
            )
        }

        val dates = dao.datesBetween(september28, september30)

        assertEquals(listOf(september28, september30), dates)
    }

    @Test
    fun daysBetweenReturnsDaysWithAppsInDateOrder() {
        val september28 = LocalDate.of(2026, 9, 28)
        val september30 = LocalDate.of(2026, 9, 30)
        val october1 = LocalDate.of(2026, 10, 1)
        val firstDay = DailyUsage(
            september28,
            15,
            Source.COLLECTED,
            Instant.parse("2026-09-29T07:00:00Z"),
        )
        val lastDay = DailyUsage(
            september30,
            25,
            Source.COLLECTED,
            Instant.parse("2026-10-01T07:00:00Z"),
        )
        val firstApps = listOf(AppUsage(september28, "com.example.reader", 15))
        val lastApps = listOf(AppUsage(september30, "com.example.video", 25))
        dao.insert(lastDay, lastApps)
        dao.insert(firstDay, firstApps)
        dao.insert(
            DailyUsage(october1, 5, Source.COLLECTED, Instant.parse("2026-10-02T07:00:00Z")),
            listOf(AppUsage(october1, "com.example.messages", 5)),
        )

        val days = dao.daysBetween(september28, september30)

        assertEquals(
            listOf(DayWithApps(firstDay, firstApps), DayWithApps(lastDay, lastApps)),
            days,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppRuleDaoTest {
    private lateinit var database: UsageDatabase
    private lateinit var dao: AppRuleDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.appRuleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertReadsAllRulesAndOnlyExcludedKeys() {
        val included = AppRule("com.example.maps", "Maps", excluded = false)
        val excluded = AppRule("com.example.video", "Video", excluded = true)

        dao.insert(included)
        dao.insert(excluded)

        assertEquals(listOf(included, excluded), dao.all())
        assertEquals(listOf(excluded.appKey), dao.excludedKeys())
    }
}
