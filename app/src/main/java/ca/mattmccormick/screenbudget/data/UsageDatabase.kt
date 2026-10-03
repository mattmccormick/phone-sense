package ca.mattmccormick.screenbudget.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyUsage::class, AppUsage::class, AppRule::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(UsageConverters::class)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun appRuleDao(): AppRuleDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
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
