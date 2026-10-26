package tw.fann.bp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.fann.bp.data.BpRules
import tw.fann.bp.data.Reading
import kotlin.math.abs

/**
 * 收縮壓 / 舒張壓趨勢圖：單一 y 軸、虛線標出高血壓門檻、點一下看該筆數值。
 * readings 由新到舊傳入。
 */
@Composable
fun BpChart(readings: List<Reading>, modifier: Modifier = Modifier, chartHeight: Dp = 300.dp) {
    val points = remember(readings) { readings.sortedBy { it.measuredAt } }
    var selected by remember(readings) { mutableStateOf<Int?>(null) }
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(fontSize = 16.sp, color = BpColors.TextSecondary)
    val labelStyle = TextStyle(fontSize = 16.sp, color = BpColors.TextPrimary)
    val tipStyle = TextStyle(fontSize = 18.sp, color = BpColors.TextPrimary)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Legend(BpColors.Systolic, "高壓（收縮壓）")
        Legend(BpColors.Diastolic, "低壓（舒張壓）")
        Text("虛線：高血壓門檻 ${BpRules.STAGE1_SYS} / ${BpRules.STAGE1_DIA}　點圖看數值",
            style = MaterialTheme.typography.labelMedium, color = BpColors.TextSecondary)

        Box(Modifier.fillMaxWidth().height(chartHeight)) {
            if (points.isEmpty()) {
                Text("這段期間沒有紀錄", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.Center))
                return@Box
            }
            val lo = (minOf(points.minOf { it.diastolic }, BpRules.STAGE1_DIA) - 10) / 20 * 20
            val hi = (maxOf(points.maxOf { it.systolic }, BpRules.STAGE1_SYS) + 29) / 20 * 20
            val t0 = points.first().measuredAt; val t1 = points.last().measuredAt
            val maxIndex = points.indices.maxBy { points[it].systolic }

            Canvas(
                Modifier.fillMaxWidth().height(chartHeight).pointerInput(points) {
                    detectTapGestures { tap ->
                        val left = LEFT_PAD.toPx(); val right = size.width - RIGHT_PAD.toPx()
                        val xs = points.map { xOf(it.measuredAt, t0, t1, left, right) }
                        val nearest = xs.indices.minByOrNull { abs(xs[it] - tap.x) }
                        selected = if (nearest == selected) null else nearest
                    }
                }
            ) {
                val left = LEFT_PAD.toPx(); val right = size.width - RIGHT_PAD.toPx()
                val top = 28.dp.toPx(); val bottom = size.height - 32.dp.toPx()
                fun y(v: Int) = bottom - (v - lo).toFloat() / (hi - lo) * (bottom - top)
                fun x(t: Long) = xOf(t, t0, t1, left, right)

                // 格線與 y 軸刻度
                for (v in lo..hi step 20) {
                    drawLine(BpColors.Grid, Offset(left, y(v)), Offset(right, y(v)), 1.dp.toPx())
                    val tl = measurer.measure(v.toString(), axisStyle)
                    drawText(tl, topLeft = Offset(left - tl.size.width - 8.dp.toPx(), y(v) - tl.size.height / 2))
                }
                // 門檻虛線
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                for (v in listOf(BpRules.STAGE1_SYS, BpRules.STAGE1_DIA)) {
                    drawLine(BpColors.TextSecondary, Offset(left, y(v)), Offset(right, y(v)), 1.5.dp.toPx(), pathEffect = dash)
                }
                // x 軸日期：依寬度決定刻度數，標籤之間至少留半個標籤寬
                val labelW = measurer.measure("10/30", axisStyle).size.width
                val gaps = ((right - left) / (labelW * 1.6f)).toInt().coerceIn(1, 6)
                val ticks = if (t1 == t0) listOf(t0) else (0..gaps).map { t0 + (t1 - t0) * it / gaps }
                for (t in ticks) {
                    val tl = measurer.measure(formatShortDate(t), axisStyle)
                    val tx = (x(t) - tl.size.width / 2).coerceIn(left - 8.dp.toPx(), right - tl.size.width.toFloat())
                    drawLine(BpColors.Grid, Offset(x(t), bottom), Offset(x(t), bottom + 4.dp.toPx()), 1.dp.toPx())
                    drawText(tl, topLeft = Offset(tx, bottom + 6.dp.toPx()))
                }

                // 點太密時只畫線，避免糊成一團
                val markers = points.size < 2 || (right - left) / (points.size - 1) >= 12.dp.toPx()
                series(points.map { Offset(x(it.measuredAt), y(it.systolic)) }, BpColors.Systolic, markers)
                series(points.map { Offset(x(it.measuredAt), y(it.diastolic)) }, BpColors.Diastolic, markers)

                // 線尾直接標最新數值（文字用文字色，顏色由旁邊的線條表示）
                val end = points.last()
                val sysLabel = measurer.measure("高 ${end.systolic}", labelStyle)
                val diaLabel = measurer.measure("低 ${end.diastolic}", labelStyle)
                drawText(sysLabel, topLeft = Offset(right + 10.dp.toPx(), y(end.systolic) - sysLabel.size.height / 2))
                drawText(diaLabel, topLeft = Offset(right + 10.dp.toPx(), y(end.diastolic) - diaLabel.size.height / 2))

                // 期間最高點
                if (points.size > 1 && maxIndex != points.lastIndex) {
                    val m = points[maxIndex]
                    val ml = measurer.measure("最高 ${m.systolic}", labelStyle)
                    val mx = (x(m.measuredAt) - ml.size.width / 2).coerceIn(left, right - ml.size.width)
                    drawText(ml, topLeft = Offset(mx, y(m.systolic) - ml.size.height - 8.dp.toPx()))
                }

                selected?.let { i ->
                    val r = points[i]
                    val px = x(r.measuredAt)
                    drawLine(BpColors.TextSecondary, Offset(px, top), Offset(px, bottom), 1.dp.toPx())
                    val tip = measurer.measure(
                        "${formatDateTime(r.measuredAt)}\n高壓 ${r.systolic}　低壓 ${r.diastolic}" +
                            (r.pulse?.let { "\n脈搏 $it" } ?: ""),
                        tipStyle,
                    )
                    val pad = 10.dp.toPx()
                    val w = tip.size.width + pad * 2; val h = tip.size.height + pad * 2
                    val tx = (px - w / 2).coerceIn(0f, size.width - w)
                    drawRoundRect(Color.White, Offset(tx, 0f), Size(w, h), CornerRadius(12.dp.toPx()))
                    drawRoundRect(BpColors.TextSecondary, Offset(tx, 0f), Size(w, h), CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx()))
                    drawText(tip, topLeft = Offset(tx + pad, pad))
                }
            }
        }
    }
}

private val LEFT_PAD = 44.dp
private val RIGHT_PAD = 64.dp

private fun xOf(t: Long, t0: Long, t1: Long, left: Float, right: Float): Float =
    if (t1 == t0) (left + right) / 2 else left + (t - t0).toFloat() / (t1 - t0) * (right - left)

private fun DrawScope.series(pts: List<Offset>, color: Color, markers: Boolean) {
    if (pts.size > 1) {
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        drawPath(path, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    if (!markers) return
    // 標記點：外圈留一圈底色，重疊時仍分得開
    for (p in pts) {
        drawCircle(BpColors.Surface, 6.dp.toPx(), p)
        drawCircle(color, 4.5.dp.toPx(), p)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(14.dp).background(color, CircleShape))
        Text(label, style = MaterialTheme.typography.labelMedium, color = BpColors.TextPrimary)
    }
}
