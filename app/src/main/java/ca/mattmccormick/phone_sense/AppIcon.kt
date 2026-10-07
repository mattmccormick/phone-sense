package ca.mattmccormick.phone_sense

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
internal fun AppIcon(appInfo: AppInfo) {
    val iconModifier = Modifier
        .size(40.dp)
        .semantics { contentDescription = "${appInfo.label} icon" }
    if (appInfo.icon != null) {
        val painter = remember(appInfo.icon) {
            BitmapPainter(appInfo.icon.toBitmap().asImageBitmap())
        }
        Image(painter = painter, contentDescription = null, modifier = iconModifier)
    } else {
        Box(
            modifier = iconModifier.background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(appInfo.label.take(1))
        }
    }
}
