package ca.mattmccormick.phone_sense.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportCodecTest {
    @Test
    fun encodedDocumentDecodesToTheOriginalDocument() {
        val document = ExportDocument(
            exportedAt = "2026-10-03T18:30:00Z",
            weekStartDay = "SATURDAY",
            dailyUsage = listOf(
                ExportDailyUsage("2026-10-02", 75, "COLLECTED"),
            ),
            appUsage = listOf(
                ExportAppUsage("2026-10-02", "com.example.reader", 45),
            ),
            appRules = listOf(
                ExportAppRule("com.example.reader", "Reader", excluded = false),
            ),
            goals = listOf(
                ExportGoal("2026-09-26", 45),
            ),
        )

        val encoded = encodeExport(document)

        assertTrue(encoded.contains("\"version\":1"))
        assertEquals(document, decodeExport(encoded))
    }

    @Test
    fun decodingAnUnknownVersionExplainsWhyItFailed() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            decodeExport(
                """{"version":2,"exportedAt":"2026-10-03T18:30:00Z","weekStartDay":"SATURDAY","dailyUsage":[],"appUsage":[],"appRules":[],"goals":[]}""",
            )
        }

        assertTrue(error.message.orEmpty().contains("Unsupported export version 2"))
    }

    @Test
    fun decodingIgnoresUnknownFields() {
        val decoded = decodeExport(
            """{"version":1,"exportedAt":"2026-10-03T18:30:00Z","weekStartDay":"SATURDAY","dailyUsage":[{"date":"2026-10-02","totalMinutes":75,"source":"COLLECTED","futureDailyField":true}],"appUsage":[],"appRules":[],"goals":[],"futureDocumentField":"ignored"}""",
        )

        assertEquals(
            listOf(ExportDailyUsage("2026-10-02", 75, "COLLECTED")),
            decoded.dailyUsage,
        )
    }

    @Test
    fun csvHasOneRowPerDayAndOneColumnPerAppKey() {
        val dailyUsage = listOf(
            ExportDailyUsage("2026-10-01", 60, "COLLECTED"),
            ExportDailyUsage("2026-10-02", 45, "MANUAL"),
        )
        val appUsage = listOf(
            ExportAppUsage("2026-10-01", "com.example.video", 40),
            ExportAppUsage("2026-10-01", "com.example.reader", 20),
            ExportAppUsage("2026-10-02", "com.example.reader", 30),
        )

        assertEquals(
            """
            date,totalMinutes,com.example.reader,com.example.video
            2026-10-01,60,20,40
            2026-10-02,45,30,
            """.trimIndent() + "\n",
            toCsv(dailyUsage, appUsage),
        )
    }
}
