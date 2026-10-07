package ca.mattmccormick.phone_sense

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import ca.mattmccormick.phone_sense.data.AppRule
import ca.mattmccormick.phone_sense.data.AppUsage
import ca.mattmccormick.phone_sense.data.DailyUsage
import ca.mattmccormick.phone_sense.data.Goal
import ca.mattmccormick.phone_sense.data.Settings
import ca.mattmccormick.phone_sense.data.Source
import ca.mattmccormick.phone_sense.data.UsageDatabase
import ca.mattmccormick.phone_sense.export.decodeExport
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportFlowTest {
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
    fun jsonResultUriReceivesTheFullDatabase() {
        val date = LocalDate.of(2026, 10, 2)
        database.usageDao().insert(
            DailyUsage(date, 75, Source.COLLECTED, Instant.parse("2026-10-03T18:00:00Z")),
            listOf(AppUsage(date, "video.app", 45)),
        )
        database.appRuleDao().insert(AppRule("video.app", "Video", excluded = true))
        database.goalDao().insert(Goal(LocalDate.of(2026, 9, 26), 420))
        val destination = Uri.parse("content://exports/full.json")
        val output = ByteArrayOutputStream()

        val error = handleExportResult(
            Activity.RESULT_OK,
            Intent().setData(destination),
        ) { resultUri ->
            writeExport(
                destination = resultUri,
                format = ExportFormat.JSON,
                database = database,
                settings = Settings(weekStartDay = DayOfWeek.MONDAY),
                exportedAt = Instant.parse("2026-10-03T18:30:00Z"),
                openOutputStream = { uri ->
                    assertEquals(destination, uri)
                    output
                },
            )
        }

        assertEquals(null, error)
        val document = decodeExport(output.toString(Charsets.UTF_8.name()))
        assertEquals("2026-10-03T18:30:00Z", document.exportedAt)
        assertEquals("MONDAY", document.weekStartDay)
        assertEquals(listOf("2026-10-02"), document.dailyUsage.map { it.date })
        assertEquals(listOf("video.app"), document.appUsage.map { it.appKey })
        assertEquals(listOf("Video"), document.appRules.map { it.label })
        assertEquals(listOf(420), document.goals.map { it.minutes })
    }

    @Test
    fun csvResultUriReceivesUsageData() {
        val date = LocalDate.of(2026, 10, 2)
        database.usageDao().insert(
            DailyUsage(date, 75, Source.COLLECTED, Instant.parse("2026-10-03T18:00:00Z")),
            listOf(AppUsage(date, "video.app", 45)),
        )
        val destination = Uri.parse("content://exports/usage.csv")
        val output = ByteArrayOutputStream()

        val error = handleExportResult(
            Activity.RESULT_OK,
            Intent().setData(destination),
        ) { resultUri ->
            writeExport(
                destination = resultUri,
                format = ExportFormat.CSV,
                database = database,
                settings = Settings(),
                openOutputStream = { uri ->
                    assertEquals(destination, uri)
                    output
                },
            )
        }

        assertEquals(null, error)
        assertEquals(
            "date,totalMinutes,video.app\n2026-10-02,75,45\n",
            output.toString(Charsets.UTF_8.name()),
        )
    }

    @Test
    fun cancelledPickerLeavesNoError() {
        var wroteExport = false

        val error = handleExportResult(Activity.RESULT_CANCELED, null) {
            wroteExport = true
        }

        assertEquals(null, error)
        assertFalse(wroteExport)
    }
}
