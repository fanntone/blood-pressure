package tw.fann.bp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReportBuilderTest {

    private val tw = ZoneId.of("Asia/Taipei")
    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(tw).toInstant().toEpochMilli()

    private var nextId = 1L
    private fun r(time: Long, sys: Int, dia: Int, pulse: Int?) = Reading(nextId++, time, sys, dia, pulse, null)

    @Test
    fun stats_withHandComputedAverages() {
        val readings = listOf(
            r(at(20, 7), 138, 88, 72),
            r(at(20, 21), 124, 78, 68),
            r(at(21, 7), 142, 91, null),
            r(at(21, 22), 118, 74, 89),
            r(at(22, 14), 130, 79, 70),
        )
        // 收縮 (138+124+142+118+130)/5 = 652/5 = 130.4 → 130
        // 舒張 (88+78+91+74+79)/5 = 410/5 = 82
        // 脈搏 (72+68+89+70)/4 = 299/4 = 74.75 → 75
        // 超過 130/80：138/88、142/91、130/79 → 3 次
        val all = ReportBuilder.build(readings, tw).overall!!
        assertEquals(5, all.count)
        assertEquals(130, all.avgSystolic)
        assertEquals(82, all.avgDiastolic)
        assertEquals(75, all.avgPulse)
        assertEquals(142, all.maxSystolic)
        assertEquals(118, all.minSystolic)
        assertEquals(91, all.maxDiastolic)
        assertEquals(74, all.minDiastolic)
        assertEquals(3, all.highCount)
    }

    @Test
    fun noReadings_statsNull() {
        assertNull(ReportBuilder.build(emptyList(), tw).overall)
        assertEquals(emptyList<DayRow>(), ReportBuilder.build(emptyList(), tw).days)
    }

    @Test
    fun dailyTable_groupsByCalendarDay_newestDayFirst_earliestReadingFirst() {
        val a = r(at(20, 6, 50), 138, 88, 72)
        val b = r(at(20, 7, 5), 134, 86, 70)
        val c = r(at(20, 23, 40), 124, 78, 68)
        val d = r(at(22, 0, 30), 130, 79, 70)
        val e = r(at(22, 7), 142, 91, null)
        val days = ReportBuilder.build(listOf(e, d, c, b, a), tw).days

        assertEquals(listOf(LocalDate.of(2026, 10, 22), LocalDate.of(2026, 10, 20)), days.map { it.date })
        assertEquals(listOf(d, e), days[0].readings)
        assertEquals(listOf(a, b, c), days[1].readings)
    }
}
