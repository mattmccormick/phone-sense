package ca.mattmccormick.screenbudget.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
    private lateinit var store: DataStore<Preferences>
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.preferencesDataStoreFile("settings-test")
        file.delete()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repository = SettingsRepository(store)
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
                notificationsDeclined = false,
            ),
            repository.settings.first(),
        )
    }

    @Test
    fun settingsReadBackWrittenValues() = runBlocking {
        store.edit { it[intPreferencesKey("reduction_percent")] = 25 }
        repository.setNotificationsDeclined(notificationsDeclined = true)
        repository.finishOnboarding(DayOfWeek.MONDAY, LocalTime.of(18, 45))

        assertEquals(
            Settings(
                weekStartDay = DayOfWeek.MONDAY,
                notificationTime = LocalTime.of(18, 45),
                reductionPercent = 25,
                onboardingDone = true,
                notificationsDeclined = true,
            ),
            repository.settings.first(),
        )
    }

    @Test
    fun finishingOnboardingWritesScheduleAndCompletionTogether() = runBlocking {
        repository.finishOnboarding(
            weekStartDay = DayOfWeek.MONDAY,
            notificationTime = LocalTime.of(18, 45),
        )

        assertEquals(
            Settings(
                weekStartDay = DayOfWeek.MONDAY,
                notificationTime = LocalTime.of(18, 45),
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

        repository.setNotificationsDeclined(true)
        collection.join()

        assertEquals(listOf(Settings(), Settings(notificationsDeclined = true)), emissions)
    }
}
