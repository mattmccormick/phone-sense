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

private val LightColors = lightColorScheme(
    primary = Color(0xFF286F56), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EBE4), onPrimaryContainer = Color(0xFF204D3B),
    secondary = Color(0xFF48769A), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EDF4), onSecondaryContainer = Color(0xFF254A67),
    tertiary = Color(0xFF946214), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBF1DC), onTertiaryContainer = Color(0xFF79500E),
    background = Color.White, onBackground = Color(0xFF24332B),
    surface = Color.White, onSurface = Color(0xFF24332B),
    surfaceVariant = Color(0xFFECF0EC), onSurfaceVariant = Color(0xFF626E66),
    surfaceContainer = Color(0xFFF5F7F5),
    outline = Color(0xFF7E8981), outlineVariant = Color(0xFFE1E6E2),
    error = Color(0xFFB34238), errorContainer = Color(0xFFFCEAE7),
    onErrorContainer = Color(0xFF912F27),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA1CFB6), onPrimary = Color(0xFF153B2A),
    primaryContainer = Color(0xFF293E33), onPrimaryContainer = Color(0xFFC5E9D4),
    secondary = Color(0xFF9DC6E6), onSecondary = Color(0xFF17354C),
    secondaryContainer = Color(0xFF293C4B), onSecondaryContainer = Color(0xFFD3E8F8),
    tertiary = Color(0xFFE5BA70), onTertiary = Color(0xFF412C09),
    tertiaryContainer = Color(0xFF44351C), onTertiaryContainer = Color(0xFFF1CD8E),
    background = Color(0xFF161D19), onBackground = Color(0xFFE3EBE5),
    surface = Color(0xFF161D19), onSurface = Color(0xFFE3EBE5),
    surfaceVariant = Color(0xFF2B352E), onSurfaceVariant = Color(0xFFB4BFB6),
    surfaceContainer = Color(0xFF1E2821),
    outline = Color(0xFF9BA79E), outlineVariant = Color(0xFF364139),
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
