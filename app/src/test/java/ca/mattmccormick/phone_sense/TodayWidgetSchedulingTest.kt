package ca.mattmccormick.phone_sense

import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import org.robolectric.Shadows.shadowOf
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TodayWidgetSchedulingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workManager: WorkManager

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        workManager = WorkManager.getInstance(context)
    }

    @Test fun multipleWidgetsShareOnePeriodicScheduleAndRemovingLastCancelsIt() {
        val provider = TodayWidget()
        val manager = AppWidgetManager.getInstance(context)
        provider.onUpdate(context, manager, intArrayOf(1))
        provider.onUpdate(context, manager, intArrayOf(2))
        val scheduled = workManager.getWorkInfosForUniqueWork(WidgetUpdates.PERIODIC_WORK).get()
        assertEquals(1, scheduled.size)
        assertEquals(15 * 60 * 1000L, scheduled.single().periodicityInfo!!.repeatIntervalMillis)
        provider.onDisabled(context)
        assertEquals(WorkInfo.State.CANCELLED,
            workManager.getWorkInfosForUniqueWork(WidgetUpdates.PERIODIC_WORK).get().single().state)
    }

    @Test fun removingTodayKeepsScheduleForRemainingChart() {
        val manager = AppWidgetManager.getInstance(context)
        val info = AppWidgetProviderInfo().apply {
            provider = ComponentName(context, ChartWidget::class.java)
            initialLayout = R.layout.chart_widget
        }
        shadowOf(manager).addInstalledProvider(info)
        shadowOf(manager).setAllowedToBindAppWidgets(true)
        assertTrue(manager.bindAppWidgetIdIfAllowed(71, info.provider))
        ChartWidget().onUpdate(context, manager, intArrayOf(71))
        TodayWidget().onDisabled(context)
        val scheduled = workManager.getWorkInfosForUniqueWork(WidgetUpdates.PERIODIC_WORK).get()
        assertEquals(1, scheduled.size)
        assertNotEquals(WorkInfo.State.CANCELLED, scheduled.single().state)
    }

    @Test fun leavingAppWithoutWidgetsDoesNotEnqueueWork() {
        WidgetUpdates.refresh(context)
        assertTrue(workManager.getWorkInfosForUniqueWork(WidgetUpdates.REFRESH_WORK).get().isEmpty())
    }
}
