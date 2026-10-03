package ca.mattmccormick.screenbudget

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import ca.mattmccormick.screenbudget.data.UsageDatabase
import java.time.Clock

class ScreenBudgetApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()
    val database: UsageDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            UsageDatabase::class.java,
            "usage.db",
        ).build()
    }

    override fun onCreate() {
        super.onCreate()
        scheduleDailyCollection(
            WorkManager.getInstance(this),
            Clock.systemDefaultZone(),
        )
    }
}
