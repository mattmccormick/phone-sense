package ca.mattmccormick.screenbudget.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyUsage::class, AppUsage::class, Goal::class, AppRule::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(UsageConverters::class)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun appRuleDao(): AppRuleDao
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `app_rules` (
                        |`appKey` TEXT NOT NULL,
                        |`label` TEXT NOT NULL,
                        |`excluded` INTEGER NOT NULL,
                        |PRIMARY KEY(`appKey`))
                    """.trimMargin(),
                )
            }
        }
    }
}
