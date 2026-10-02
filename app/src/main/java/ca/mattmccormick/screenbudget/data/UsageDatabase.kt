package ca.mattmccormick.screenbudget.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [DailyUsage::class, AppUsage::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(UsageConverters::class)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
}
