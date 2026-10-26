package tw.fann.bp.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/** 讀入照片：依 EXIF 轉正、縮到長邊不超過 maxSide，並取出拍攝時間。 */
object PhotoLoader {

    const val MAX_SIDE = 2048

    class Loaded(val bitmap: Bitmap, val takenAt: Long?)

    /** open 每次呼叫都要回傳一個新的串流（需要讀三次：EXIF、尺寸、像素）。 */
    fun load(open: () -> InputStream, maxSide: Int = MAX_SIDE): Loaded {
        val exif = open().use { ExifInterface(it) }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0) { "無法讀取這張照片" }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val decoded = open().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("無法讀取這張照片")

        val rotated = rotate(decoded, exif.rotationDegrees)
        val takenAt = TimeConfig.parseExif(
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
        ) ?: TimeConfig.parseExif(
            exif.getAttribute(ExifInterface.TAG_DATETIME),
            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME),
        )
        return Loaded(scaleDown(rotated, maxSide), takenAt)
    }

    fun rotate(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun scaleDown(src: Bitmap, maxSide: Int = MAX_SIDE): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(src, (src.width * ratio).toInt(), (src.height * ratio).toInt(), true)
    }
}
