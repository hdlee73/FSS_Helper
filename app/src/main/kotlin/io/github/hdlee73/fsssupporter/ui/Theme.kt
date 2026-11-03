package io.github.hdlee73.fsssupporter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hdlee73.fsssupporter.data.ThemeMode

/**
 * 산뜻하고 차분하게: 흰 바탕, 1px 테두리, 강조색은 맑은 파랑 한 가지만 쓰고 나머지는 무채색.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF2B5CE6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9EFFD),
    onPrimaryContainer = Color(0xFF173A99),
    secondary = Color(0xFF5B6275),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEEFF4),
    onSecondaryContainer = Color(0xFF1A2033),
    tertiary = Color(0xFFE89400),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF6B4300),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF191C23),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C23),
    surfaceVariant = Color(0xFFF4F5F8),
    onSurfaceVariant = Color(0xFF656B78),
    surfaceContainer = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color(0xFFECEEF3),
    surfaceContainerLow = Color(0xFFFAFAFC),
    outline = Color(0xFFA9ABB8),
    outlineVariant = Color(0xFFE5E7EE),
    error = Color(0xFFC0392B),
    onError = Color.White,
    errorContainer = Color(0xFFFBE4E1),
    onErrorContainer = Color(0xFF6B1B14),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9DB7FF),
    onPrimary = Color(0xFF0E2A70),
    primaryContainer = Color(0xFF1E3366),
    onPrimaryContainer = Color(0xFFDDE6FF),
    secondary = Color(0xFFB4B3CC),
    onSecondary = Color(0xFF1B1840),
    secondaryContainer = Color(0xFF2A2B33),
    onSecondaryContainer = Color(0xFFE2E2EC),
    tertiary = Color(0xFFFFC560),
    onTertiary = Color(0xFF3A2500),
    tertiaryContainer = Color(0xFF3D2E12),
    onTertiaryContainer = Color(0xFFFFDDA0),
    background = Color(0xFF16171B),
    onBackground = Color(0xFFE6E6EC),
    surface = Color(0xFF1C1D22),
    onSurface = Color(0xFFE6E6EC),
    surfaceVariant = Color(0xFF24252B),
    onSurfaceVariant = Color(0xFFA0A2B0),
    surfaceContainer = Color(0xFF191A1F),
    surfaceContainerHigh = Color(0xFF2A2C33),
    surfaceContainerLow = Color(0xFF18191D),
    outline = Color(0xFF5E606C),
    outlineVariant = Color(0xFF2E3038),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0E09),
    errorContainer = Color(0xFF3A1C1A),
    onErrorContainer = Color(0xFFFFDAD5),
)

/** 정상·주의·오류를 색과 글자 모두로 보여 줄 때 쓰는 색. */
@Immutable
data class StatusColors(
    val ok: Color, val okBg: Color,
    val warn: Color, val warnBg: Color,
    val bad: Color, val badBg: Color,
)

private val LightStatus = StatusColors(
    ok = Color(0xFF21794F), okBg = Color(0xFFE2F2E9),
    warn = Color(0xFFB35E12), warnBg = Color(0xFFFBEBDD),
    bad = Color(0xFFB93A30), badBg = Color(0xFFF8E2DF),
)
private val DarkStatus = StatusColors(
    ok = Color(0xFF7FD6A8), okBg = Color(0xFF173227),
    warn = Color(0xFFF2B35B), warnBg = Color(0xFF3A2A14),
    bad = Color(0xFFFF8A80), badBg = Color(0xFF3A1C1A),
)

val LocalStatus = staticCompositionLocalOf { LightStatus }

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, lineHeight = 30.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    titleSmall = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 19.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 11.5.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatus provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
