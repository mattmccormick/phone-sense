package ca.mattmccormick.screenbudget.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsRepositoryTest {
    private lateinit var scope: CoroutineScope
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.preferencesDataStoreFile("settings-test")
        file.delete()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        repository = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }),
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun settingsHaveDefaultsWhenNothingIsStored() = runBlocking {
        assertEquals(
            Settings(
                weekStartDay = DayOfWeek.SATURDAY,
                notificationTime = LocalTime.of(7, 0),
                reductionPercent = 10,
                onboardingDone = false,
            ),
            repository.settings.first(),
        )
    }

    @Test
    fun settingsReadBackWrittenValues() = runBlocking {
        repository.setWeekStartDay(DayOfWeek.MONDAY)
        repository.setNotificationTime(LocalTime.of(18, 45))
        repository.setReductionPercent(25)
        repository.setOnboardingDone(true)

        assertEquals(
            Settings(
                weekStartDay = DayOfWeek.MONDAY,
                notificationTime = LocalTime.of(18, 45),
                reductionPercent = 25,
                onboardingDone = true,
            ),
            repository.settings.first(),
        )
    }

    @Test
    fun settingsFlowEmitsWhenAValueChanges() = runBlocking {
        val emissions = mutableListOf<Settings>()
        val collection = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.settings.take(2).toList(emissions)
        }

        repository.setReductionPercent(15)
        collection.join()

        assertEquals(listOf(Settings(), Settings(reductionPercent = 15)), emissions)
    }
}
