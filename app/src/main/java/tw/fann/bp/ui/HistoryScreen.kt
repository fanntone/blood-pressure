package tw.fann.bp.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import tw.fann.bp.data.BpRules
import tw.fann.bp.data.DayRow
import tw.fann.bp.data.Reading
import tw.fann.bp.data.ReportBuilder
import tw.fann.bp.data.Stats
import tw.fann.bp.data.TimeConfig
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RANGES = listOf(7 to "7 天", 30 to "30 天", 90 to "90 天")
private val dayFormat = DateTimeFormatter.ofPattern("M/d\nEEE", Locale.TAIWAN)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.TAIWAN)

private fun formatTime(millis: Long) = Instant.ofEpochMilli(millis).atZone(TimeConfig.ZONE).format(timeFormat)

/** 紀錄與報告：預設 30 天，回診時直接給醫師看。 */
@Composable
fun HistoryScreen(
    readings: List<Reading>,
    onEdit: (Reading) -> Unit,
    onDelete: (Reading) -> Unit,
    onBack: () -> Unit,
) {
    var days by rememberSaveable { mutableStateOf(30) }
    var opened by remember { mutableStateOf<Reading?>(null) }
    val now = System.currentTimeMillis()
    val from = now - days * 24L * 3600 * 1000
    val shown = readings.filter { it.measuredAt >= from }
    val report = remember(shown) { ReportBuilder.build(shown) }

    LazyColumn(
        Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text("← 返回", style = MaterialTheme.typography.labelLarge)
                }
                Text("  紀錄與報告", style = MaterialTheme.typography.headlineMedium)
            }
        }
        item {
            Choice(RANGES, days) { days = it }
            Text(
                "${formatShortDate(from)} ～ ${formatShortDate(now)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item { SummaryCard(report.overall) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("血壓變化", style = MaterialTheme.typography.titleLarge)
                    BpChart(shown)
                }
            }
        }
        item {
            Text("每日血壓表", style = MaterialTheme.typography.titleLarge)
            Text("點一筆可以看照片、修改或刪除", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { TableHeader() }
        items(report.days, key = { it.date.toEpochDay() }) { day -> DayLine(day) { opened = it } }
        item { Text("") }
    }

    opened?.let { r ->
        ReadingDialog(
            reading = r,
            onEdit = { opened = null; onEdit(r) },
            onDelete = { opened = null; onDelete(r) },
            onClose = { opened = null },
        )
    }
}

@Composable
private fun SummaryCard(all: Stats?) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (all == null) {
                Text("這段期間還沒有紀錄", style = MaterialTheme.typography.titleLarge)
                return@Column
            }
            AvgLine("平均（${all.count} 次）", all)
            HorizontalDivider()
            Text("最高 ${all.maxSystolic} / ${all.maxDiastolic}", style = MaterialTheme.typography.bodyLarge)
            Text("最低 ${all.minSystolic} / ${all.minDiastolic}", style = MaterialTheme.typography.bodyLarge)
            Text("超過 ${BpRules.STAGE1_SYS}/${BpRules.STAGE1_DIA}：${all.highCount} / ${all.count} 次",
                style = MaterialTheme.typography.bodyLarge)
            all.avgPulse?.let { Text("平均脈搏 $it", style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

@Composable
private fun AvgLine(label: String, s: Stats?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(s?.let { "${it.avgSystolic} / ${it.avgDiastolic}" } ?: "—", style = MaterialTheme.typography.headlineMedium)
    }
}

private val DATE_COL = 64.dp

@Composable
private fun TableHeader() {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("日期", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(DATE_COL))
            Text("時間", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(TIME_COL))
            Text("高壓 / 低壓", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("脈搏", style = MaterialTheme.typography.titleMedium)
        }
        HorizontalDivider(thickness = 2.dp)
    }
}

private val TIME_COL = 72.dp

@Composable
private fun DayLine(day: DayRow, onOpen: (Reading) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Text(day.date.format(dayFormat), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(DATE_COL))
            Column(Modifier.weight(1f)) {
                for (r in day.readings) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onOpen(r) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(formatTime(r.measuredAt), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(TIME_COL))
                        Text("${r.systolic} / ${r.diastolic}", style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f))
                        Text(r.pulse?.toString() ?: "—", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun ReadingDialog(reading: Reading, onEdit: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val photo = remember(reading) { reading.photoPath?.let { BitmapFactory.decodeFile(it) } }
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text(
                if (confirmDelete) "確定要刪除這筆嗎？" else formatDateTime(reading.measuredAt),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                photo?.let {
                    Image(it.asImageBitmap(), "血壓計照片", contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().height(220.dp))
                }
                Text("${reading.systolic} / ${reading.diastolic}", style = MaterialTheme.typography.displayMedium)
                reading.pulse?.let { Text("脈搏 $it", style = MaterialTheme.typography.titleLarge) }
            }
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(onClick = onDelete) { Text("刪除", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB3261E)) }
            } else {
                TextButton(onClick = onClose) { Text("關閉", style = MaterialTheme.typography.labelLarge) }
            }
        },
        dismissButton = {
            if (confirmDelete) {
                TextButton(onClick = { confirmDelete = false }) { Text("不要刪", style = MaterialTheme.typography.labelLarge) }
            } else {
                Row {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("刪除", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB3261E))
                    }
                    TextButton(onClick = onEdit) { Text("修改", style = MaterialTheme.typography.labelLarge) }
                }
            }
        },
    )
}
