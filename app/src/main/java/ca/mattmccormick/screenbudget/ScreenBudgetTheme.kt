package ca.mattmccormick.screenbudget

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Forest teal, sage hills, and sunrise gold; lighter tones keep dark mode readable.
private val LightColors = lightColorScheme(
    primary = Color(0xFF21675E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDECE5), onPrimaryContainer = Color(0xFF164E46),
    secondary = Color(0xFF526C47), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5ECD9), onSecondaryContainer = Color(0xFF354C2D),
    tertiary = Color(0xFF805B15), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF0CB), onTertiaryContainer = Color(0xFF65470E),
    background = Color(0xFFFFFCF4), onBackground = Color(0xFF25332E),
    surface = Color(0xFFFFFCF4), onSurface = Color(0xFF25332E),
    surfaceVariant = Color(0xFFEAEDE3), onSurfaceVariant = Color(0xFF606D64),
    surfaceContainer = Color(0xFFF5F5EC),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF9F9F0),
    surfaceContainerHigh = Color(0xFFEFEFE6), surfaceContainerHighest = Color(0xFFE9EAE0),
    surfaceBright = Color(0xFFFFFCF4), surfaceDim = Color(0xFFDDDCD3),
    surfaceTint = Color(0xFF21675E),
    inverseSurface = Color(0xFF2D3730), inverseOnSurface = Color(0xFFF5F5EC),
    inversePrimary = Color(0xFF9DD3C5),
    outline = Color(0xFF78867C), outlineVariant = Color(0xFFDDE3D7),
    error = Color(0xFFB34238), errorContainer = Color(0xFFFCEAE7),
    onErrorContainer = Color(0xFF912F27),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD3C5), onPrimary = Color(0xFF103B34),
    primaryContainer = Color(0xFF23483F), onPrimaryContainer = Color(0xFFC0EBDD),
    secondary = Color(0xFFB8CDA4), onSecondary = Color(0xFF263B1E),
    secondaryContainer = Color(0xFF354A2C), onSecondaryContainer = Color(0xFFDBEACB),
    tertiary = Color(0xFFEFC477), onTertiary = Color(0xFF432E08),
    tertiaryContainer = Color(0xFF4C3B19), onTertiaryContainer = Color(0xFFFFE2AA),
    background = Color(0xFF161E1B), onBackground = Color(0xFFE5EBE2),
    surface = Color(0xFF161E1B), onSurface = Color(0xFFE5EBE2),
    surfaceVariant = Color(0xFF2D3730), onSurfaceVariant = Color(0xFFBCC7BB),
    surfaceContainer = Color(0xFF202A24),
    surfaceContainerLowest = Color(0xFF101612), surfaceContainerLow = Color(0xFF1A231E),
    surfaceContainerHigh = Color(0xFF29332C), surfaceContainerHighest = Color(0xFF343E36),
    surfaceBright = Color(0xFF3A443C), surfaceDim = Color(0xFF161E1B),
    surfaceTint = Color(0xFF9DD3C5),
    inverseSurface = Color(0xFFE5EBE2), inverseOnSurface = Color(0xFF25332E),
    inversePrimary = Color(0xFF21675E),
    outline = Color(0xFF96A497), outlineVariant = Color(0xFF3D493F),
    error = Color(0xFFFFB4A9), errorContainer = Color(0xFF542C28),
    onErrorContainer = Color(0xFFFFDAD4),
)

@Composable
internal fun ScreenBudgetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors) {
        Surface(modifier = Modifier.fillMaxSize(), content = content)
    }
}
