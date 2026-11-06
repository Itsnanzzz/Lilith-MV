package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import com.example.data.AppAccent

@Immutable
data class AccentColorSet(
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val miniPlayerProgress: Color
)

@Immutable
data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val disabled: Color,
    val primary: Color,
    val onPrimary: Color,
    val miniPlayerSurface: Color,
    val miniPlayerTextPrimary: Color,
    val miniPlayerTextSecondary: Color,
    val destructive: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val miniPlayerProgress: Color,
    val isDark: Boolean,
    val isLightWhiteAccent: Boolean = false
) {
    /**
     * Foreground accent for thin strokes/indicators on app background:
     * preserves exact accent in Dark mode and for all chromatic colors,
     * while falling back to dark ink when White (#ffffff) is paired with Light mode.
     */
    val foregroundAccent: Color
        get() = if (isLightWhiteAccent) accentText else accent
}

object AppTokens {
    // 1. Light Palette (Minimal, Airy, Monochrome base)
    val LightBackground = Color(0xFFFFFFFF)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceMuted = Color(0xFFF3F4F7)
    val LightHairline = Color(0xFFE6E8EE)
    val LightTextPrimary = Color(0xFF171C26)
    val LightTextSecondary = Color(0xFF6C7689)
    val LightDisabled = Color(0xFFA4AAB7)
    val LightPrimary = Color(0xFF171C26)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightMiniPlayerSurface = Color(0xFF171C26)
    val LightMiniPlayerTextPrimary = Color(0xFFFFFFFF)
    val LightMiniPlayerTextSecondary = Color(0xFFA4AAB7)
    val LightDestructive = Color(0xFFD64545)
    val LightAccent = LightPrimary

    // 2. Dark Palette
    val DarkBackground = Color(0xFF0E1117)
    val DarkSurface = Color(0xFF0E1117)
    val DarkSurfaceMuted = Color(0xFF1A1F2B)
    val DarkHairline = Color(0xFF262C3A)
    val DarkTextPrimary = Color(0xFFF2F4F8)
    val DarkTextSecondary = Color(0xFFA4AAB7)
    val DarkDisabled = Color(0xFF6C7689)
    val DarkPrimary = Color(0xFFF2F4F8)
    val DarkOnPrimary = Color(0xFF171C26)
    val DarkMiniPlayerSurface = Color(0xFF232938)
    val DarkMiniPlayerTextPrimary = Color(0xFFFFFFFF)
    val DarkMiniPlayerTextSecondary = Color(0xFFA4AAB7)
    val DarkDestructive = Color(0xFFFF6B6B)
    val DarkAccent = DarkPrimary

    fun resolveAccentColors(accent: AppAccent, isDark: Boolean): AccentColorSet {
        return when (accent) {
            AppAccent.WHITE -> AccentColorSet(
                accent = AccentWhite,
                onAccent = Color(0xFF171C26),
                accentText = if (isDark) AccentWhite else Color(0xFF171C26),
                miniPlayerProgress = AccentWhite
            )
            AppAccent.YELLOW -> AccentColorSet(
                accent = AccentYellow,
                onAccent = Color(0xFF171C26),
                accentText = if (isDark) AccentYellow else Color(0xFF997B00),
                miniPlayerProgress = AccentYellow
            )
            AppAccent.BLUE -> AccentColorSet(
                accent = AccentBlue,
                onAccent = Color(0xFFFFFFFF),
                accentText = if (isDark) Color(0xFF6B78FF) else AccentBlue,
                miniPlayerProgress = AccentBlue
            )
            AppAccent.GREEN -> AccentColorSet(
                accent = AccentGreen,
                onAccent = Color(0xFF0E1117),
                accentText = if (isDark) AccentGreen else Color(0xFF12873B),
                miniPlayerProgress = AccentGreen
            )
            AppAccent.RED -> AccentColorSet(
                accent = AccentRed,
                onAccent = Color(0xFFFFFFFF),
                accentText = if (isDark) Color(0xFFFF5A54) else AccentRed,
                miniPlayerProgress = AccentRed
            )
            AppAccent.ORANGE -> AccentColorSet(
                accent = AccentOrange,
                onAccent = Color(0xFF0E1117),
                accentText = if (isDark) AccentOrange else Color(0xFFB55600),
                miniPlayerProgress = AccentOrange
            )
            AppAccent.PURPLE -> AccentColorSet(
                accent = AccentPurple,
                onAccent = Color(0xFFFFFFFF),
                accentText = if (isDark) Color(0xFFC965F2) else AccentPurple,
                miniPlayerProgress = AccentPurple
            )
            AppAccent.CYAN -> AccentColorSet(
                accent = AccentCyan,
                onAccent = Color(0xFF171C26),
                accentText = if (isDark) AccentCyan else Color(0xFF007E82),
                miniPlayerProgress = AccentCyan
            )
        }
    }

