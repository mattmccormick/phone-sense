package ca.mattmccormick.screenbudget.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageCoverageMigrationTest {
    @Test fun upgradePreservesHistoryAndExplicitRules() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "coverage-migration.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE daily_usage (date TEXT NOT NULL PRIMARY KEY, totalMinutes INTEGER NOT NULL, source TEXT NOT NULL, collectedAt INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE app_usage (date TEXT NOT NULL, appKey TEXT NOT NULL, minutes INTEGER NOT NULL, PRIMARY KEY(date, appKey), FOREIGN KEY(date) REFERENCES daily_usage(date) ON DELETE CASCADE)")
                        db.execSQL("CREATE INDEX index_app_usage_date ON app_usage(date)")
                        UsageDatabase.MIGRATION_1_2.migrate(db)
                        UsageDatabase.MIGRATION_2_3.migrate(db)
                        db.execSQL("INSERT INTO daily_usage VALUES ('2026-10-04', 114, 'COLLECTED', 0)")
                        db.execSQL("INSERT INTO app_rules VALUES ('ca.mattmccormick.screenbudget', 'Screen Budget', 0)")
                        db.execSQL("INSERT INTO app_rules VALUES ('custom.app', 'Custom', 1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        helper.writableDatabase
        helper.close()
        fun open() = Room.databaseBuilder(context, UsageDatabase::class.java, name)
            .addMigrations(UsageDatabase.MIGRATION_3_4)
            .addCallback(UsageDatabase.DEFAULT_APP_RULES)
            .allowMainThreadQueries().build()
        open().useDatabase { db ->
            val day = db.usageDao().day(LocalDate.of(2026, 10, 4))!!.day
            assertEquals(114, day.totalMinutes)
            assertFalse(day.includesAllApps)
            assertEquals(setOf("custom.app", "com.android.systemui", "com.google.android.apps.nexuslauncher"),
                db.appRuleDao().excludedKeys().toSet())
            db.appRuleDao().insert(AppRule("com.google.android.apps.nexuslauncher", "Pixel Launcher", false))
        }
        open().useDatabase { db ->
            assertFalse("Explicit inclusions must survive reopening", "com.google.android.apps.nexuslauncher" in db.appRuleDao().excludedKeys())
        }
        context.deleteDatabase(name)
    }

    @Test fun exportImportPreservesCoverageFlagAndAcceptsOldFiles() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Room.inMemoryDatabaseBuilder(context, UsageDatabase::class.java).allowMainThreadQueries().build().useDatabase { db ->
            val json = """{"exportedAt":"2026-10-05T12:00:00Z","weekStartDay":"MONDAY",
                "dailyUsage":[{"date":"2026-10-04","totalMinutes":197,"source":"COLLECTED","includesAllApps":true},
                {"date":"2026-10-03","totalMinutes":114,"source":"COLLECTED"}],
                "appUsage":[],"appRules":[],"goals":[]}"""
            ca.mattmccormick.screenbudget.export.ImportService(db).importJson(json)
            assertEquals(true, db.usageDao().day(LocalDate.of(2026, 10, 4))!!.day.includesAllApps)
            assertEquals(false, db.usageDao().day(LocalDate.of(2026, 10, 3))!!.day.includesAllApps)
            val output = java.io.ByteArrayOutputStream()
            ca.mattmccormick.screenbudget.writeExport(android.net.Uri.parse("content://test/export"),
                ca.mattmccormick.screenbudget.ExportFormat.JSON, db, Settings(), openOutputStream = { output })
            val exported = ca.mattmccormick.screenbudget.export.decodeExport(output.toString("UTF-8"))
            assertEquals(listOf(false, true), exported.dailyUsage.map { it.includesAllApps })
        }
    }

    @Test fun freshInstallGetsEditableDefaultExclusions() {
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), UsageDatabase::class.java)
            .addCallback(UsageDatabase.DEFAULT_APP_RULES).allowMainThreadQueries().build().useDatabase { db ->
                assertEquals(3, db.appRuleDao().excludedKeys().size)
                db.appRuleDao().insert(AppRule("ca.mattmccormick.screenbudget", "Screen Budget", false))
                assertEquals(2, db.appRuleDao().excludedKeys().size)
            }
    }
}

private fun UsageDatabase.useDatabase(block: (UsageDatabase) -> Unit) {
    try { block(this) } finally { close() }
}
