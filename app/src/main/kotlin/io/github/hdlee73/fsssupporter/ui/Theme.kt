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

/** 시안 5 "컴팩트 그린": 짙은 녹색 강조, 옅은 회녹색 메뉴, 흰 카드, 촘촘한 여백. */
private val Light = lightColorScheme(
    primary = Color(0xFF215C45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBE0),
    onPrimaryContainer = Color(0xFF12392A),
    secondary = Color(0xFF4E6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EAE6),
    onSecondaryContainer = Color(0xFF22302A),
    tertiary = Color(0xFFC46A1B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEBDD),
    onTertiaryContainer = Color(0xFF7A3E08),
    background = Color(0xFFFAFBFA),
    onBackground = Color(0xFF1C2621),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2621),
    surfaceVariant = Color(0xFFF1F4F2),
    onSurfaceVariant = Color(0xFF66736C),
    surfaceContainer = Color(0xFFF1F4F2),
    surfaceContainerHigh = Color(0xFFE9EEEB),
    surfaceContainerLow = Color(0xFFF7F9F8),
    outline = Color(0xFFA7B3AC),
    outlineVariant = Color(0xFFE1E6E3),
    error = Color(0xFFB93A30),
    onError = Color.White,
    errorContainer = Color(0xFFF8E2DF),
    onErrorContainer = Color(0xFF6B1B14),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD6A8),
    onPrimary = Color(0xFF06311F),
    primaryContainer = Color(0xFF1D3A2E),
    onPrimaryContainer = Color(0xFFBFEBD3),
    secondary = Color(0xFFA9BDB2),
    onSecondary = Color(0xFF15241D),
    secondaryContainer = Color(0xFF26302B),
    onSecondaryContainer = Color(0xFFDCE6E0),
    tertiary = Color(0xFFF2B35B),
    onTertiary = Color(0xFF3A2306),
    tertiaryContainer = Color(0xFF3A2A14),
    onTertiaryContainer = Color(0xFFFFD9A8),
    background = Color(0xFF111513),
    onBackground = Color(0xFFE5EAE7),
    surface = Color(0xFF181D1A),
    onSurface = Color(0xFFE5EAE7),
    surfaceVariant = Color(0xFF1E2421),
    onSurfaceVariant = Color(0xFF9AA6A0),
    surfaceContainer = Color(0xFF1E2421),
    surfaceContainerHigh = Color(0xFF252C28),
    surfaceContainerLow = Color(0xFF151A17),
    outline = Color(0xFF5C6862),
    outlineVariant = Color(0xFF2A312D),
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
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
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
    medium = RoundedCornerShape(8.dp),
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
