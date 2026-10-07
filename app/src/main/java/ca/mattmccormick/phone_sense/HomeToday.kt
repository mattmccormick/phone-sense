package ca.mattmccormick.phone_sense

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeToday(used: Int?, allowance: Int?) {
    val colors = MaterialTheme.colorScheme
    val over = used != null && allowance != null && used > allowance
    val near = used != null && allowance != null && used.toLong() * 5 >= allowance.toLong() * 4
    val color = when { over -> colors.error; near -> colors.tertiary; else -> colors.primary }
    val status = when {
        used == null -> "Usage unavailable"
        allowance == null -> "Set a goal for an allowance"
        over -> "Over your allowance"
        used == allowance -> "Allowance reached"
        near -> "Near your allowance"
        else -> "Within your allowance"
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today", style = MaterialTheme.typography.titleLarge)
            Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(20.dp)) {
                Text(status, Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = color)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(used?.toString() ?: "—", fontSize = 64.sp, lineHeight = 72.sp,
                modifier = Modifier.semantics { contentDescription = used?.let { "$it minutes counted today" } ?: "Today's usage unavailable" })
            Text("min counted", Modifier.padding(bottom = 12.dp), color = colors.onSurfaceVariant)
        }
        Text("Toward your allowance · selected apps excluded", style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant)
        if (used != null && allowance != null) {
            Canvas(Modifier.fillMaxWidth().height(24.dp).semantics {
                contentDescription = "$used of $allowance minutes daily allowance used"
            }) {
                val inset = 4.dp.toPx()
                val width = size.width - inset * 2
                val scale = maxOf(allowance * 1.2f, used.toFloat(), 1f)
                val marker = inset + width * allowance / scale
                drawLine(colors.surfaceVariant, Offset(inset, center.y), Offset(size.width - inset, center.y),
                    strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                if (used > 0) drawLine(color, Offset(inset, center.y), Offset(inset + width * used / scale, center.y),
                    strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
                drawLine(colors.onSurface, Offset(marker, 3.dp.toPx()), Offset(marker, size.height - 3.dp.toPx()),
                    strokeWidth = 1.5.dp.toPx())
            }
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (over) "${used - allowance} min over today" else "${allowance - used} min left today",
                    color = color, style = MaterialTheme.typography.titleSmall)
                Text("$allowance min allowance", color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
