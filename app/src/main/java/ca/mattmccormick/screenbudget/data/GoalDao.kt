package ca.mattmccormick.screenbudget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDate

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(goal: Goal)

    @Query("SELECT * FROM goals WHERE weekStart = :weekStart")
    fun forWeek(weekStart: LocalDate): Goal?

    @Query("SELECT * FROM goals WHERE weekStart BETWEEN :start AND :end ORDER BY weekStart")
    fun between(start: LocalDate, end: LocalDate): List<Goal>

    @Query("SELECT * FROM goals ORDER BY weekStart DESC")
    fun newestFirst(): List<Goal>
}
