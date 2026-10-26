package tw.fann.bp.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

data class Stats(
    val count: Int,
    val avgSystolic: Int,
    val avgDiastolic: Int,
    val avgPulse: Int?,
    val maxSystolic: Int,
    val minSystolic: Int,
    val maxDiastolic: Int,
    val minDiastolic: Int,
    /** 收縮壓 ≥130 或舒張壓 ≥80 的次數 */
    val highCount: Int,
)

/** 每日血壓表的一列；當天的紀錄由早到晚。 */
data class DayRow(val date: LocalDate, val readings: List<Reading>)

data class DoctorReport(
    val overall: Stats?,
    /** 由新到舊 */
    val days: List<DayRow>,
)

object ReportBuilder {

    fun stats(readings: List<Reading>): Stats? {
        if (readings.isEmpty()) return null
        val pulses = readings.mapNotNull { it.pulse }
        return Stats(
            count = readings.size,
            avgSystolic = readings.map { it.systolic }.average().roundToInt(),
            avgDiastolic = readings.map { it.diastolic }.average().roundToInt(),
            avgPulse = if (pulses.isEmpty()) null else pulses.average().roundToInt(),
            maxSystolic = readings.maxOf { it.systolic },
            minSystolic = readings.minOf { it.systolic },
            maxDiastolic = readings.maxOf { it.diastolic },
            minDiastolic = readings.minOf { it.diastolic },
            highCount = readings.count {
                BpRules.categorize(it.systolic, it.diastolic) >= BpCategory.STAGE1
            },
        )
    }

    /** readings：已篩選好期間的紀錄。 */
    fun build(readings: List<Reading>, zone: ZoneId = TimeConfig.ZONE): DoctorReport {
        val days = readings
            .sortedBy { it.measuredAt }
            .groupBy { Instant.ofEpochMilli(it.measuredAt).atZone(zone).toLocalDate() }
            .map { (date, list) -> DayRow(date, list) }
            .sortedByDescending { it.date }
        return DoctorReport(overall = stats(readings), days = days)
    }
}
