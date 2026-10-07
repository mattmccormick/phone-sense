package ca.mattmccormick.phone_sense

import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun dayDateHeading(date: LocalDate, today: LocalDate, locale: Locale): String {
    if (date == today || date == today.minusDays(1)) {
        val formatter = RelativeDateTimeFormatter.getInstance(
            ULocale.forLocale(locale), null, RelativeDateTimeFormatter.Style.LONG,
            DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE,
        )
        val direction = if (date == today) RelativeDateTimeFormatter.Direction.THIS else RelativeDateTimeFormatter.Direction.LAST
        return formatter.format(direction, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
    }
    val pattern = DateFormat.getBestDateTimePattern(locale, "EEE MMM d yyyy")
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

@Composable
internal fun DayDateHeading(date: LocalDate, today: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onDateChange(date.minusDays(1)) },
            modifier = Modifier.semantics { contentDescription = "Previous day" }) {
            Text(if (rtl) "›" else "‹", fontSize = 32.sp)
        }
        Text(dayDateHeading(date, today, locale), Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        IconButton(onClick = { onDateChange(date.plusDays(1)) }, enabled = date < today,
            modifier = Modifier.semantics { contentDescription = "Next day" }) {
            Text(if (rtl) "‹" else "›", fontSize = 32.sp)
        }
    }
}
