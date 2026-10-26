package tw.fann.bp.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test

class QrCodeTest {

    @Test
    fun encodedQr_decodesBackToSameUrl() {
        val url = "https://github.com/fanntone/blood-pressure/releases/latest/download/blood-pressure.apk"
        val m = QrCode.matrix(url)

        // 每個模組放大成 4×4 像素再交給讀碼器，模擬實際顯示
        val scale = 4
        val w = m.width * scale; val h = m.height * scale
        val px = IntArray(w * h) { i -> if (m.get(i % w / scale, i / w / scale)) 0xFF000000.toInt() else -1 }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, px)))

        assertEquals(url, QRCodeReader().decode(bitmap).text)
    }
}
