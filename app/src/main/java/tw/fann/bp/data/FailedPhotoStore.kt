package tw.fann.bp.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/**
 * 讀不到數字的照片留存，供之後分析、改進辨識用。不進資料庫、不出現在報告。
 * 只保留最近 [keep] 張。
 */
class FailedPhotoStore(
    context: Context,
    private val dir: File = File(context.filesDir, "failed"),
    private val keep: Int = 30,
) {
    fun save(photo: Bitmap, takenAt: Long): File {
        dir.mkdirs()
        val file = File(dir, "failed_$takenAt.jpg")
        file.outputStream().use { out ->
            check(photo.compress(Bitmap.CompressFormat.JPEG, 92, out)) { "照片寫入失敗：$file" }
        }
        list().drop(keep).forEach { it.delete() }
        return file
    }

    /** 由新到舊 */
    fun list(): List<File> =
        dir.listFiles { f -> f.name.startsWith("failed_") }.orEmpty().sortedByDescending { it.name.removePrefix("failed_").removeSuffix(".jpg").toLongOrNull() ?: 0 }
}
