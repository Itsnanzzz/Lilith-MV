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
    val isDark: Boolean
)

object AppTokens {
    // 1. Light Palette (From Reference: Minimal, Airy, Monochrome)
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

    // 2. Dark Palette (Derived with Same Clean Aesthetic)
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
            AppAccent.WHITE -> if (isDark) {
                AccentColorSet(
                    accent = Color(0xFFF2F4F8),
                    onAccent = Color(0xFF171C26),
                    accentText = Color(0xFFF2F4F8),
                    miniPlayerProgress = Color(0xFFFFFFFF)
                )
            } else {
                AccentColorSet(
                    accent = Color(0xFF171C26),
                    onAccent = Color(0xFFFFFFFF),
                    accentText = Color(0xFF171C26),
                    miniPlayerProgress = Color(0xFFFFFFFF)
                )
            }
            AppAccent.BLUE -> if (isDark) {
                AccentColorSet(
                    accent = Color(0xFF60A5FA),
                    onAccent = Color(0xFF0E1117),
                    accentText = Color(0xFF60A5FA),
                    miniPlayerProgress = Color(0xFF60A5FA)
                )
            } else {
                AccentColorSet(
                    accent = Color(0xFF2563EB),
                    onAccent = Color(0xFFFFFFFF),
                    accentText = Color(0xFF2563EB),
                    miniPlayerProgress = Color(0xFF60A5FA)
                )
            }
            AppAccent.GREEN -> if (isDark) {
                AccentColorSet(
                    accent = Color(0xFF4ADE80),
                    onAccent = Color(0xFF0E1117),
                    accentText = Color(0xFF4ADE80),
                    miniPlayerProgress = Color(0xFF4ADE80)
                )
            } else {
                AccentColorSet(
                    accent = Color(0xFF15803D),
                    onAccent = Color(0xFFFFFFFF),
                    accentText = Color(0xFF15803D),
                    miniPlayerProgress = Color(0xFF4ADE80)
                )
            }
            AppAccent.YELLOW -> if (isDark) {
                AccentColorSet(
                    accent = Color(0xFFFACC15),
                    onAccent = Color(0xFF0E1117),
                    accentText = Color(0xFFFACC15),
                    miniPlayerProgress = Color(0xFFFACC15)
                )
            } else {
                AccentColorSet(
                    accent = Color(0xFFFACC15),
                    onAccent = Color(0xFF171C26),
                    accentText = Color(0xFFA16207),
                    miniPlayerProgress = Color(0xFFFACC15)
                )
            }
            AppAccent.RED -> if (isDark) {
                AccentColorSet(
                    accent = Color(0xFFF87171),
                    onAccent = Color(0xFF0E1117),
                    accentText = Color(0xFFF87171),
                    miniPlayerProgress = Color(0xFFF87171)
                )
            } else {
                AccentColorSet(
                    accent = Color(0xFFDC2626),
                    onAccent = Color(0xFFFFFFFF),
                    accentText = Color(0xFFDC2626),
                    miniPlayerProgress = Color(0xFFF87171)
                )
            }
            AppAccent.ORANGE -> if (isDark) {
                AccentColorSet(
                    accent = Color(0xFFFB923C),
                    onAccent = Color(0xFF0E1117),
                    accentText = Color(0xFFFB923C),
                    miniPlayerProgress = Color(0xFFFB923C)
                )
            } else {
                AccentColorSet(
                    accent = Color(0xFFC2410C),
                    onAccent = Color(0xFFFFFFFF),
                    accentText = Color(0xFFC2410C),
                    miniPlayerProgress = Color(0xFFFB923C)
                )
            }
            AppAccent.PURPLE -> if (isDark) {
                AccentColorSet(
                    accent = Color(0xFFA78BFA),
                    onAccent = Color(0xFF0E1117),
                    accentText = Color(0xFFA78BFA),
                    miniPlayerProgress = Color(0xFFA78BFA)
                )
            } else {
                AccentColorSet(
                    accent = Color(0xFF7C3AED),
                    onAccent = Color(0xFFFFFFFF),
                    accentText = Color(0xFF7C3AED),
                    miniPlayerProgress = Color(0xFFA78BFA)
                )
            }
        }
    }

    val LightColors = createColors(isDark = false, accent = AppAccent.WHITE)
    val DarkColors = createColors(isDark = true, accent = AppAccent.WHITE)

    fun createColors(isDark: Boolean, accent: AppAccent): AppColors {
        val accentSet = resolveAccentColors(accent, isDark)
        return if (isDark) {
            AppColors(
                background = DarkBackground,
                surface = DarkSurface,
                surfaceMuted = DarkSurfaceMuted,
                hairline = DarkHairline,
                textPrimary = DarkTextPrimary,
                textSecondary = DarkTextSecondary,
                disabled = DarkDisabled,
                primary = DarkPrimary,
                onPrimary = DarkOnPrimary,
                miniPlayerSurface = DarkMiniPlayerSurface,
                miniPlayerTextPrimary = DarkMiniPlayerTextPrimary,
                miniPlayerTextSecondary = DarkMiniPlayerTextSecondary,
                destructive = DarkDestructive,
                accent = accentSet.accent,
                onAccent = accentSet.onAccent,
                accentText = accentSet.accentText,
                miniPlayerProgress = accentSet.miniPlayerProgress,
                isDark = true
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
                primary = LightPrimary,
                onPrimary = LightOnPrimary,
                miniPlayerSurface = LightMiniPlayerSurface,
                miniPlayerTextPrimary = LightMiniPlayerTextPrimary,
                miniPlayerTextSecondary = LightMiniPlayerTextSecondary,
                destructive = LightDestructive,
                accent = accentSet.accent,
                onAccent = accentSet.onAccent,
                accentText = accentSet.accentText,
                miniPlayerProgress = accentSet.miniPlayerProgress,
                isDark = false
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
