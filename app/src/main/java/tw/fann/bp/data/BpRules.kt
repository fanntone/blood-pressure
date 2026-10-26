package tw.fann.bp.data

/** 血壓分級（依 2022 台灣高血壓治療指引的居家血壓分類）。 */
enum class BpCategory(val label: String) {
    NORMAL("正常"),
    ELEVATED("偏高"),
    STAGE1("第一期高血壓"),
    STAGE2("第二期高血壓"),
}

/** 分級門檻與可輸入範圍：所有「多少算高 / 什麼數字合理」都只在這裡決定。 */
object BpRules {
    const val ELEVATED_SYS = 120
    const val STAGE1_SYS = 130
    const val STAGE1_DIA = 80
    const val STAGE2_SYS = 140
    const val STAGE2_DIA = 90

    val SYS_RANGE = 60..260
    val DIA_RANGE = 30..160
    val PULSE_RANGE = 30..200

    fun categorize(systolic: Int, diastolic: Int): BpCategory = when {
        systolic >= STAGE2_SYS || diastolic >= STAGE2_DIA -> BpCategory.STAGE2
        systolic >= STAGE1_SYS || diastolic >= STAGE1_DIA -> BpCategory.STAGE1
        systolic >= ELEVATED_SYS -> BpCategory.ELEVATED
        else -> BpCategory.NORMAL
    }

    /** 回傳錯誤訊息；數字合理時回傳 null。 */
    fun validate(systolic: Int?, diastolic: Int?, pulse: Int?): String? = when {
        systolic == null || systolic !in SYS_RANGE -> "收縮壓（高壓）應在 ${SYS_RANGE.first}～${SYS_RANGE.last} 之間"
        diastolic == null || diastolic !in DIA_RANGE -> "舒張壓（低壓）應在 ${DIA_RANGE.first}～${DIA_RANGE.last} 之間"
        diastolic >= systolic -> "低壓應該比高壓小，請再檢查一次"
        pulse != null && pulse !in PULSE_RANGE -> "脈搏應在 ${PULSE_RANGE.first}～${PULSE_RANGE.last} 之間"
        else -> null
    }
}
