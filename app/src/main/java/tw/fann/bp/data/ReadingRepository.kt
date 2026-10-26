package tw.fann.bp.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import java.io.File

data class Reading(
    val id: Long,
    val measuredAt: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int?,
    val photoPath: String?,
)

private class ReadingDb(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE readings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                measured_at INTEGER NOT NULL,
                systolic INTEGER NOT NULL,
                diastolic INTEGER NOT NULL,
                pulse INTEGER,
                photo_path TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_readings_time ON readings(measured_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

/**
 * 量測紀錄的唯一存取點：SQLite 存數字，照片存在 app 私有目錄。
 * dbName / photoDir 可注入，讓驗收測試使用獨立的資料庫。
 */
class ReadingRepository(
    context: Context,
    dbName: String = "readings.db",
    private val photoDir: File = File(context.filesDir, "photos"),
) {
    private val helper = ReadingDb(context.applicationContext, dbName)

    fun save(measuredAt: Long, systolic: Int, diastolic: Int, pulse: Int?, photo: Bitmap?): Long {
        val photoPath = photo?.let { writePhoto(measuredAt, it) }
        val values = ContentValues().apply {
            put("measured_at", measuredAt)
            put("systolic", systolic)
            put("diastolic", diastolic)
            if (pulse != null) put("pulse", pulse) else putNull("pulse")
            put("photo_path", photoPath)
        }
        return helper.writableDatabase.insertOrThrow("readings", null, values)
    }

    /** 修改數值（時間與照片不變） */
    fun update(id: Long, systolic: Int, diastolic: Int, pulse: Int?) {
        val values = ContentValues().apply {
            put("systolic", systolic)
            put("diastolic", diastolic)
            if (pulse != null) put("pulse", pulse) else putNull("pulse")
        }
        val n = helper.writableDatabase.update("readings", values, "id = ?", arrayOf(id.toString()))
        check(n == 1) { "找不到要修改的紀錄 id=$id" }
    }

    /** 由新到舊 */
    fun all(): List<Reading> = query(null, null)

    /** sinceMillis 之後（含）的紀錄，由新到舊 */
    fun since(sinceMillis: Long): List<Reading> =
        query("measured_at >= ?", arrayOf(sinceMillis.toString()))

    fun delete(reading: Reading) {
        helper.writableDatabase.delete("readings", "id = ?", arrayOf(reading.id.toString()))
        reading.photoPath?.let { File(it).delete() }
    }

    fun close() = helper.close()

    private fun query(where: String?, args: Array<String>?): List<Reading> =
        helper.readableDatabase.query(
            "readings", null, where, args, null, null, "measured_at DESC, id DESC"
        ).use { c -> buildList { while (c.moveToNext()) add(c.toReading()) } }

    private fun Cursor.toReading() = Reading(
        id = getLong(getColumnIndexOrThrow("id")),
        measuredAt = getLong(getColumnIndexOrThrow("measured_at")),
        systolic = getInt(getColumnIndexOrThrow("systolic")),
        diastolic = getInt(getColumnIndexOrThrow("diastolic")),
        pulse = getColumnIndexOrThrow("pulse").let { if (isNull(it)) null else getInt(it) },
        photoPath = getColumnIndexOrThrow("photo_path").let { if (isNull(it)) null else getString(it) },
    )

    private fun writePhoto(measuredAt: Long, photo: Bitmap): String {
        photoDir.mkdirs()
        val file = File(photoDir, "bp_$measuredAt.jpg")
        file.outputStream().use { out ->
            check(photo.compress(Bitmap.CompressFormat.JPEG, 85, out)) { "照片寫入失敗：$file" }
        }
        return file.absolutePath
    }
}
