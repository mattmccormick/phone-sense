package ca.mattmccormick.screenbudget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

internal data class DependencyLicense(
    val dependency: String,
    val license: String,
)

internal val dependencyLicenses = listOf(
    DependencyLicense("AndroidX Activity", "Apache License 2.0"),
    DependencyLicense("AndroidX Compose", "Apache License 2.0"),
    DependencyLicense("AndroidX Core", "Apache License 2.0"),
    DependencyLicense("AndroidX DataStore", "Apache License 2.0"),
    DependencyLicense("AndroidX Lifecycle", "Apache License 2.0"),
    DependencyLicense("AndroidX Room", "Apache License 2.0"),
    DependencyLicense("AndroidX WorkManager", "Apache License 2.0"),
    DependencyLicense("Guava ListenableFuture", "Apache License 2.0"),
    DependencyLicense("JetBrains Annotations", "Apache License 2.0"),
    DependencyLicense("JSpecify", "Apache License 2.0"),
    DependencyLicense("Kotlin", "Apache License 2.0"),
    DependencyLicense("Kotlinx Coroutines", "Apache License 2.0"),
    DependencyLicense("Kotlinx Serialization", "Apache License 2.0"),
    DependencyLicense("Okio", "Apache License 2.0"),
    DependencyLicense("Protocol Buffers", "BSD 3-Clause License"),
)

@Composable
internal fun AboutScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(stringResource(R.string.about), onBack, modifier = modifier) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.app_license))
            Text(
                stringResource(R.string.dependency_licenses),
                style = MaterialTheme.typography.titleMedium,
            )
            dependencyLicenses.forEach { dependency ->
                Text("${dependency.dependency}: ${dependency.license}")
            }
        }
    }
}
