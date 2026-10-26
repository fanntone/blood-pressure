package tw.fann.bp.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tw.fann.bp.data.BpRules
import tw.fann.bp.data.Reading

/** 自動辨識成功、已存檔後的結果畫面。 */
@Composable
fun SavedScreen(reading: Reading, onEdit: () -> Unit, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("✅ 已經存好了", style = MaterialTheme.typography.headlineLarge, color = Color(0xFF0A7A0A))
        Text(
            formatDateTime(reading.measuredAt),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ValueLine("高壓（收縮壓）", reading.systolic.toString())
                ValueLine("低壓（舒張壓）", reading.diastolic.toString())
                ValueLine("脈搏", reading.pulse?.toString() ?: "—")
            }
        }
        Text(
            "請對一下血壓計上的數字，不一樣就按「修改」。",
            style = MaterialTheme.typography.bodyLarge,
        )
        BigButton("完成", onDone, height = 96.dp)
        BigButton("修改數字", onEdit, outlined = true)
    }
}

@Composable
private fun ValueLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.displayMedium)
    }
}

/** 讀不到數字：請使用者重拍。 */
@Composable
fun RetryScreen(photo: Bitmap, onRetake: () -> Unit, onHome: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("沒有讀到數字", style = MaterialTheme.typography.headlineLarge, color = Color(0xFFB3261E))
        Image(
            photo.asImageBitmap(), contentDescription = "剛剛拍的照片",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Text("請再拍一次，拍的時候：", style = MaterialTheme.typography.titleLarge)
        Text(
            "・手機正對血壓計螢幕，不要斜斜的\n・趁螢幕綠光還亮著時拍（量完馬上拍）\n・避開燈光反光\n・靠近一點，讓螢幕佔大半個框",
            style = MaterialTheme.typography.bodyLarge,
        )
        BigButton("📷  重拍", onRetake, height = 120.dp)
        BigButton("回首頁", onHome, outlined = true)
    }
}

/** 辨識有誤時修正數值（時間、照片不變）。 */
@Composable
fun EditScreen(
    reading: Reading,
    onSave: (Int, Int, Int?) -> Unit,
    onCancel: () -> Unit,
) {
    var sys by rememberSaveable { mutableStateOf(reading.systolic.toString()) }
    var dia by rememberSaveable { mutableStateOf(reading.diastolic.toString()) }
    var pulse by rememberSaveable { mutableStateOf(reading.pulse?.toString() ?: "") }
    val photo: Bitmap? = remember(reading) { reading.photoPath?.let { BitmapFactory.decodeFile(it) } }

    val pulseValue = pulse.toIntOrNull()
    val error = BpRules.validate(sys.toIntOrNull(), dia.toIntOrNull(), pulseValue)
        ?: if (pulse.isNotBlank() && pulseValue == null) "脈搏請輸入數字" else null

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("對照照片修改數字", style = MaterialTheme.typography.headlineMedium)
        Text(
            formatDateTime(reading.measuredAt),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        photo?.let {
            Image(
                it.asImageBitmap(), contentDescription = "血壓計照片",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(240.dp),
            )
        }

        NumberField("高壓（收縮壓）", sys, { sys = it }, default = reading.systolic)
        NumberField("低壓（舒張壓）", dia, { dia = it }, default = reading.diastolic)
        NumberField("脈搏", pulse, { pulse = it }, default = reading.pulse ?: 70)

        val s = sys.toIntOrNull(); val d = dia.toIntOrNull()
        if (error != null) {
            Text(error, color = Color(0xFFB3261E), style = MaterialTheme.typography.titleMedium)
        }

        BigButton("儲存", { onSave(s!!, d!!, pulseValue) }, height = 96.dp, enabled = error == null)
        BigButton("取消", onCancel, outlined = true)
    }
}

/** 大字數字欄：可直接打字，也可按 －／＋（按住會連續變動）。 */
@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, default: Int) {
    val current by rememberUpdatedState(value)
    fun step(delta: Int) {
        val v = current.toIntOrNull() ?: default
        onChange((v + delta).coerceIn(0, 300).toString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RepeatButton("－") { step(-1) }
            OutlinedTextField(
                value = value,
                onValueChange = { t -> onChange(t.filter { it.isDigit() }.take(3)) },
                textStyle = MaterialTheme.typography.displayMedium.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            RepeatButton("＋") { step(1) }
        }
    }
}

@Composable
private fun RepeatButton(text: String, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val action by rememberUpdatedState(onStep)
    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        action()
        delay(450)
        while (true) {
            action()
            delay(90)
        }
    }
    FilledTonalButton(
        onClick = {},
        interactionSource = interaction,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.size(76.dp),
    ) { Text(text, style = MaterialTheme.typography.headlineLarge) }
}
