package tw.fann.bp.data

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** 時區只在這裡決定：一律台灣時間（UTC+8），不跟著手機設定跑。 */
object TimeConfig {
    val ZONE: ZoneId = ZoneId.of("Asia/Taipei")

    private val EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    /**
     * EXIF 拍攝時間 → epoch 毫秒。照片有記錄時差（如 "+08:00"）就照它算，
     * 沒有就當成台灣時間。格式不對回傳 null。
     */
    fun parseExif(dateTime: String?, offset: String?): Long? {
        if (dateTime.isNullOrBlank()) return null
        val local = try {
            LocalDateTime.parse(dateTime.trim(), EXIF_FORMAT)
        } catch (e: DateTimeParseException) {
            return null
        }
        val zoned = offset?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { local.atOffset(ZoneOffset.of(it)) }.getOrNull()?.toInstant() }
            ?: local.atZone(ZONE).toInstant()
        return zoned.toEpochMilli()
    }
}
