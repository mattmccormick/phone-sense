package ca.mattmccormick.screenbudget.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyUsage::class, AppUsage::class, Goal::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(UsageConverters::class)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun goalDao(): GoalDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `goals` " +
                        "(`weekStart` TEXT NOT NULL, `minutes` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`weekStart`))",
                )
            }
        }
    }
}
