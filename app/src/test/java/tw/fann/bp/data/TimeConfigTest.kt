package tw.fann.bp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeConfigTest {

    // 黃金值由 Python datetime 獨立算出：2026-10-26 11:19:41 +08:00
    private val taipei = 1_792_984_781_000L

    @Test
    fun exifWithOffset_usesThatOffset() {
        assertEquals(taipei, TimeConfig.parseExif("2026:10:26 11:19:41", "+08:00"))
        // 同樣的時鐘時間在 +09:00 → 早一小時（Python 算出 1792981181000）
        assertEquals(1_792_981_181_000L, TimeConfig.parseExif("2026:10:26 11:19:41", "+09:00"))
    }

    @Test
    fun exifWithoutOffset_isTaiwanTime_notUtc() {
        assertEquals(taipei, TimeConfig.parseExif("2026:10:26 11:19:41", null))
        assertEquals(taipei, TimeConfig.parseExif("2026:10:26 11:19:41", ""))
    }

    @Test
    fun badExif_returnsNull() {
        assertNull(TimeConfig.parseExif(null, null))
        assertNull(TimeConfig.parseExif("    :  :     :  :  ", null))
        assertNull(TimeConfig.parseExif("2026-10-26", null))
    }
}
