package ca.mattmccormick.phone_sense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
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
        val uriHandler = LocalUriHandler.current
        val privacyContactEmail = stringResource(R.string.privacy_contact_email)
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.app_license))
            Text(stringResource(R.string.privacy_policy), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_policy_intro))
            Text(stringResource(R.string.privacy_policy_usage_access))
            Text(stringResource(R.string.privacy_policy_export))
            Text(stringResource(R.string.privacy_policy_deletion))
            TextButton(onClick = {
                uriHandler.openUri("https://github.com/mattmccormick/phone-sense/blob/main/PRIVACY.md")
            }) {
                Text(stringResource(R.string.privacy_policy_public_link))
            }
            Text(stringResource(R.string.privacy_contact), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.privacy_contact_prompt))
            TextButton(onClick = {
                uriHandler.openUri("mailto:$privacyContactEmail")
            }) {
                Text(stringResource(R.string.privacy_contact_email))
            }
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
