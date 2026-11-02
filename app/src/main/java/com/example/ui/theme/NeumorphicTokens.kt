package com.example.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
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
    val miniPlayerLightShadow: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerLightShadow else NeumorphicTokens.LightMiniPlayerLightShadow,
    val miniPlayerDarkShadow: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerDarkShadow else NeumorphicTokens.LightMiniPlayerDarkShadow,
    val miniPlayerTextPrimary: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerTextPrimary else NeumorphicTokens.LightMiniPlayerTextPrimary,
    val miniPlayerTextSecondary: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerTextSecondary else NeumorphicTokens.LightMiniPlayerTextSecondary,
    val miniPlayerAccent: Color = if (isDark) NeumorphicTokens.DarkMiniPlayerAccent else NeumorphicTokens.LightMiniPlayerAccent
)

object NeumorphicTokens {
    // Light Theme
    val LightBackground = Color(0xFFE0E5EC)
    val LightSurface = Color(0xFFE0E5EC)
    val LightLightShadow = Color(0xFFFFFFFF)
    val LightDarkShadow = Color(0xFFA3B1C6)
    val LightTextPrimary = Color(0xFF3D4852)
    val LightTextSecondary = Color(0xFF6B7785)
    val LightAccent = Color(0xFF6C63FF)

    // Mini Player Light Theme Tokens
    // Soft lavender-tinted surface, slightly darker than the list, harmonizing with accent #6C63FF
    val LightMiniPlayerSurface = Color(0xFFD4D7EC)
    val LightMiniPlayerLightShadow = Color(0xFFE8EAF8)
    val LightMiniPlayerDarkShadow = Color(0xFF9DA3C4)
    val LightMiniPlayerTextPrimary = Color(0xFF3D4852) // 6.47:1 contrast ratio against #D4D7EC (exceeds WCAG AAA >= 4.5:1)
    val LightMiniPlayerTextSecondary = Color(0xFF525D6B) // 4.80:1 contrast ratio against #D4D7EC (exceeds WCAG AA >= 4.5:1)
    val LightMiniPlayerAccent = Color(0xFF5A50ED) // 3.65:1 contrast ratio against #D4D7EC (exceeds graphical/UI >= 3:1)

    // Dark Theme
    val DarkBackground = Color(0xFF2B2F36)
    val DarkSurface = Color(0xFF2B2F36)
    val DarkLightShadow = Color(0xFF373C45)
    val DarkDarkShadow = Color(0xFF1F2227)
    val DarkTextPrimary = Color(0xFFE6E9EE)
    val DarkTextSecondary = Color(0xFFA0A8B4)
    val DarkAccent = Color(0xFF8E86FF)

    // Mini Player Dark Theme Tokens
    // Subtly toned down: barely lighter than list cards (#2B2F36), low saturation cool tint
    val DarkMiniPlayerSurface = Color(0xFF31353F)
    val DarkMiniPlayerLightShadow = Color(0xFF3A3F4A)
    val DarkMiniPlayerDarkShadow = Color(0xFF23262D)
    val DarkMiniPlayerTextPrimary = Color(0xFFE6E9EE) // 10.0:1 contrast ratio against #31353F (exceeds WCAG AAA >= 4.5:1)
    val DarkMiniPlayerTextSecondary = Color(0xFFA0A8B4) // 5.10:1 contrast ratio against #31353F (exceeds WCAG AA >= 4.5:1)
    val DarkMiniPlayerAccent = Color(0xFF8E86FF) // 4.10:1 contrast ratio against #31353F (exceeds graphical/UI >= 3:1)

    // Shadow Dimensions
    fun shadowOffset(level: NeumorphLevel): Dp = when (level) {
        NeumorphLevel.SMALL -> 3.dp
        NeumorphLevel.MEDIUM -> 6.dp
        NeumorphLevel.LARGE -> 10.dp
    }

    fun shadowBlur(level: NeumorphLevel): Dp = when (level) {
        NeumorphLevel.SMALL -> 5.dp
        NeumorphLevel.MEDIUM -> 10.dp
        NeumorphLevel.LARGE -> 16.dp
    }

    // Corner Radii
    val RadiusSmall = 12.dp
    val RadiusMedium = 18.dp
    val RadiusLarge = 24.dp
    val RadiusPill = 999.dp

    // Spacing scale
    val Space4 = 4.dp
    val Space8 = 8.dp
    val Space12 = 12.dp
    val Space16 = 16.dp
    val Space24 = 24.dp

    // Animation
    const val AnimDurationMs = 180
    val stateAnimationSpec: AnimationSpec<Float> = tween(durationMillis = AnimDurationMs)

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
 * Dedicated tokens for the full-screen video player overlay.
 * NOTE: This is an intentional exception to the neumorphic design rules. Neumorphism
 * (surface-matching colors, inner/outer raised shadows, blurs) does not read well on top
 * of dynamic, moving video frames. The video player overlay uses clean, flat, translucent
 * elements with high-contrast text and icons to ensure legibility and peak performance.
 */
object VideoOverlayTokens {
    val ButtonBase = Color(0x73000000) // Translucent black (~45% alpha)
    val ButtonPressed = Color(0xA6000000) // Translucent black on press (~65% alpha)
    val Accent = Color(0xFF8E86FF) // Crisp high-contrast purple accent for active states
    val IconNormal = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF) // White 70%
    val ScrimTop = Color(0x99000000) // Black ~60%
    val ScrimBottom = Color(0xB3000000) // Black ~70%
    val TrackBackground = Color(0x4DFFFFFF) // White ~30%
    val ThumbNormal = Color(0xFFFFFFFF)
}
