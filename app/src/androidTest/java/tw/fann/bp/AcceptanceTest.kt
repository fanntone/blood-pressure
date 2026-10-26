package tw.fann.bp

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tw.fann.bp.data.FailedPhotoStore
import tw.fann.bp.data.PhotoLoader
import tw.fann.bp.data.ReadingRepository
import tw.fann.bp.ocr.BpReader
import java.io.File

/**
 * 端到端驗收：真實血壓計照片 → 載入 → 離線七段解碼→ 寫入 SQLite → 從資料庫讀回對帳。
 * 期望值是人工看照片判讀後寫死的黃金值。
 * 使用獨立的測試資料庫與照片目錄，前後自行清理，可重複執行。
 */
@RunWith(AndroidJUnit4::class)
class AcceptanceTest {

    private val testContext: Context = InstrumentationRegistry.getInstrumentation().context
    private val appContext: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "acceptance_test.db"
    private val photoDir = File(appContext.filesDir, "acceptance_photos")
    private lateinit var repo: ReadingRepository

    @Before
    fun setUp() {
        cleanUp()
        repo = ReadingRepository(appContext, dbName, photoDir)
    }

    @After
    fun tearDown() {
        repo.close()
        cleanUp()
    }

    private fun cleanUp() {
        appContext.deleteDatabase(dbName)
        photoDir.deleteRecursively()
    }

    private fun photoToDb(asset: String, expected: Triple<Int, Int, Int?>) = runBlocking {
        val path = "bp_photos/$asset"
        val photo = PhotoLoader.load({ testContext.assets.open(path) }).bitmap

        val values = BpReader.read(photo)
        Log.i(TAG, "$asset => $values")
        assertNotNull("$asset 辨識不出血壓數字", values)

        val before = repo.all().size
        val id = repo.save(1_000L, values!!.systolic, values.diastolic, values.pulse, photo)

        // 實際值一律從資料庫取
        val rows = repo.all()
        assertEquals(before + 1, rows.size)
        val row = rows.single { it.id == id }
        assertEquals(asset, expected, Triple(row.systolic, row.diastolic, row.pulse))
        assertTrue("$asset 照片沒存到", File(row.photoPath!!).length() > 0)
    }

    @Test fun photo_123_79_70() = photoToDb("bp_123_79_70.jpg", Triple(123, 79, 70))

    @Test fun photo_118_74_89() = photoToDb("bp_118_74_89.jpg", Triple(118, 74, 89))

    @Test fun photo_120_81_85_tilted() = photoToDb("bp_120_81_85_tilted.jpg", Triple(120, 81, 85))

    @Test fun photo_117_75_80_inCamera() = photoToDb("bp_117_75_80_incamera.jpg", Triple(117, 75, 80))

    @Test
    fun failedPhotos_keptForDiagnosis_onlyNewest() {
        val dir = File(appContext.filesDir, "acceptance_failed").apply { deleteRecursively() }
        val store = FailedPhotoStore(appContext, dir, keep = 3)
        val photo = android.graphics.Bitmap.createBitmap(40, 30, android.graphics.Bitmap.Config.ARGB_8888)
        for (t in 1L..5L) store.save(photo, t * 1000)
        assertEquals(listOf("failed_5000.jpg", "failed_4000.jpg", "failed_3000.jpg"), dir.list()!!.sortedDescending())
        dir.deleteRecursively()
    }

    /** App 內相機實拍（已轉正、縮到 2048，沒有 EXIF） */
    @Test fun photo_121_76_85_inCamera() = photoToDb("bp_121_76_85_incamera.jpg", Triple(121, 76, 85))

    @Test
    fun manualEntry_savedWithoutPhoto_thenDeleted() {
        val id = repo.save(2_000L, 135, 88, null, null)
        val row = repo.all().single { it.id == id }
        assertEquals(Triple(135, 88, null), Triple(row.systolic, row.diastolic, row.pulse))
        assertEquals(null, row.photoPath)

        repo.delete(row)
        assertTrue(repo.all().none { it.id == id })
    }

    @Test
    fun autoSavedReading_correctedByUser_keepsTimeAndPhoto() = runBlocking {
        val photo = PhotoLoader.load({ testContext.assets.open("bp_photos/bp_118_74_89.jpg") }).bitmap
        val takenAt = 1_761_446_400_000L
        val id = repo.save(takenAt, 118, 74, 89, photo)

        repo.update(id, 119, 75, null)

        val row = repo.all().single { it.id == id }
        assertEquals(Triple(119, 75, null), Triple(row.systolic, row.diastolic, row.pulse))
        assertEquals(takenAt, row.measuredAt)
        assertTrue(File(row.photoPath!!).length() > 0)
    }

    @Test
    fun since_returnsOnlyReadingsInRange_newestFirst() {
        repo.save(1_000L, 120, 80, 70, null)
        repo.save(5_000L, 130, 85, 72, null)
        repo.save(9_000L, 140, 90, 74, null)
        assertEquals(listOf(140, 130), repo.since(5_000L).map { it.systolic })
    }

    companion object { private const val TAG = "BpAcceptance" }
}