    val LightColors = createColors(isDark = false, accent = AppAccent.WHITE)
    val DarkColors = createColors(isDark = true, accent = AppAccent.WHITE)

    fun createColors(isDark: Boolean, accent: AppAccent): AppColors {
        val accentSet = resolveAccentColors(accent, isDark)
        val isLightWhite = !isDark && accent == AppAccent.WHITE
        return if (isDark) {
            AppColors(
                background = DarkBackground,
                surface = DarkSurface,
                surfaceMuted = DarkSurfaceMuted,
                hairline = DarkHairline,
                textPrimary = DarkTextPrimary,
                textSecondary = DarkTextSecondary,
                disabled = DarkDisabled,
                primary = accentSet.accent,
                onPrimary = accentSet.onAccent,
                miniPlayerSurface = DarkMiniPlayerSurface,
                miniPlayerTextPrimary = DarkMiniPlayerTextPrimary,
                miniPlayerTextSecondary = DarkMiniPlayerTextSecondary,
                destructive = DarkDestructive,
                accent = accentSet.accent,
                onAccent = accentSet.onAccent,
                accentText = accentSet.accentText,
                miniPlayerProgress = accentSet.miniPlayerProgress,
                isDark = true,
                isLightWhiteAccent = false
            )
        } else {
            AppColors(
                background = LightBackground,
                surface = LightSurface,
                surfaceMuted = LightSurfaceMuted,
                hairline = LightHairline,
                textPrimary = LightTextPrimary,
                textSecondary = LightTextSecondary,
                disabled = LightDisabled,
                primary = accentSet.accent,
                onPrimary = accentSet.onAccent,
                miniPlayerSurface = LightMiniPlayerSurface,
                miniPlayerTextPrimary = LightMiniPlayerTextPrimary,
                miniPlayerTextSecondary = LightMiniPlayerTextSecondary,
                destructive = LightDestructive,
                accent = accentSet.accent,
                onAccent = accentSet.onAccent,
                accentText = accentSet.accentText,
                miniPlayerProgress = accentSet.miniPlayerProgress,
                isDark = false,
                isLightWhiteAccent = isLightWhite
            )
        }
    }

    // Spacing Scale
    val Space4: Dp = 4.dp
    val Space8: Dp = 8.dp
    val Space12: Dp = 12.dp
    val Space16: Dp = 16.dp
    val Space20: Dp = 20.dp
    val Space24: Dp = 24.dp
    val Space32: Dp = 32.dp

    // Screen horizontal padding
    val ScreenHorizontalPadding: Dp = 20.dp

    // Corner Radii
    val Radius8: Dp = 8.dp   // Small thumbnails, mini player artwork
    val Radius10: Dp = 10.dp // Song list artwork
    val Radius12: Dp = 12.dp // Rows, inputs, highlights, snackbars
    val Radius16: Dp = 16.dp // Menus, groups, mini player card
    val Radius20: Dp = 20.dp // Collection header artwork
    val Radius24: Dp = 24.dp // Dialogs, bottom sheets
    val Radius28: Dp = 28.dp // Now Playing artwork
    val RadiusPill: Dp = 999.dp // Pills, circles

    // Animation Durations
    const val AnimDurationShort: Int = 150
    const val AnimDurationMedium: Int = 200
}

val LocalAppColors = staticCompositionLocalOf { AppTokens.LightColors }

object AppThemeTokens {
    val colors: AppColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppColors.current
}
