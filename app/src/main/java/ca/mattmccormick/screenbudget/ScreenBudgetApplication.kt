package ca.mattmccormick.screenbudget

import android.app.Application
import androidx.room.ExperimentalRoomApi
import androidx.room.Room
import androidx.work.Configuration
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.util.concurrent.TimeUnit

class ScreenBudgetApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()
    @OptIn(ExperimentalRoomApi::class)
    val database: UsageDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            UsageDatabase::class.java,
            "usage.db",
        )
            .addMigrations(UsageDatabase.MIGRATION_1_2, UsageDatabase.MIGRATION_2_3, UsageDatabase.MIGRATION_3_4)
            .addCallback(UsageDatabase.DEFAULT_APP_RULES)
            .setAutoCloseTimeout(1, TimeUnit.MINUTES)
            .build()
    }
}
