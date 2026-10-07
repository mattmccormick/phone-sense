package ca.mattmccormick.phone_sense.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DailyUsage::class, AppUsage::class, Goal::class, AppRule::class],
    version = 4,
    exportSchema = true,
)
@TypeConverters(UsageConverters::class)
abstract class UsageDatabase : RoomDatabase() {
    abstract fun usageDao(): UsageDao
    abstract fun appRuleDao(): AppRuleDao
    abstract fun goalDao(): GoalDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_usage ADD COLUMN includesAllApps INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Previously hidden apps remain excluded from the allowance, but can now be changed.
        // INSERT OR IGNORE preserves both explicit inclusions and exclusions across reopens.
        val DEFAULT_APP_RULES = object : Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                listOf(
                    "com.android.systemui" to "System UI",
                    "com.google.android.apps.nexuslauncher" to "Pixel Launcher",
                    "ca.mattmccormick.phone_sense" to "Phone Sense",
                ).forEach { (key, label) ->
                    db.execSQL("INSERT OR IGNORE INTO app_rules (appKey, label, excluded) VALUES (?, ?, 1)",
                        arrayOf(key, label))
                }
            }
        }

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
