package tw.fann.bp.ocr

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 在電腦上用真實照片快速驗證七段解碼（方便調參數）。
 * 照片解碼/縮放方式和手機不完全相同，最終以 androidTest 的 AcceptanceTest 為準。
 */
class SevenSegmentPhotoTest {

    private val dir = File("src/androidTest/assets/bp_photos")

    /** 讀 JPEG、順時針轉 quarterTurns×90°（照 EXIF 方向）、縮到長邊 2048（同 PhotoLoader.MAX_SIDE），回傳 ARGB 像素。 */
    private fun load(name: String, quarterTurns: Int): Triple<IntArray, Int, Int> {
        val src = ImageIO.read(File(dir, name))
        val side = 2048
        val scale = side.toDouble() / maxOf(src.width, src.height)
        val turned = quarterTurns % 2 == 1
        val w = ((if (turned) src.height else src.width) * scale).toInt()
        val h = ((if (turned) src.width else src.height) * scale).toInt()
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, AffineTransform().apply {
            if (turned) { translate(w.toDouble(), 0.0); rotate(Math.PI / 2) }
            scale(scale, scale)
        }, null)
        g.dispose()
        return Triple(out.getRGB(0, 0, w, h, null, 0, w), w, h)
    }

    private fun check(name: String, expected: BpValues, quarterTurns: Int = 1) {
        val (px, w, h) = load(name, quarterTurns)
        SevenSegmentReader.debugRows(px, w, h).forEach { println("$name  $it") }
        System.getenv("BP_DEBUG_DIR")?.let { dir ->
            val (mw, mh, mv) = SevenSegmentReader.debugMask(px, w, h) ?: return@let
            val img = BufferedImage(mw, mh, BufferedImage.TYPE_INT_RGB)
            for (i in mv.indices) img.setRGB(i % mw, i / mw, if (mv[i]) 0 else 0xFFFFFF)
            ImageIO.write(img, "png", File(dir, "$name.mask.png"))
        }
        assertEquals(name, expected, SevenSegmentReader.read(px, w, h))
    }

    @Test fun photo_123_79_70() = check("bp_123_79_70.jpg", BpValues(123, 79, 70))
    @Test fun photo_118_74_89() = check("bp_118_74_89.jpg", BpValues(118, 74, 89))
    @Test fun photo_120_81_85_tilted() = check("bp_120_81_85_tilted.jpg", BpValues(120, 81, 85))
    @Test fun photo_117_75_80_inCamera() = check("bp_117_75_80_incamera.jpg", BpValues(117, 75, 80), quarterTurns = 0)

    /** 把近拍照片縮小置中、四周補桌面色，模擬站遠一點拍（螢幕只佔畫面一成也要讀得到）。 */
    @Test
    fun fartherShots_stillRead() {
        val src = ImageIO.read(File(dir, "bp_117_75_80_incamera.jpg"))
        for (scale in listOf(0.6, 0.45, 0.3)) {
            val out = BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_ARGB)
            val g = out.createGraphics()
            g.color = java.awt.Color(0x6B, 0x4A, 0x33)
            g.fillRect(0, 0, src.width, src.height)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val w = (src.width * scale).toInt(); val h = (src.height * scale).toInt()
            g.drawImage(src, (src.width - w) / 2, (src.height - h) / 2, w, h, null)
            g.dispose()
            val px = out.getRGB(0, 0, out.width, out.height, null, 0, out.width)
            assertEquals("scale=$scale", BpValues(117, 75, 80), SevenSegmentReader.read(px, out.width, out.height))
        }
    }

    @Test fun photo_121_76_85_inCamera() = check("bp_121_76_85_incamera.jpg", BpValues(121, 76, 85), quarterTurns = 0)
}
