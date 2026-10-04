package ca.mattmccormick.screenbudget

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

internal const val EXTRA_INITIAL_DESTINATION =
    "ca.mattmccormick.screenbudget.extra.INITIAL_DESTINATION"

internal enum class AppDestination(
    val route: String,
    val label: String,
) {
    HOME("home", "Home"),
    DAY_DETAIL("day", "Day"),
    GOALS("goals", "Goals"),
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
    home: @Composable (onSetGoal: () -> Unit) -> Unit = {},
    dayDetail: @Composable () -> Unit,
    goals: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var route by rememberSaveable { mutableStateOf(initialDestination.route) }
    val selectedDestination = AppDestination.entries.firstOrNull { it.route == route }
        ?: AppDestination.HOME

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == selectedDestination,
                        onClick = { route = destination.route },
                        icon = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedDestination) {
                AppDestination.HOME -> home { route = AppDestination.GOALS.route }
                AppDestination.DAY_DETAIL -> dayDetail()
                AppDestination.GOALS -> goals()
            }
        }
    }
}
