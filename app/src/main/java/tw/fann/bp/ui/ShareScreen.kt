package tw.fann.bp.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.fann.bp.R
import tw.fann.bp.share.QrCode

/** 分享這個 App：對方用手機相機掃 QR code 直接下載安裝檔。 */
@Composable
fun ShareScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val url = stringResource(R.string.share_url)
    val matrix = remember(url) { QrCode.matrix(url) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("分享這個 App", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.fillMaxWidth())
        Text("請對方用手機相機掃這個 QR code，就會下載安裝檔。",
            style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())

        Box(
            Modifier.fillMaxWidth(0.85f).aspectRatio(1f)
                .background(Color.White, RoundedCornerShape(16.dp)).padding(8.dp),
        ) {
            // 一格一格畫，放大後仍然清晰
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.minDimension / matrix.width
                for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                    if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }

        Text(
            "安裝時手機會問「是否允許安裝未知來源的應用程式」，按允許即可。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        BigButton("📤  傳送下載連結", {
            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "量血壓 App（拍照記錄血壓）下載：$url")
            context.startActivity(Intent.createChooser(send, "傳送下載連結"))
        })
        BigButton("返回", onBack, outlined = true)
    }
}
