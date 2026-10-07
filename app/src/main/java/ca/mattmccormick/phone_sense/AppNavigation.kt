package ca.mattmccormick.phone_sense

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

internal const val EXTRA_INITIAL_DESTINATION =
    "ca.mattmccormick.phone_sense.extra.INITIAL_DESTINATION"

internal enum class AppDestination(
    val route: String,
    val label: String,
) {
    HOME("home", "Home"),
    DAY_DETAIL("day", "Day"),
    ;

    companion object {
        fun from(intent: Intent): AppDestination = entries.firstOrNull {
            it.route == intent.getStringExtra(EXTRA_INITIAL_DESTINATION)
        } ?: HOME
    }
}

@Composable
internal fun AppNavigationShell(
    initialDestination: AppDestination = AppDestination.HOME,
    home: @Composable () -> Unit = {},
    dayDetail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dayVisit by remember { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf(initialDestination.route) }
    val selectedDestination = AppDestination.entries.firstOrNull { it.route == route }
        ?: AppDestination.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().selectableGroup()) {
                    AppDestination.entries.forEach { destination ->
                        val selected = destination == selectedDestination
                        Box(
                            Modifier.weight(1f).height(56.dp).selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = {
                                    if (destination == AppDestination.DAY_DETAIL) dayVisit++
                                    route = destination.route
                                },
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(destination.label, style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedDestination) {
                AppDestination.HOME -> home()
                AppDestination.DAY_DETAIL -> key(dayVisit) { dayDetail() }
            }
        }
    }
}
