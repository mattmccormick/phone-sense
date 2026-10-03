package ca.mattmccormick.screenbudget.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val weekStart: LocalDate,
    val minutes: Int,
)
