package ca.mattmccormick.phone_sense.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.time.LocalDate

enum class Source {
    COLLECTED,
    MANUAL,
    IMPORTED,
}

@Entity(tableName = "daily_usage")
data class DailyUsage(
    @PrimaryKey val date: LocalDate,
    val totalMinutes: Int,
    val source: Source,
    val collectedAt: Instant,
    @ColumnInfo(defaultValue = "0") val includesAllApps: Boolean = false,
)

@Entity(
    tableName = "app_usage",
    primaryKeys = ["date", "appKey"],
    foreignKeys = [
        ForeignKey(
            entity = DailyUsage::class,
            parentColumns = ["date"],
            childColumns = ["date"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("date")],
)
data class AppUsage(
    val date: LocalDate,
    val appKey: String,
    val minutes: Int,
)

data class DayWithApps(
    @Embedded val day: DailyUsage,
    @Relation(parentColumn = "date", entityColumn = "date")
    val apps: List<AppUsage>,
)
