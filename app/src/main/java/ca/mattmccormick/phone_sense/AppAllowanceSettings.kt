package ca.mattmccormick.phone_sense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.mattmccormick.phone_sense.data.AppRule
import ca.mattmccormick.phone_sense.data.AppRuleDao
import ca.mattmccormick.phone_sense.data.UsageDao
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AppAllowanceSettings(
    usageDao: UsageDao,
    appRuleDao: AppRuleDao,
    appInfoSource: AppInfoSource,
    onBack: () -> Unit,
    readCurrentDay: (ZoneId) -> CurrentDayUsageSnapshotResult = { CurrentDayUsageSnapshotResult.Unavailable },
) {
    var apps by remember { mutableStateOf<List<AppRule>?>(null) }
    var appInfos by remember { mutableStateOf(emptyMap<String, AppInfo>()) }
    var saving by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(usageDao, appRuleDao, appInfoSource) {
        val resolvedInfos = mutableMapOf<String, AppInfo>()
        apps = withContext(Dispatchers.IO) {
            val rules = appRuleDao.all().associateBy { it.appKey }
            val live = (readCurrentDay(ZoneId.systemDefault()) as? CurrentDayUsageSnapshotResult.Available)
                ?.snapshot?.perPackageMillis?.keys.orEmpty()
            (usageDao.appKeys() + rules.keys + live).distinct().map { key ->
                val info = if (key.startsWith("label:")) AppInfo(key.removePrefix("label:"), null)
                    else appInfoSource.resolve(key)
                val label = if (key.startsWith("label:")) key.removePrefix("label:") else {
                    info.label.let { resolved ->
                        if (resolved == key) rules[key]?.label ?: resolved else resolved
                    }
                }
                resolvedInfos[key] = info.copy(label = label)
                AppRule(key, label, rules[key]?.excluded ?: false)
            }.sortedBy { it.label.lowercase() }
        }
        appInfos = resolvedInfos
    }
    SettingsPage("Apps counted", onBack, scrollContent = false) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Switch on to count an app toward your daily allowance.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                apps == null -> Text("Loading apps…")
                apps!!.isEmpty() -> Text("Apps will appear here once usage is recorded.")
                else -> LazyColumn(Modifier.weight(1f)) {
                    items(apps!!, key = { it.appKey }) { app ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(appInfos[app.appKey] ?: AppInfo(app.label, null))
                            Text(app.label, Modifier.weight(1f))
                            Switch(
                                checked = !app.excluded,
                                enabled = app.appKey !in saving,
                                modifier = Modifier.semantics { contentDescription = "Count ${app.label} toward allowance" },
                                onCheckedChange = { included ->
                                    saving = saving + app.appKey
                                    error = null
                                    scope.launch {
                                        val updated = app.copy(excluded = !included)
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching { appRuleDao.insert(updated) }
                                        }
                                        if (result.isSuccess) {
                                            apps = apps?.map { if (it.appKey == app.appKey) updated else it }
                                        } else error = "Could not save this choice. Please try again."
                                        saving = saving - app.appKey
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
