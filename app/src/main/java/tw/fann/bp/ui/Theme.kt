package tw.fann.bp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 顏色與字級：長輩使用，字級整體放大、對比拉高。 */
object BpColors {
    val Surface = Color(0xFFFCFCFB)
    val TextPrimary = Color(0xFF0B0B0B)
    val TextSecondary = Color(0xFF52514E)
    val Grid = Color(0xFFE4E3DF)
    val Primary = Color(0xFF1C5CAB)

    // 圖表系列色（已通過色盲辨識度驗證）
    val Systolic = Color(0xFFEB6834)
    val Diastolic = Color(0xFF2A78D6)
}

private val BigTypography = Typography(
    displayLarge = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold, lineHeight = 72.sp),
    displayMedium = TextStyle(fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp),
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    titleLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp),
    titleMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    labelLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp),
    labelMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp),
)

@Composable
fun BpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = BpColors.Primary,
            background = BpColors.Surface,
            surface = BpColors.Surface,
            onBackground = BpColors.TextPrimary,
            onSurface = BpColors.TextPrimary,
            onSurfaceVariant = BpColors.TextSecondary,
        ),
        typography = BigTypography,
        content = content,
    )
}
