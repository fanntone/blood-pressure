package tw.fann.bp.ocr

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 背光液晶血壓計的七段數字解碼器（純運算，不依賴 Android）。
 *
 * 流程：找出最大的綠色背光區（螢幕）→ 轉正並裁切 → 自適應二值化 → 只留細長的筆畫
 * → 修正斜體 → 切成一列列 → 切成一格格數字 → 檢查 7 段筆畫哪幾段亮。
 * 二值化用多組參數各讀一次，收縮/舒張/脈搏各自多數決，降低反光造成的誤判。
 */
object SevenSegmentReader {

    /** 找螢幕時使用的格點長邊 */
    private const val FIND_SIDE = 1024

    /** 裁切時四周外擴的比例 */
    private const val CROP_MARGIN = 0.04

    /** 裁切後的螢幕統一高度（像素） */
    private const val LCD_HEIGHT = 300

    private class Gray(val w: Int, val h: Int, val v: FloatArray) {
        operator fun get(x: Int, y: Int) = v[y * w + x]
    }

    private class Mask(val w: Int, val h: Int, val v: BooleanArray = BooleanArray(w * h)) {
        operator fun get(x: Int, y: Int) = v[y * w + x]
        operator fun set(x: Int, y: Int, b: Boolean) { v[y * w + x] = b }
    }

    private enum class Background { MEAN, MAX }

    private val CONFIGS = listOf(
        Background.MEAN to 0.75f, Background.MEAN to 0.80f, Background.MEAN to 0.85f,
        Background.MAX to 0.75f, Background.MAX to 0.80f, Background.MAX to 0.85f,
    )

    // a=上 b=右上 c=右下 d=下 e=左下 f=左上 g=中
    private val DIGITS = mapOf(
        "abcdef" to 0, "bc" to 1, "abdeg" to 2, "abcdg" to 3, "bcfg" to 4,
        "acdfg" to 5, "acdefg" to 6, "abc" to 7, "abcdefg" to 8, "abcdfg" to 9,
    )

    /** argb：照片像素（Bitmap.getPixels 的格式）。讀不出合理數值時回傳 null。 */
    fun read(argb: IntArray, width: Int, height: Int): BpValues? {
        val lcd = cropLcd(argb, width, height) ?: return null
        val all = CONFIGS.mapNotNull { (bg, ratio) -> ReadingParser.parse(readRows(lcd, bg, ratio)) }
        if (all.isEmpty()) return null
        // 三個數字都讀到的組別二值化品質通常較好，優先由它們投票
        val results = all.filter { it.pulse != null }.ifEmpty { all }
        fun vote(pick: (BpValues) -> Int?) =
            results.mapNotNull(pick).groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return BpValues(vote { it.systolic }!!, vote { it.diastolic }!!, vote { it.pulse })
    }

    /** 診斷用：每組二值化參數讀到的各列文字。 */
    internal fun debugRows(argb: IntArray, width: Int, height: Int): List<String> {
        val lcd = cropLcd(argb, width, height) ?: return listOf("找不到螢幕")
        return CONFIGS.map { (bg, ratio) ->
            "$bg $ratio ${lcd.w}x${lcd.h}: " + readRows(lcd, bg, ratio).joinToString { it.text }
        }
    }

    /** 診斷用：第一組參數的筆畫影像（寬、高、像素）。 */
    internal fun debugMask(argb: IntArray, width: Int, height: Int): Triple<Int, Int, BooleanArray>? {
        val lcd = cropLcd(argb, width, height) ?: return null
        val d = deslant(strokes(lcd, CONFIGS[0].first, CONFIGS[0].second))
        return Triple(d.w, d.h, d.v)
    }

    /** 筆畫組合 → 數字；允許錯一段，但最接近的數字必須唯一。 */
    internal fun decodeSegments(on: String): Int? {
        DIGITS[on]?.let { return it }
        val key = on.toSet()
        val near = DIGITS.filterKeys { (it.toSet() union key).size - (it.toSet() intersect key).size == 1 }.values.toSet()
        return near.singleOrNull()
    }

    // ---------- 找螢幕、轉正、裁切 ----------

