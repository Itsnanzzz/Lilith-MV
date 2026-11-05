package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NeumorphStyle {
    RAISED,
    PRESSED,
    FLAT
}

enum class NeumorphLevel {
    SMALL,
    MEDIUM,
    LARGE
}

@Immutable
data class NeumorphicColors(
    val background: Color,
    val surface: Color,
    val lightShadow: Color,
    val darkShadow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val isDark: Boolean,
    val miniPlayerSurface: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerSurface else NeumorphicTokens.LightMiniPlayerSurface,
    val miniPlayerLightShadow: Color = Color.Transparent,
    val miniPlayerDarkShadow: Color = Color.Transparent,
    val miniPlayerTextPrimary: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerTextPrimary else NeumorphicTokens.LightMiniPlayerTextPrimary,
    val miniPlayerTextSecondary: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerTextSecondary else NeumorphicTokens.LightMiniPlayerTextSecondary,
    val miniPlayerAccent: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerAccent else NeumorphicTokens.LightMiniPlayerAccent
)

object NeumorphicTokens {
    // Light Theme Bridge (Mapped to Flat Tokens: #FFFFFF, #171C26, #6C7689)
    val LightBackground = AppTokens.LightBackground
    val LightSurface = AppTokens.LightSurface
    val LightLightShadow = Color.Transparent
    val LightDarkShadow = Color.Transparent
    val LightTextPrimary = AppTokens.LightTextPrimary
    val LightTextSecondary = AppTokens.LightTextSecondary
    val LightAccent = AppTokens.LightPrimary

    // Mini Player Light Theme Bridge
    val LightMiniPlayerSurface = AppTokens.LightMiniPlayerSurface
    val LightMiniPlayerLightShadow = Color.Transparent
    val LightMiniPlayerDarkShadow = Color.Transparent
    val LightMiniPlayerTextPrimary = AppTokens.LightMiniPlayerTextPrimary
    val LightMiniPlayerTextSecondary = AppTokens.LightMiniPlayerTextSecondary
    val LightMiniPlayerAccent = AppTokens.LightMiniPlayerTextPrimary

    // Dark Theme Bridge (Mapped to Flat Tokens: #0E1117, #F2F4F8, #A4AAB7)
    val DarkBackground = AppTokens.DarkBackground
    val DarkSurface = AppTokens.DarkSurface
    val DarkLightShadow = Color.Transparent
    val DarkDarkShadow = Color.Transparent
    val DarkTextPrimary = AppTokens.DarkTextPrimary
    val DarkTextSecondary = AppTokens.DarkTextSecondary
    val DarkAccent = AppTokens.DarkPrimary

    // Mini Player Dark Theme Bridge
    val DarkMiniPlayerSurface = AppTokens.DarkMiniPlayerSurface
    val DarkMiniPlayerLightShadow = Color.Transparent
    val DarkMiniPlayerDarkShadow = Color.Transparent
    val DarkMiniPlayerTextPrimary = AppTokens.DarkMiniPlayerTextPrimary
    val DarkMiniPlayerTextSecondary = AppTokens.DarkMiniPlayerTextSecondary
    val DarkMiniPlayerAccent = AppTokens.DarkMiniPlayerTextPrimary

    // Radii Bridge
    val RadiusSmall = AppTokens.Radius12
    val RadiusMedium = AppTokens.Radius16
    val RadiusLarge = AppTokens.Radius24
    val RadiusPill = AppTokens.RadiusPill

    // Spacing scale
    val Space4 = AppTokens.Space4
    val Space8 = AppTokens.Space8
    val Space12 = AppTokens.Space12
    val Space16 = AppTokens.Space16
    val Space20 = AppTokens.Space20
    val Space24 = AppTokens.Space24
    val Space32 = AppTokens.Space32

    const val AnimDurationMs = 150

    val LightColors = NeumorphicColors(
        background = LightBackground,
        surface = LightSurface,
        lightShadow = LightLightShadow,
        darkShadow = LightDarkShadow,
        textPrimary = LightTextPrimary,
        textSecondary = LightTextSecondary,
        accent = LightAccent,
        isDark = false,
        miniPlayerSurface = LightMiniPlayerSurface,
        miniPlayerLightShadow = LightMiniPlayerLightShadow,
        miniPlayerDarkShadow = LightMiniPlayerDarkShadow,
        miniPlayerTextPrimary = LightMiniPlayerTextPrimary,
        miniPlayerTextSecondary = LightMiniPlayerTextSecondary,
        miniPlayerAccent = LightMiniPlayerAccent
    )

    val DarkColors = NeumorphicColors(
        background = DarkBackground,
        surface = DarkSurface,
        lightShadow = DarkLightShadow,
        darkShadow = DarkDarkShadow,
        textPrimary = DarkTextPrimary,
        textSecondary = DarkTextSecondary,
        accent = DarkAccent,
        isDark = true,
        miniPlayerSurface = DarkMiniPlayerSurface,
        miniPlayerLightShadow = DarkMiniPlayerLightShadow,
        miniPlayerDarkShadow = DarkMiniPlayerDarkShadow,
        miniPlayerTextPrimary = DarkMiniPlayerTextPrimary,
        miniPlayerTextSecondary = DarkMiniPlayerTextSecondary,
        miniPlayerAccent = DarkMiniPlayerAccent
    )
}

val LocalNeumorphicColors = staticCompositionLocalOf { NeumorphicTokens.LightColors }

object NeumorphicTheme {
    val colors: NeumorphicColors
        @Composable
        get() = LocalNeumorphicColors.current
}

/**
 * Clean Flat Video Overlay Tokens.
 */
object VideoOverlayTokens {
    val ButtonBase = Color(0x73000000)
    val ButtonPressed = Color(0xA6000000)
    val Accent = Color.White
    val IconNormal = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF) // White 70%
    val ScrimTop = Color(0x59000000) // Black ~35%
    val ScrimBottom = Color(0x59000000) // Black ~35%
    val TrackBackground = Color(0x4DFFFFFF) // White ~30%
    val ThumbNormal = Color(0xFFFFFFFF)
}
