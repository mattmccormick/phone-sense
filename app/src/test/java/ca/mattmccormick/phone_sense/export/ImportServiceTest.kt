package ca.mattmccormick.phone_sense.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.phone_sense.data.AppRule
import ca.mattmccormick.phone_sense.data.AppUsage
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDatabase
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportServiceTest {
    private lateinit var database: UsageDatabase
    private lateinit var importer: ImportService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            UsageDatabase::class.java,
        ).allowMainThreadQueries().build()
        importer = ImportService(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun importedDaysAreStoredAsImported() {
        val date = LocalDate.of(2026, 10, 2)

        val result = importer.importJson(
            document(
                dailyUsage = listOf(ExportDailyUsage(date.toString(), 75, "COLLECTED")),
                appUsage = listOf(ExportAppUsage(date.toString(), "com.example.reader", 45)),
            ),
        )

        assertEquals(ImportResult(1, 0, java.time.DayOfWeek.SATURDAY), result)
        val stored = database.usageDao().day(date)!!
        assertEquals(Source.IMPORTED, stored.day.source)
        assertEquals(75, stored.day.totalMinutes)
        assertEquals("com.example.reader", stored.apps.single().appKey)
    }

    @Test
    fun collectedDaysAreSkippedAndCounted() {
        val collectedDate = LocalDate.of(2026, 10, 1)
        val importedDate = LocalDate.of(2026, 10, 2)
        val collected = DailyUsage(
            collectedDate,
            90,
            Source.COLLECTED,
            Instant.parse("2026-10-02T07:00:00Z"),
        )
        val collectedApps = listOf(AppUsage(collectedDate, "com.example.original", 90))
        database.usageDao().insert(collected, collectedApps)

        val result = importer.importJson(
            document(
                dailyUsage = listOf(
                    ExportDailyUsage(collectedDate.toString(), 10, "MANUAL"),
                    ExportDailyUsage(importedDate.toString(), 20, "MANUAL"),
                ),
                appUsage = listOf(
                    ExportAppUsage(collectedDate.toString(), "com.example.replacement", 10),
                ),
            ),
        )

        assertEquals(ImportResult(1, 1, java.time.DayOfWeek.SATURDAY), result)
        assertEquals(collected, database.usageDao().day(collectedDate)!!.day)
        assertEquals(collectedApps, database.usageDao().day(collectedDate)!!.apps)
    }

    @Test
    fun rulesAndGoalsReplaceRowsWithTheSameKey() {
        val weekStart = LocalDate.of(2026, 9, 26)
        database.appRuleDao().insert(AppRule("com.example.reader", "Old label", true))
        database.goalDao().insert(Goal(weekStart, 30))

        importer.importJson(
            document(
                appRules = listOf(
                    ExportAppRule("com.example.reader", "Reader", false),
                    ExportAppRule("com.example.video", "Video", true),
                ),
                goals = listOf(ExportGoal(weekStart.toString(), 45)),
            ),
        )

        assertEquals(
            listOf(
                AppRule("com.example.reader", "Reader", false),
                AppRule("com.example.video", "Video", true),
            ),
            database.appRuleDao().all(),
        )
        assertEquals(Goal(weekStart, 45), database.goalDao().forWeek(weekStart))
    }

    @Test
    fun decodeFailureChangesNothing() {
        val date = LocalDate.of(2026, 10, 1)
        val originalDay = DailyUsage(date, 90, Source.COLLECTED, Instant.EPOCH)
        val originalRule = AppRule("com.example.reader", "Reader", false)
        val originalGoal = Goal(LocalDate.of(2026, 9, 26), 45)
        database.usageDao().insert(originalDay, emptyList())
        database.appRuleDao().insert(originalRule)
        database.goalDao().insert(originalGoal)

        assertThrows(Exception::class.java) { importer.importJson("not JSON") }

        assertEquals(originalDay, database.usageDao().day(date)!!.day)
        assertEquals(listOf(originalRule), database.appRuleDao().all())
        assertEquals(listOf(originalGoal), database.goalDao().newestFirst())
    }

    private fun document(
        dailyUsage: List<ExportDailyUsage> = emptyList(),
        appUsage: List<ExportAppUsage> = emptyList(),
        appRules: List<ExportAppRule> = emptyList(),
        goals: List<ExportGoal> = emptyList(),
    ): String = encodeExport(
        ExportDocument(
            exportedAt = "2026-10-03T18:30:00Z",
            weekStartDay = "SATURDAY",
            dailyUsage = dailyUsage,
            appUsage = appUsage,
            appRules = appRules,
            goals = goals,
        ),
    )
}