    /**
     * 在縮小的格點上找螢幕位置，再從原解析度照片轉正裁切，
     * 統一縮放成高 [LCD_HEIGHT] 像素，讓後續參數與拍攝距離無關。
     */
    private fun cropLcd(argb: IntArray, fullW: Int, fullH: Int): Gray? {
        val step = (max(fullW, fullH) + FIND_SIDE - 1) / FIND_SIDE
        val w = fullW / step; val h = fullH / step
        val green = Mask(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = argb[(y * step) * fullW + x * step]
            val r = (p shr 16) and 0xff; val g = (p shr 8) and 0xff; val b = p and 0xff
            green[x, y] = g > 130 && g - r > 35 && g - b > 35
        }
        val closed = fillHoles(close(green, max(2, max(w, h) / 170)))
        val lcd = components(closed).maxByOrNull { it.size } ?: return null
        if (lcd.size < w * h / 200) return null

        // 主軸方向（PCA）決定傾斜角
        var cx = 0.0; var cy = 0.0
        for (i in lcd) { cx += i % w; cy += i / w }
        cx /= lcd.size; cy /= lcd.size
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in lcd) {
            val dx = i % w - cx; val dy = i / w - cy
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        val axisAngle = 0.5 * atan2(2 * sxy, sxx - syy)      // 主軸與 x 軸夾角
        var phi = Math.PI / 2 - axisAngle                     // 與垂直方向的夾角
        while (phi > Math.PI / 2) phi -= Math.PI
        while (phi <= -Math.PI / 2) phi += Math.PI
        if (abs(phi) > Math.PI / 4) phi -= Math.signum(phi) * Math.PI / 2
        // 轉正座標：q = R·(p - c)
        val cosP = cos(phi); val sinP = sin(phi)

        val us = DoubleArray(lcd.size); val vs = DoubleArray(lcd.size)
        lcd.forEachIndexed { k, i ->
            val dx = i % w - cx; val dy = i / w - cy
            us[k] = dx * cosP - dy * sinP
            vs[k] = dx * sinP + dy * cosP
        }
        us.sort(); vs.sort()
        // 換算回原解析度座標；螢幕邊緣常因反光或暗角被少算，四周多留一點邊，
        // 免得靠邊的數字被切到一半、又被當成邊框雜訊刪掉
        val du = (us[(us.size * 0.99).toInt()] - us[(us.size * 0.01).toInt()]) * CROP_MARGIN
        val dv = (vs[(vs.size * 0.99).toInt()] - vs[(vs.size * 0.01).toInt()]) * CROP_MARGIN
        val u0 = (us[(us.size * 0.01).toInt()] - du) * step; val u1 = (us[(us.size * 0.99).toInt()] + du) * step
        val v0 = (vs[(vs.size * 0.01).toInt()] - dv) * step; val v1 = (vs[(vs.size * 0.99).toInt()] + dv) * step
        val fcx = cx * step; val fcy = cy * step
        if (u1 - u0 < 20 || v1 - v0 < 20) return null

        val k = (v1 - v0) / LCD_HEIGHT                        // 每個輸出像素對應的原圖像素
        val cw = ((u1 - u0) / k).toInt(); val ch = LCD_HEIGHT
        val out = FloatArray(cw * ch)
        val sub = doubleArrayOf(-0.25, 0.25)                  // 2×2 超取樣
        for (y in 0 until ch) for (x in 0 until cw) {
            var sum = 0f
            for (dy in sub) for (dx in sub) {
                val u = u0 + (x + 0.5 + dx) * k; val v = v0 + (y + 0.5 + dy) * k
                sum += bilinearLuma(argb, fullW, fullH, fcx + u * cosP + v * sinP, fcy - u * sinP + v * cosP)
            }
            out[y * cw + x] = sum / 4
        }
        return Gray(cw, ch, out)
    }

    private fun luma(p: Int) = 0.299f * ((p shr 16) and 0xff) + 0.587f * ((p shr 8) and 0xff) + 0.114f * (p and 0xff)

    private fun bilinearLuma(argb: IntArray, w: Int, h: Int, x: Double, y: Double): Float {
        val fx = x.coerceIn(0.0, w - 1.001); val fy = y.coerceIn(0.0, h - 1.001)
        val x0 = fx.toInt(); val y0 = fy.toInt()
        val ax = (fx - x0).toFloat(); val ay = (fy - y0).toFloat()
        val top = luma(argb[y0 * w + x0]) * (1 - ax) + luma(argb[y0 * w + x0 + 1]) * ax
        val bottom = luma(argb[(y0 + 1) * w + x0]) * (1 - ax) + luma(argb[(y0 + 1) * w + x0 + 1]) * ax
        return top * (1 - ay) + bottom * ay
    }

    // ---------- 數字辨識 ----------

