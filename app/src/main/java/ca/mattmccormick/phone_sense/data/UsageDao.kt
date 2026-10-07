package ca.mattmccormick.phone_sense.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import java.time.LocalDate

@Dao
abstract class UsageDao {
    @Transaction
    open fun insert(day: DailyUsage, apps: List<AppUsage>) {
        deleteApps(day.date)
        insertDay(day)
        if (apps.isNotEmpty()) insertApps(apps)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertDay(day: DailyUsage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract fun insertApps(apps: List<AppUsage>)

    @Query("DELETE FROM app_usage WHERE date = :date")
    protected abstract fun deleteApps(date: LocalDate)

    @Transaction
    @Query("SELECT * FROM daily_usage WHERE date = :date")
    abstract fun day(date: LocalDate): DayWithApps?

    @Query("SELECT date FROM daily_usage WHERE date BETWEEN :start AND :end ORDER BY date")
    abstract fun datesBetween(start: LocalDate, end: LocalDate): List<LocalDate>

    @Transaction
    @Query("SELECT * FROM daily_usage WHERE date BETWEEN :start AND :end ORDER BY date")
    abstract fun daysBetween(start: LocalDate, end: LocalDate): List<DayWithApps>

    @Query("SELECT DISTINCT appKey FROM app_usage ORDER BY appKey")
    abstract fun appKeys(): List<String>

    @Transaction
    @Query("SELECT * FROM daily_usage ORDER BY date")
    abstract fun allDays(): List<DayWithApps>
}
