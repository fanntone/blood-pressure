package tw.fann.bp.ocr

import tw.fann.bp.data.BpRules

/** 辨識出的一段文字（一列數字）與它在螢幕中的位置（像素）。 */
data class TextToken(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = bottom - top
}

data class BpValues(val systolic: Int, val diastolic: Int, val pulse: Int?)

/**
 * 從血壓計螢幕辨識出的各列數字挑出收縮壓 / 舒張壓 / 脈搏。
 *
 * 假設：多數血壓計由上到下依序顯示 SYS、DIA、PULSE，且前兩者字最大。
 * 找不到合理的收縮/舒張組合時回傳 null，請使用者重拍。
 */
object ReadingParser {

    private val SYS_RANGE = BpRules.SYS_RANGE
    private val DIA_RANGE = BpRules.DIA_RANGE
    private val PULSE_RANGE = BpRules.PULSE_RANGE

    /** 「大字」門檻：高度至少是畫面最大數字的這個比例 */
    private const val BIG_RATIO = 0.55

    fun parse(tokens: List<TextToken>): BpValues? {
        val numbers = tokens.mapNotNull(::normalize).filter { it.text.length in 2..3 }
        if (numbers.isEmpty()) return null

        val maxH = numbers.maxOf { it.height }
        val big = numbers.filter { it.height >= maxH * BIG_RATIO }.sortedWith(compareBy({ it.top }, { it.left }))

        for (i in big.indices) {
            val sys = big[i].value
            if (sys !in SYS_RANGE) continue
            for (j in i + 1 until big.size) {
                val dia = big[j].value
                if (dia in DIA_RANGE && dia < sys) {
                    val pulse = findPulse(numbers, big[i], big[j])
                    return BpValues(sys, dia, pulse)
                }
            }
        }
        return null
    }

    /** 脈搏：舒張壓下方最近的合理數字，字可以比較小。 */
    private fun findPulse(numbers: List<TextToken>, sys: TextToken, dia: TextToken): Int? =
        numbers
            .filter { it !== sys && it !== dia && it.top > dia.top && it.value in PULSE_RANGE }
            .minByOrNull { it.top }
            ?.value

    /** 只留純數字的字塊（含「?」等無法判讀的格子就捨棄）。 */
    private fun normalize(token: TextToken): TextToken? {
        val text = token.text.trim()
        return if (text.isNotEmpty() && text.all { it.isDigit() }) token.copy(text = text) else null
    }

    private val TextToken.value: Int get() = text.toInt()
}
