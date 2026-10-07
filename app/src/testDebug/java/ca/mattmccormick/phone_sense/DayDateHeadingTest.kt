package ca.mattmccormick.phone_sense

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DayDateHeadingTest {
    private val today = LocalDate.of(2026, 10, 5)

    @Test fun recentDatesHaveOnlyRelativeLabels() {
        assertEquals("Today", dayDateHeading(today, today, Locale.US))
        assertEquals("Yesterday", dayDateHeading(today.minusDays(1), today, Locale.US))
    }

    @Test fun labelsAndWeekdaysFollowTheLocale() {
        assertEquals("Heute", dayDateHeading(today, today, Locale.GERMANY))
        assertEquals("Gestern", dayDateHeading(today.minusDays(1), today, Locale.GERMANY))
        val french = dayDateHeading(today.minusDays(2), today, Locale.FRANCE)
        assertTrue(french, french.contains("sam.") && french.contains("oct.") && french.contains("2026"))
    }

    @Test fun olderDatesUseLocaleDateOrder() {
        val date = today.minusDays(2)
        val us = dayDateHeading(date, today, Locale.US)
        val uk = dayDateHeading(date, today, Locale.UK)
        assertTrue(us, us.indexOf("Oct") < us.indexOf("3"))
        assertTrue(uk, uk.indexOf("3") < uk.indexOf("Oct"))
        assertTrue(us, us.contains("Sat"))
        assertTrue(uk, uk.contains("Sat"))
    }

    @Test fun yesterdayWorksAcrossYearAndDaylightSavingBoundaries() {
        for (date in listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 9))) {
            assertTrue(dayDateHeading(date.minusDays(1), date, Locale.US).startsWith("Yesterday"))
        }
    }
}
