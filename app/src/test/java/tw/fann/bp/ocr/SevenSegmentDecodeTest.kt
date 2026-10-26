package tw.fann.bp.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SevenSegmentDecodeTest {

    @Test
    fun exactPatterns() {
        val expected = mapOf(
            "abcdef" to 0, "bc" to 1, "abdeg" to 2, "abcdg" to 3, "bcfg" to 4,
            "acdfg" to 5, "acdefg" to 6, "abc" to 7, "abcdefg" to 8, "abcdfg" to 9,
        )
        expected.forEach { (segs, digit) -> assertEquals(segs, digit, SevenSegmentReader.decodeSegments(segs)) }
    }

    @Test
    fun oneSegmentMissing_uniqueNearest_isAccepted() {
        // 反光讓「0」的底部筆畫消失
        assertEquals(0, SevenSegmentReader.decodeSegments("abcef"))
    }

    @Test
    fun ambiguousOrFarPatterns_returnNull() {
        // 「abcdeg」離 0、2、3、8、9 好幾個都只差一段 → 不猜
        assertNull(SevenSegmentReader.decodeSegments("abcdeg"))
        assertNull(SevenSegmentReader.decodeSegments(""))
        assertNull(SevenSegmentReader.decodeSegments("ad"))
    }
}
