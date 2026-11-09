package ca.mattmccormick.phone_sense

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DebugNotificationReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private fun broadcast(action: String) {
        val receiver = DebugNotificationReceiver()
        thread(name = "broadcast-$action") { receiver.post(context, Intent(action)) }.join()
    }

    private fun postedIds(): List<Int> = notificationManager.activeNotifications.map { it.id }

    @Before
    fun setUp() {
        notificationManager.cancelAll()
        context.getSharedPreferences("notifications", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun theDailyActionPostsTheDailyNotification() {
        broadcast(DebugNotificationReceiver.ACTION_DAILY)

        assertEquals(listOf(1), postedIds())
    }

    @Test
    fun theDailyActionPostsAgainWhenItWasAlreadyPostedToday() {
        broadcast(DebugNotificationReceiver.ACTION_DAILY)
        notificationManager.cancelAll()

        broadcast(DebugNotificationReceiver.ACTION_DAILY)

        assertEquals(listOf(1), postedIds())
    }

    @Test
    fun theWeeklyActionPostsTheWeeklyNotification() {
        broadcast(DebugNotificationReceiver.ACTION_WEEKLY)

        assertEquals(listOf(2), postedIds())
    }

    @Test
    fun theWeeklyActionPostsAgainWhenItWasAlreadyPostedThisWeek() {
        broadcast(DebugNotificationReceiver.ACTION_WEEKLY)
        notificationManager.cancelAll()

        broadcast(DebugNotificationReceiver.ACTION_WEEKLY)

        assertEquals(listOf(2), postedIds())
    }
}