    /** 回傳每一列的文字（無法判讀的格子為「?」），交給 ReadingParser 挑出血壓值。 */
    private fun readRows(lcd: Gray, bg: Background, ratio: Float): List<TextToken> {
        val d = deslant(strokes(lcd, bg, ratio))
        val rows = runs(IntArray(d.h) { y -> (0 until d.w).count { d[it, y] } }, 1)
            .filter { (y0, y1) -> y1 - y0 >= d.h * 0.08 }
        return rows.map { (y0, y1) ->
            val bh = y1 - y0
            val cols = IntArray(d.w) { x -> (y0 until y1).count { d[x, it] } }
            val text = buildString {
                for ((x0, x1) in runs(cols, 2)) {
                    val ys = (y0 until y1).filter { y -> (x0 until x1).any { d[it, y] } }
                    if (ys.isEmpty()) continue
                    val top = ys.first(); val bottom = ys.last() + 1
                    if (bottom - top < 0.7 * bh || x1 - x0 > 0.9 * bh) continue
                    append(decodeCell(d, x0, x1, top, bottom)?.toString() ?: "?")
                }
            }
            TextToken(text, 0, y0, d.w, y1)
        }
    }

    private fun decodeCell(d: Mask, x0: Int, x1: Int, y0: Int, y1: Int): Int? {
        val w = x1 - x0; val h = y1 - y0
        if (w < 0.4 * h) return 1
        fun on(fx0: Double, fx1: Double, fy0: Double, fy1: Double): Boolean {
            val ax = x0 + (fx0 * w).toInt(); val bx = max(x0 + (fx1 * w).toInt(), ax + 1)
            val ay = y0 + (fy0 * h).toInt(); val by = max(y0 + (fy1 * h).toInt(), ay + 1)
            var n = 0
            for (y in ay until by) for (x in ax until bx) if (d[x, y]) n++
            return n > 0.35 * (bx - ax) * (by - ay)
        }
        val segs = buildString {
            if (on(.3, .7, 0.0, .15)) append('a')
            if (on(.75, 1.0, .15, .42)) append('b')
            if (on(.75, 1.0, .58, .85)) append('c')
            if (on(.3, .7, .85, 1.0)) append('d')
            if (on(0.0, .25, .58, .85)) append('e')
            if (on(0.0, .25, .15, .42)) append('f')
            if (on(.3, .7, .43, .57)) append('g')
        }
        return decodeSegments(segs)
    }

    /** 暗於背景的像素 → 填滿空心筆畫 → 只留細長、夠大的筆畫（濾掉圖示、小字、邊框）。 */
    private fun strokes(g: Gray, bg: Background, ratio: Float): Mask {
        val (src, background) = when (bg) {
            Background.MEAN -> g to boxBlur(g, max(3, g.w / 8))
            Background.MAX -> {
                val smooth = boxBlur(g, 1)
                val r = max(3, g.w / 12)
                smooth to boxBlur(maxFilter(smooth, r), r)
            }
        }
        var d = Mask(g.w, g.h)
        for (i in d.v.indices) d.v[i] = src.v[i] < background.v[i] * ratio
        for (c in components(d)) if (c.any { touchesEdge(it, g.w, g.h) }) c.forEach { d.v[it] = false }
        d = fillHoles(d)
        for (c in components(d)) {
            var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1
            for (i in c) {
                val x = i % g.w; val y = i / g.w
                minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
            }
            val bw = maxX - minX + 1; val bh = maxY - minY + 1
            val long = max(bw, bh); val short = max(1, min(bw, bh))
            if (long < 0.05 * g.h || long.toDouble() / short < 1.8) c.forEach { d.v[it] = false }
        }
        return d
    }

    /** 斜體修正：找讓直行投影最「尖銳」的剪切量。 */
    private fun deslant(d: Mask): Mask {
        var best = d; var bestScore = -1.0
        var s = -0.30
        while (s <= 0.3001) {
            val sh = shear(d, s)
            val cols = DoubleArray(sh.w) { x -> (0 until sh.h).count { sh[x, it] }.toDouble() }
            val mean = cols.average()
            val score = cols.sumOf { (it - mean) * (it - mean) }
            if (score > bestScore) { bestScore = score; best = sh }
            s += 0.02
        }
        return best
    }

    private fun shear(d: Mask, s: Double): Mask {
        val out = Mask(d.w, d.h)
        for (y in 0 until d.h) {
            val off = ((y - d.h / 2.0) * s).roundToInt()
            for (x in 0 until d.w) {
                val sx = x + off
                if (sx in 0 until d.w) out[x, y] = d[sx, y]
            }
        }
        return out
    }

    /** 連續非零區段 [start, end)，長度至少 minLen。 */
    private fun runs(p: IntArray, minLen: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var start = -1
        for (i in 0..p.size) {
            val v = if (i < p.size) p[i] else 0
            if (v > 0 && start < 0) start = i
            if (v <= 0 && start >= 0) {
                if (i - start >= minLen) out += start to i
                start = -1
            }
        }
        return out
    }

    // ---------- 影像基本運算 ----------

    private fun touchesEdge(i: Int, w: Int, h: Int): Boolean {
        val x = i % w; val y = i / w
        return x == 0 || y == 0 || x == w - 1 || y == h - 1
    }

