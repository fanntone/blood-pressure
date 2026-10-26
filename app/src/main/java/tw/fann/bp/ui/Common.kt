package tw.fann.bp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.Instant
import tw.fann.bp.data.TimeConfig
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormat = DateTimeFormatter.ofPattern("M/d EEE HH:mm", Locale.TAIWAN)
private val shortDateFormat = DateTimeFormatter.ofPattern("M/d", Locale.TAIWAN)

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(TimeConfig.ZONE).format(dateTimeFormat)

fun formatShortDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(TimeConfig.ZONE).format(shortDateFormat)

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 80.dp,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val content: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
    val m = modifier.fillMaxWidth().heightIn(min = height)
    val shape = RoundedCornerShape(20.dp)
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = m, enabled = enabled, shape = shape) { content() }
    } else {
        Button(
            onClick = onClick, modifier = m, enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = BpColors.Primary),
        ) { content() }
    }
}

/** 大字的單選切換列。 */
@Composable
fun <T> Choice(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
                modifier = Modifier.heightIn(min = 60.dp),
            ) { Text(label, style = MaterialTheme.typography.titleMedium) }
        }
    }
}
