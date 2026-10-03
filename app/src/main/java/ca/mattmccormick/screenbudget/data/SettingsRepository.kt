package ca.mattmccormick.screenbudget.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val weekStartDay: DayOfWeek = DayOfWeek.SATURDAY,
    val notificationTime: LocalTime = LocalTime.of(7, 0),
    val reductionPercent: Int = 10,
    val onboardingDone: Boolean = false,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data.map { preferences ->
        Settings(
            weekStartDay = preferences[WEEK_START_DAY]?.let(DayOfWeek::valueOf)
                ?: DayOfWeek.SATURDAY,
            notificationTime = preferences[NOTIFICATION_TIME]?.let(LocalTime::parse)
                ?: LocalTime.of(7, 0),
            reductionPercent = preferences[REDUCTION_PERCENT] ?: 10,
            onboardingDone = preferences[ONBOARDING_DONE] ?: false,
        )
    }

    suspend fun setWeekStartDay(day: DayOfWeek) {
        dataStore.edit { it[WEEK_START_DAY] = day.name }
    }

    suspend fun setNotificationTime(time: LocalTime) {
        dataStore.edit { it[NOTIFICATION_TIME] = time.toString() }
    }

    suspend fun setReductionPercent(percent: Int) {
        dataStore.edit { it[REDUCTION_PERCENT] = percent }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        dataStore.edit { it[ONBOARDING_DONE] = done }
    }

    private companion object {
        val WEEK_START_DAY = stringPreferencesKey("week_start_day")
        val NOTIFICATION_TIME = stringPreferencesKey("notification_time")
        val REDUCTION_PERCENT = intPreferencesKey("reduction_percent")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }
}
