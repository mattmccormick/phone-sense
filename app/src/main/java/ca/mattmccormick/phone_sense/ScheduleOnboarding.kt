package ca.mattmccormick.phone_sense

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.mattmccormick.phone_sense.data.Settings
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun ScheduleOnboardingScreen(
    settings: Settings,
    onFinish: (DayOfWeek, LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var weekStartDay by remember(settings.weekStartDay) { mutableStateOf(settings.weekStartDay) }
    var notificationTime by remember(settings.notificationTime) {
        mutableStateOf(settings.notificationTime)
    }
    var weekMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.schedule_onboarding_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.week_starts_on),
                    modifier = Modifier.padding(top = 24.dp),
                )
                Button(onClick = { weekMenuExpanded = true }) {
                    Text(weekStartDay.displayName())
                }
                DropdownMenu(
                    expanded = weekMenuExpanded,
                    onDismissRequest = { weekMenuExpanded = false },
                ) {
                    DayOfWeek.entries.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(day.displayName()) },
                            onClick = {
                                weekStartDay = day
                                weekMenuExpanded = false
                            },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.notification_time),
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(
                    onClick = {
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> notificationTime = LocalTime.of(hour, minute) },
                            notificationTime.hour,
                            notificationTime.minute,
                            true,
                        ).show()
                    },
                ) {
                    Text(notificationTime.format(timeFormatter))
                }
                Button(
                    onClick = { onFinish(weekStartDay, notificationTime) },
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.finish_onboarding))
                }
            }
        }
    }
}

private fun DayOfWeek.displayName(): String =
    getDisplayName(TextStyle.FULL, Locale.getDefault())
