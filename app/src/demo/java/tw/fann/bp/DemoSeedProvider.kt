package tw.fann.bp

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import tw.fann.bp.data.ReadingRepository
import tw.fann.bp.data.TimeConfig
import java.time.LocalDate
import kotlin.random.Random

/** 示範版專用：資料庫是空的時候，填入最近 30 天的起床 / 睡前假資料。 */
class DemoSeedProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val repo = ReadingRepository(context!!)
        if (repo.all().isEmpty()) seed(repo)
        repo.close()
        return true
    }

    private fun seed(repo: ReadingRepository) {
        val rnd = Random(42)
        val zone = TimeConfig.ZONE
        val today = LocalDate.now(zone)
        for (daysAgo in 30 downTo 0) {
            val day = today.minusDays(daysAgo.toLong())
            val trend = daysAgo * 0.4                     // 越早越高，模擬調藥後慢慢下降
            if (rnd.nextInt(10) < 9) {                    // 起床：偶爾忘了量
                val t = day.atTime(6 + rnd.nextInt(2), rnd.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
                repo.save(t, (128 + trend + rnd.nextInt(-6, 9)).toInt(), (82 + trend / 2 + rnd.nextInt(-4, 6)).toInt(), 64 + rnd.nextInt(12), null)
            }
            if (daysAgo > 0 && rnd.nextInt(10) < 8) {     // 睡前
                val t = day.atTime(22, rnd.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
                repo.save(t, (120 + trend + rnd.nextInt(-6, 9)).toInt(), (76 + trend / 2 + rnd.nextInt(-4, 6)).toInt(), 60 + rnd.nextInt(12), null)
            }
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri) = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
