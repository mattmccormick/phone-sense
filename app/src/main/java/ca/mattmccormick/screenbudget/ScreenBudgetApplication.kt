package ca.mattmccormick.screenbudget

import android.app.Application
import androidx.work.Configuration
import androidx.work.WorkManager
import java.time.Clock

class ScreenBudgetApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        scheduleDailyCollection(
            WorkManager.getInstance(this),
            Clock.systemDefaultZone(),
        )
    }
}
