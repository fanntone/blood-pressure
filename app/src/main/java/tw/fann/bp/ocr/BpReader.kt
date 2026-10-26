package tw.fann.bp.ocr

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tw.fann.bp.data.PhotoLoader

/** 照片 → 血壓數值的唯一入口。辨識不出合理數字時回傳 null，請使用者重拍。 */
object BpReader {

    suspend fun read(photo: Bitmap): BpValues? {
        val work = PhotoLoader.scaleDown(photo)
        val pixels = IntArray(work.width * work.height)
        work.getPixels(pixels, 0, work.width, 0, 0, work.width, work.height)
        // 背光液晶的七段數字一般 OCR 讀不到，用專用解碼器（完全離線）
        return withContext(Dispatchers.Default) { SevenSegmentReader.read(pixels, work.width, work.height) }
    }
}
