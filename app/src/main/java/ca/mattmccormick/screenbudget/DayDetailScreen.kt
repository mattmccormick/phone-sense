package ca.mattmccormick.screenbudget

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import ca.mattmccormick.screenbudget.data.DayWithApps
import ca.mattmccormick.screenbudget.data.DailyUsage
import ca.mattmccormick.screenbudget.data.Source
import ca.mattmccormick.screenbudget.data.UsageDao
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DayDetailScreen(
    dao: UsageDao,
    appInfoSource: AppInfoSource,
    today: LocalDate = LocalDate.now(),
    refreshKey: Int = 0,
    loadDispatcher: CoroutineDispatcher = Dispatchers.IO,
    modifier: Modifier = Modifier,
) {
    var date by remember(today) { mutableStateOf(today.minusDays(1)) }
    var day by remember { mutableStateOf<DayWithApps?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var enteringManual by remember(date) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(date, dao, refreshKey) {
        loaded = false
        day = withContext(loadDispatcher) { dao.day(date) }
        loaded = true
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { date = date.minusDays(1) },
                    modifier = Modifier.semantics { contentDescription = "Previous day" },
                ) { Text("Back") }
                Text(text = date.toString(), style = MaterialTheme.typography.headlineSmall)
                Button(
                    onClick = { date = date.plusDays(1) },
                    enabled = date < today,
                    modifier = Modifier.semantics { contentDescription = "Next day" },
                ) { Text("Forward") }
            }

            if (loaded) {
                val storedDay = day
                if (storedDay == null) {
                    val manualDate = date
                    Text("Not collected yet", modifier = Modifier.padding(top = 32.dp))
                    if (enteringManual) {
                        ManualEntryForm(
                            modifier = Modifier.weight(1f),
                            onSave = { totalMinutes, apps ->
                                scope.launch {
                                    day = withContext(loadDispatcher) {
                                        dao.insert(
                                            DailyUsage(
                                                date = manualDate,
                                                totalMinutes = totalMinutes,
                                                source = Source.MANUAL,
                                                collectedAt = Instant.now(),
                                            ),
                                            apps,
                                        )
                                        dao.day(manualDate)
                                    }
                                    enteringManual = false
                                }
                            },
                            date = manualDate,
                        )
                    } else {
                        Button(onClick = { enteringManual = true }) { Text("Enter by hand") }
                    }
                } else {
                    Text(
                        text = "${storedDay.day.totalMinutes} min",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(
                            items = storedDay.apps.sortedByDescending { it.minutes },
                            key = { it.appKey },
                        ) { usage ->
                            val appInfo = remember(usage.appKey) {
                                if (usage.appKey.startsWith("label:")) {
                                    AppInfo(usage.appKey.removePrefix("label:"), null)
                                } else {
                                    appInfoSource.resolve(usage.appKey)
                                }
                            }
                            AppUsageRow(appInfo, usage.minutes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageRow(appInfo: AppInfo, minutes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconModifier = Modifier
            .size(40.dp)
            .semantics { contentDescription = "${appInfo.label} icon" }
        if (appInfo.icon != null) {
            val painter = remember(appInfo.icon) {
                BitmapPainter(appInfo.icon.toBitmap().asImageBitmap())
            }
            Image(painter = painter, contentDescription = null, modifier = iconModifier)
        } else {
            Box(
                modifier = iconModifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Text(appInfo.label.take(1))
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(appInfo.label, modifier = Modifier.weight(1f))
        Text("$minutes min")
    }
}
