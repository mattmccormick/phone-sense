package ca.mattmccormick.screenbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.room.Room
import ca.mattmccormick.screenbudget.data.UsageDatabase

class MainActivity : ComponentActivity() {
    private lateinit var database: UsageDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(
            applicationContext,
            UsageDatabase::class.java,
            "usage.db",
        ).build()
        setContent {
            MaterialTheme {
                DayDetailScreen(
                    dao = database.usageDao(),
                    appInfoSource = AppInfoResolver(packageManager),
                )
            }
        }
    }

    override fun onDestroy() {
        database.close()
        super.onDestroy()
    }
}
