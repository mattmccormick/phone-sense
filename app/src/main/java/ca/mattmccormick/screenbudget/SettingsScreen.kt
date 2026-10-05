package ca.mattmccormick.screenbudget

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ca.mattmccormick.screenbudget.data.Settings
import ca.mattmccormick.screenbudget.export.ImportStatus

@Composable
internal fun SettingsScreen(
    hasUsageAccess: Boolean,
    notificationsEnabled: Boolean,
    onBack: () -> Unit = {},
    openUsageSettings: () -> Unit = {},
    openNotificationSettings: () -> Unit = {},
    onData: () -> Unit = {},
    onAbout: () -> Unit = {},
    onAppAllowances: (() -> Unit)? = null,
) {
    SettingsPage(stringResource(R.string.settings), onBack) {
        onAppAllowances?.let { SettingsRow("Apps counted", "Choose which apps count toward your allowance", it) }
        SettingsRow("Usage access", if (hasUsageAccess) "Allowed" else "Access needed", openUsageSettings)
        SettingsRow("Notifications", if (notificationsEnabled) "On" else "Off · turn on for goal reminders", openNotificationSettings)
        SettingsRow("Data", "Import, export and refresh usage", onData)
        SettingsRow(stringResource(R.string.about), onClick = onAbout)
    }
}

@Composable
internal fun HomeWithSettings(
    settings: Settings,
    hasUsageAccess: Boolean = true,
    notificationsEnabled: Boolean = true,
    collectNow: () -> CollectResult = { CollectResult.NothingToDo },
    openUsageSettings: () -> Unit = {},
    openNotificationSettings: () -> Unit = {},
    launchExport: (Intent) -> Unit = {},
    exportError: String? = null,
    openImportDocument: () -> Unit = {},
    importStatus: ImportStatus? = null,
    appSettings: (@Composable (onBack: () -> Unit) -> Unit)? = null,
    mainContent: @Composable (Settings) -> Unit,
) {
    var page by rememberSaveable { mutableStateOf("home") }
    val stateHolder = rememberSaveableStateHolder()
    stateHolder.SaveableStateProvider(page) {
        when (page) {
            "apps" -> appSettings?.invoke { page = "settings" }
            "about" -> AboutScreen(onBack = { page = "settings" })
            "data" -> DataSettingsScreen(
                onBack = { page = "settings" },
                collectNow = collectNow,
                launchExport = launchExport,
                exportError = exportError,
                openImportDocument = openImportDocument,
                importStatus = importStatus,
            )
            "settings" -> SettingsScreen(
                hasUsageAccess = hasUsageAccess,
                notificationsEnabled = notificationsEnabled,
                onBack = { page = "home" },
                openUsageSettings = openUsageSettings,
                openNotificationSettings = openNotificationSettings,
                onData = { page = "data" },
                onAbout = { page = "about" },
                onAppAllowances = if (appSettings != null) ({ page = "apps" }) else null,
            )
            else -> Column(modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                TextButton(onClick = { page = "settings" }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.settings))
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) { mainContent(settings) }
            }
        }
    }
}
