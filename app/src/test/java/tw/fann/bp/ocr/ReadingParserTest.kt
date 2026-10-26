package tw.fann.bp.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingParserTest {

    /** 以 (left, top, 高度) 擺放一個字塊，寬度約為每字 0.6 倍高。 */
    private fun t(text: String, left: Int, top: Int, h: Int) =
        TextToken(text, left, top, left + (h * 0.6 * text.length).toInt(), top + h)

    @Test
    fun typicalLayout_sysDiaPulseTopToBottom_withNoise() {
        val tokens = listOf(
            t("10:35", 20, 10, 30),   // 時間
            t("12", 400, 10, 30),     // 記憶編號
            t("SYS", 20, 80, 30),
            t("128", 150, 60, 160),
            t("mmHg", 20, 130, 25),
            t("DIA", 20, 260, 30),
            t("82", 200, 240, 150),
            t("PUL", 20, 430, 30),
            t("71", 250, 420, 80),
        )
        assertEquals(BpValues(128, 82, 71), ReadingParser.parse(tokens))
    }

    @Test
    fun missingPulse_stillReturnsSysAndDia() {
        val tokens = listOf(
            t("135", 150, 60, 160),
            t("88", 200, 240, 150),
        )
        assertEquals(BpValues(135, 88, null), ReadingParser.parse(tokens))
    }

    @Test
    fun undecodedCells_rowIsSkipped() {
        // 七段解碼器把讀不出的格子標成「?」，整列不採用
        val tokens = listOf(
            t("12?", 150, 60, 160),
            t("128", 150, 240, 160),
            t("82", 200, 420, 150),
        )
        assertEquals(BpValues(128, 82, null), ReadingParser.parse(tokens))
    }

    @Test
    fun onlyOneNumber_returnsNull() {
        assertNull(ReadingParser.parse(listOf(t("128", 150, 60, 160))))
    }

    @Test
    fun noTokens_returnsNull() {
        assertNull(ReadingParser.parse(emptyList()))
    }

    @Test
    fun diastolicNotLowerThanSystolic_isSkipped() {
        // 上方最大的數字比下方小，不是合理的收縮/舒張組合，往下找
        val tokens = listOf(
            t("60", 150, 0, 160),
            t("142", 150, 200, 160),
            t("91", 200, 400, 150),
            t("66", 250, 600, 80),
        )
        assertEquals(BpValues(142, 91, 66), ReadingParser.parse(tokens))
    }
}
