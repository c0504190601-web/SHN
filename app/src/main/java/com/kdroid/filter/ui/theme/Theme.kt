package com.kdroid.filter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// פלטת "ניאון כהה"
val BgDark = Color(0xFF0A0E1A)
val SurfaceDark = Color(0xFF121829)
val SurfaceElevated = Color(0xFF1A2238)
val NeonCyan = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00E676)
val NeonAmber = Color(0xFFFFC400)
val NeonRed = Color(0xFFFF5252)
val NeonPurple = Color(0xFFB388FF)
val TextPrimary = Color(0xFFE6ECF5)
val TextMuted = Color(0xFF8A94A6)

private val KDroidColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = BgDark,
    secondary = NeonPurple,
    background = BgDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    error = NeonRed,
)

@Composable
fun KDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KDroidColors,
        typography = Typography(),
        content = content
    )
}