    /** 4 連通的連通塊，回傳每塊的像素索引。 */
    private fun components(m: Mask): List<IntArray> {
        val seen = BooleanArray(m.v.size)
        val queue = IntArray(m.v.size)
        val out = mutableListOf<IntArray>()
        for (start in m.v.indices) {
            if (!m.v[start] || seen[start]) continue
            var head = 0; var tail = 0
            queue[tail++] = start; seen[start] = true
            while (head < tail) {
                val i = queue[head++]
                val x = i % m.w; val y = i / m.w
                if (x > 0 && m.v[i - 1] && !seen[i - 1]) { seen[i - 1] = true; queue[tail++] = i - 1 }
                if (x < m.w - 1 && m.v[i + 1] && !seen[i + 1]) { seen[i + 1] = true; queue[tail++] = i + 1 }
                if (y > 0 && m.v[i - m.w] && !seen[i - m.w]) { seen[i - m.w] = true; queue[tail++] = i - m.w }
                if (y < m.h - 1 && m.v[i + m.w] && !seen[i + m.w]) { seen[i + m.w] = true; queue[tail++] = i + m.w }
            }
            out += queue.copyOf(tail)
        }
        return out
    }

    /** 從邊框灌水，灌不到的背景就是洞。 */
    private fun fillHoles(m: Mask): Mask {
        val inv = Mask(m.w, m.h, BooleanArray(m.v.size) { !m.v[it] })
        val outside = BooleanArray(m.v.size)
        for (c in components(inv)) if (c.any { touchesEdge(it, m.w, m.h) }) c.forEach { outside[it] = true }
        return Mask(m.w, m.h, BooleanArray(m.v.size) { !outside[it] })
    }

    private fun close(m: Mask, r: Int): Mask = erode(dilate(m, r), r)

    private fun dilate(m: Mask, r: Int): Mask = morph(m, r, true)
    private fun erode(m: Mask, r: Int): Mask = morph(m, r, false)

    /** 方形結構元素的膨脹/侵蝕（分離成水平、垂直兩次）。 */
    private fun morph(m: Mask, r: Int, dilate: Boolean): Mask {
        fun pass(src: Mask, horizontal: Boolean): Mask {
            val out = Mask(src.w, src.h)
            val len = if (horizontal) src.w else src.h
            val lines = if (horizontal) src.h else src.w
            for (l in 0 until lines) {
                val prefix = IntArray(len + 1)
                for (k in 0 until len) {
                    val b = if (horizontal) src[k, l] else src[l, k]
                    prefix[k + 1] = prefix[k] + if (b) 1 else 0
                }
                for (k in 0 until len) {
                    val a = max(0, k - r); val b = min(len, k + r + 1)
                    val count = prefix[b] - prefix[a]
                    // 侵蝕時，超出邊界視為前景，避免邊緣被吃掉
                    val v = if (dilate) count > 0 else count == b - a
                    if (horizontal) out[k, l] = v else out[l, k] = v
                }
            }
            return out
        }
        return pass(pass(m, true), false)
    }

    private fun boxBlur(g: Gray, r: Int): Gray {
        val integral = DoubleArray((g.w + 1) * (g.h + 1))
        val iw = g.w + 1
        for (y in 0 until g.h) {
            var row = 0.0
            for (x in 0 until g.w) {
                row += g[x, y]
                integral[(y + 1) * iw + x + 1] = integral[y * iw + x + 1] + row
            }
        }
        val out = FloatArray(g.w * g.h)
        for (y in 0 until g.h) for (x in 0 until g.w) {
            val x0 = max(0, x - r); val x1 = min(g.w, x + r + 1)
            val y0 = max(0, y - r); val y1 = min(g.h, y + r + 1)
            val s = integral[y1 * iw + x1] - integral[y0 * iw + x1] - integral[y1 * iw + x0] + integral[y0 * iw + x0]
            out[y * g.w + x] = (s / ((x1 - x0) * (y1 - y0))).toFloat()
        }
        return Gray(g.w, g.h, out)
    }

    private fun maxFilter(g: Gray, r: Int): Gray {
        val tmp = FloatArray(g.w * g.h)
        for (y in 0 until g.h) for (x in 0 until g.w) {
            var m = 0f
            for (k in max(0, x - r)..min(g.w - 1, x + r)) m = max(m, g[k, y])
            tmp[y * g.w + x] = m
        }
        val out = FloatArray(g.w * g.h)
        for (y in 0 until g.h) for (x in 0 until g.w) {
            var m = 0f
            for (k in max(0, y - r)..min(g.h - 1, y + r)) m = max(m, tmp[k * g.w + x])
            out[y * g.w + x] = m
        }
        return Gray(g.w, g.h, out)
    }
}
