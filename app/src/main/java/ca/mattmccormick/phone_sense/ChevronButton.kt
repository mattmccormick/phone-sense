package ca.mattmccormick.phone_sense

import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// The glyph mirrors in a right-to-left layout, so back always points at the start edge.
@Composable
internal fun ChevronButton(
    back: Boolean,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    fontSize: TextUnit = 32.sp,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    IconButton(onClick, enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description }) {
        Text(if (back != rtl) "‹" else "›", fontSize = fontSize)
    }
}
