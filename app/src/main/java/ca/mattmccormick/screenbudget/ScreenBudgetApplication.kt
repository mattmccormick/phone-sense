package ca.mattmccormick.screenbudget

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import ca.mattmccormick.screenbudget.data.UsageDatabase

class ScreenBudgetApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()
    val database: UsageDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            UsageDatabase::class.java,
            "usage.db",
        )
            .addMigrations(UsageDatabase.MIGRATION_1_2, UsageDatabase.MIGRATION_2_3)
            .build()
    }
}
