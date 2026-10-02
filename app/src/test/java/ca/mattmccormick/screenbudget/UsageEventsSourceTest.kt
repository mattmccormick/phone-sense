package ca.mattmccormick.screenbudget

import android.app.AppOpsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventsSourceTest {
    @Test
    fun hasUsageAccessReflectsTheAppOpMode() {
        var checkedArguments: Triple<String, Int, String>? = null
        val allowed = UsageEventsSource(
            eventsQuery = UsageEventsQuery { _, _ -> emptyList() },
            usageAccessQuery = UsageAccessQuery { op, uid, packageName ->
                checkedArguments = Triple(op, uid, packageName)
                AppOpsManager.MODE_ALLOWED
            },
            uid = 123,
            packageName = "ca.mattmccormick.screenbudget",
        )
        val denied = source(appOpMode = AppOpsManager.MODE_IGNORED)
        val unset = source(appOpMode = AppOpsManager.MODE_DEFAULT)

        assertTrue(allowed.hasUsageAccess())
        assertEquals(
            Triple(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                123,
                "ca.mattmccormick.screenbudget",
            ),
            checkedArguments,
        )
        assertFalse(denied.hasUsageAccess())
        assertFalse(unset.hasUsageAccess())
    }

    @Test
    fun eventsConvertsEveryPlatformEventInQueryOrder() {
        var queriedRange: LongRange? = null
        val source = UsageEventsSource(
            eventsQuery = UsageEventsQuery { beginMs, endMs ->
                queriedRange = beginMs..endMs
                listOf(
                    PlatformUsageEvent(1, "com.example.first", "FirstActivity", 100),
                    PlatformUsageEvent(2, "com.example.first", "FirstActivity", 200),
                    PlatformUsageEvent(1, "com.example.second", "SecondActivity", 300),
                )
            },
            usageAccessQuery = UsageAccessQuery { _, _, _ -> AppOpsManager.MODE_ALLOWED },
            uid = 123,
            packageName = "ca.mattmccormick.screenbudget",
        )

        assertEquals(
            listOf(
                UsageEvent(1, "com.example.first", "FirstActivity", 100),
                UsageEvent(2, "com.example.first", "FirstActivity", 200),
                UsageEvent(1, "com.example.second", "SecondActivity", 300),
            ),
            source.events(beginMs = 50, endMs = 350),
        )
        assertEquals(50L..350L, queriedRange)
    }

    @Test
    fun eventsPreservesNullWhenTheSystemHasNoDataYet() {
        val source = source(queriedEvents = null)

        assertNull(source.events(beginMs = 50, endMs = 350))
    }

    private fun source(
        appOpMode: Int = AppOpsManager.MODE_ALLOWED,
        queriedEvents: List<PlatformUsageEvent>? = emptyList(),
    ): UsageEventsSource = UsageEventsSource(
        eventsQuery = UsageEventsQuery { _, _ -> queriedEvents },
        usageAccessQuery = UsageAccessQuery { _, _, _ -> appOpMode },
        uid = 123,
        packageName = "ca.mattmccormick.screenbudget",
    )
}
