package tw.fann.bp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import tw.fann.bp.data.Reading
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    readings: List<Reading>,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onHistory: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("量血壓", style = MaterialTheme.typography.headlineLarge)

        LatestCard(readings.firstOrNull())

        BigButton("📷  拍照記錄血壓", onCamera, height = 120.dp)
        BigButton("🖼  從相簿選照片", onGallery, outlined = true)

        RecentChart(readings)
        BigButton("📈  看完整紀錄與報告", onHistory, outlined = true)
        BigButton("📤  分享這個 App", onShare, outlined = true)
    }
}

@Composable
private fun LatestCard(latest: Reading?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (latest == null) {
                Text("還沒有紀錄", style = MaterialTheme.typography.titleLarge)
                Text("按下面的「拍照記錄血壓」開始第一次量測", style = MaterialTheme.typography.bodyLarge)
                return@Column
            }
            Text("最近一次", style = MaterialTheme.typography.titleMedium)
            Text(formatDateTime(latest.measuredAt), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${latest.systolic} / ${latest.diastolic}", style = MaterialTheme.typography.displayLarge)
            latest.pulse?.let { Text("脈搏 $it", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Composable
private fun RecentChart(readings: List<Reading>) {
    var days by rememberSaveable { mutableStateOf(7) }
    val since = System.currentTimeMillis() - days * 24L * 3600 * 1000
    val recent = readings.filter { it.measuredAt >= since }
    if (readings.isEmpty()) return
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice(listOf(7 to "近 7 天", 30 to "近 30 天"), days) { days = it }
            if (recent.size >= 2) {
                val sys = recent.map { it.systolic }.average().roundToInt()
                val dia = recent.map { it.diastolic }.average().roundToInt()
                Text("平均 $sys / $dia（${recent.size} 次）", style = MaterialTheme.typography.titleMedium)
            }
            BpChart(recent, chartHeight = 240.dp)
        }
    }
}
