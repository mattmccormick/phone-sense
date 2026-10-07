package ca.mattmccormick.phone_sense

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal object NotificationChannels {
    const val DAILY_BUDGET = "daily_budget"
    const val WEEKLY_SUMMARY = "weekly_summary"
    const val GOAL_NEEDED = "goal_needed"
    const val USAGE_ACCESS_NEEDED = "usage_access_needed"

    fun create(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    DAILY_BUDGET,
                    context.getString(R.string.daily_budget_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    WEEKLY_SUMMARY,
                    context.getString(R.string.weekly_summary_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    GOAL_NEEDED,
                    context.getString(R.string.goal_needed_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
                NotificationChannel(
                    USAGE_ACCESS_NEEDED,
                    context.getString(R.string.usage_access_needed_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }
}

@Composable
internal fun NotificationOnboardingScreen(
    onTurnOnNotifications: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.notification_onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(stringResource(R.string.notification_onboarding_explanation))
                Text(stringResource(R.string.daily_budget_notification))
                Text(stringResource(R.string.weekly_summary_notification))
                Text(stringResource(R.string.goal_needed_notification))
                Text(stringResource(R.string.usage_access_needed_notification))
                Button(
                    onClick = onTurnOnNotifications,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.turn_on_notifications))
                }
                OutlinedButton(
                    onClick = onNotNow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.not_now))
                }
            }
        }
    }
}
