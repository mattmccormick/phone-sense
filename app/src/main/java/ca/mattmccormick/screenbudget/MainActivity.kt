package ca.mattmccormick.screenbudget

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import ca.mattmccormick.screenbudget.data.UsageDatabase

class MainActivity : ComponentActivity() {
    private lateinit var database: UsageDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = (application as ScreenBudgetApplication).database
        setContent {
            MaterialTheme {
                val usageEventsSource = remember { UsageEventsSource(this@MainActivity) }
                ScreenBudgetApp(
                    usageEventsSource = usageEventsSource,
                    launchSettings = ::startActivity,
                    mainContent = {
                        DayDetailScreen(
                            dao = database.usageDao(),
                            appRuleDao = database.appRuleDao(),
                            appInfoSource = AppInfoResolver(packageManager),
                        )
                    },
                )
            }
        }
    }
}

@Composable
internal fun ScreenBudgetApp(
    usageEventsSource: UsageEventsSource,
    launchSettings: (Intent) -> Unit,
    mainContent: @Composable () -> Unit,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    var hasUsageAccess by remember(usageEventsSource) {
        mutableStateOf(usageEventsSource.hasUsageAccess())
    }
    DisposableEffect(lifecycleOwner, usageEventsSource) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = usageEventsSource.hasUsageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasUsageAccess) {
        mainContent()
    } else {
        UsageAccessScreen(
            onAllowUsageAccess = {
                launchSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
        )
    }
}

@Composable
private fun UsageAccessScreen(
    onAllowUsageAccess: () -> Unit,
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.usage_access_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.usage_access_explanation),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(
                    onClick = onAllowUsageAccess,
                    modifier = Modifier.padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.allow_usage_access))
                }
            }
        }
    }
}

@Preview(name = "Usage access", showBackground = true, showSystemUi = true)
@Composable
private fun UsageAccessScreenPreview() {
    MaterialTheme {
        UsageAccessScreen(onAllowUsageAccess = {})
    }
}
