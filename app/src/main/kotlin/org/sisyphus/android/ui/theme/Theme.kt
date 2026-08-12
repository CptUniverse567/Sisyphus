package org.sisyphus.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Sisyphus visual identity: dark, industrial, deliberate.
 * A machine whose only purpose is "make me get out of bed."
 */
object SisyphusColors {
    val Background = Color(0xFF0B0B0C)
    val Surface = Color(0xFF121214)
    val SurfaceRaised = Color(0xFF1A1A1D)
    val Border = Color(0xFF2A2A2E)
    val BorderStrong = Color(0xFF3A3A3F)
    val TextPrimary = Color(0xFFECE9E2)
    val TextSecondary = Color(0xFF918E88)
    val TextFaint = Color(0xFF5E5B57)
    val Accent = Color(0xFFD9A45B)
    val Alarm = Color(0xFFC24B3B)
}

private val DarkScheme =
    darkColorScheme(
        primary = SisyphusColors.Accent,
        onPrimary = Color(0xFF171512),
        primaryContainer = Color(0xFF3A2F1E),
        onPrimaryContainer = SisyphusColors.TextPrimary,
        secondary = SisyphusColors.TextSecondary,
        onSecondary = Color(0xFF171512),
        background = SisyphusColors.Background,
        onBackground = SisyphusColors.TextPrimary,
        surface = SisyphusColors.Surface,
        onSurface = SisyphusColors.TextPrimary,
        surfaceVariant = SisyphusColors.SurfaceRaised,
        onSurfaceVariant = SisyphusColors.TextSecondary,
        outline = SisyphusColors.Border,
        outlineVariant = SisyphusColors.BorderStrong,
        error = SisyphusColors.Alarm,
        onError = Color(0xFFFFFFFF),
    )

private val Mono = FontFamily.Monospace

val SisyphusTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 136.sp,
                lineHeight = 136.sp,
                letterSpacing = (-4).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 88.sp,
                lineHeight = 92.sp,
                letterSpacing = (-2).sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 56.sp,
                lineHeight = 60.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = 2.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 2.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                letterSpacing = 3.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Mono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                letterSpacing = 3.sp,
            ),
    )

@Composable
fun sisyphusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = SisyphusTypography,
        content = content,
    )
}
