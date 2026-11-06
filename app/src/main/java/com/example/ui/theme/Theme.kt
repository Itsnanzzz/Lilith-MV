package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.data.AppAccent

private val FlatLightColorScheme = lightColorScheme(
    primary = AppTokens.LightPrimary,
    onPrimary = AppTokens.LightOnPrimary,
    primaryContainer = AppTokens.LightSurfaceMuted,
    onPrimaryContainer = AppTokens.LightTextPrimary,
    secondary = AppTokens.LightPrimary,
    onSecondary = AppTokens.LightOnPrimary,
    secondaryContainer = AppTokens.LightSurfaceMuted,
    onSecondaryContainer = AppTokens.LightTextPrimary,
    background = AppTokens.LightBackground,
    onBackground = AppTokens.LightTextPrimary,
    surface = AppTokens.LightSurface,
    onSurface = AppTokens.LightTextPrimary,
    surfaceVariant = AppTokens.LightSurfaceMuted,
    onSurfaceVariant = AppTokens.LightTextSecondary,
    outline = AppTokens.LightHairline,
    outlineVariant = AppTokens.LightDisabled,
    error = AppTokens.LightDestructive,
    onError = Color.White
)

private val FlatDarkColorScheme = darkColorScheme(
    primary = AppTokens.DarkPrimary,
    onPrimary = AppTokens.DarkOnPrimary,
    primaryContainer = AppTokens.DarkSurfaceMuted,
    onPrimaryContainer = AppTokens.DarkTextPrimary,
    secondary = AppTokens.DarkPrimary,
    onSecondary = AppTokens.DarkOnPrimary,
    secondaryContainer = AppTokens.DarkSurfaceMuted,
    onSecondaryContainer = AppTokens.DarkTextPrimary,
    background = AppTokens.DarkBackground,
    onBackground = AppTokens.DarkTextPrimary,
    surface = AppTokens.DarkSurface,
    onSurface = AppTokens.DarkTextPrimary,
    surfaceVariant = AppTokens.DarkSurfaceMuted,
    onSurfaceVariant = AppTokens.DarkTextSecondary,
    outline = AppTokens.DarkHairline,
    outlineVariant = AppTokens.DarkDisabled,
    error = AppTokens.DarkDestructive,
    onError = AppTokens.DarkOnPrimary
)

@Composable
fun LilithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appAccent: AppAccent = AppAccent.WHITE,
    // Material You dynamic color is disabled per minimal flat design requirements
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseAppColors = AppTokens.createColors(isDark = darkTheme, accent = appAccent)
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val animSpec = if (isAnimationDisabled) snap<Color>() else tween<Color>(durationMillis = AppTokens.AnimDurationMedium)

    val animatedAccent by animateColorAsState(targetValue = baseAppColors.accent, animationSpec = animSpec, label = "accent_anim")
    val animatedOnAccent by animateColorAsState(targetValue = baseAppColors.onAccent, animationSpec = animSpec, label = "on_accent_anim")
    val animatedAccentText by animateColorAsState(targetValue = baseAppColors.accentText, animationSpec = animSpec, label = "accent_text_anim")
    val animatedMiniPlayerProgress by animateColorAsState(targetValue = baseAppColors.miniPlayerProgress, animationSpec = animSpec, label = "mini_progress_anim")

    val appColors = baseAppColors.copy(
        primary = animatedAccent,
        onPrimary = animatedOnAccent,
        accent = animatedAccent,
        onAccent = animatedOnAccent,
        accentText = animatedAccentText,
        miniPlayerProgress = animatedMiniPlayerProgress
    )

    val colorScheme = if (darkTheme) {
        FlatDarkColorScheme.copy(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            secondary = appColors.accent,
            onSecondary = appColors.onAccent
        )
    } else {
        FlatLightColorScheme.copy(
            primary = appColors.accent,
            onPrimary = appColors.onAccent,
            secondary = appColors.accent,
            onSecondary = appColors.onAccent
        )
    }

    // Backward compatibility mapping for Neumorphic bridge during migration
    val bridgedNeumorphicColors = NeumorphicColors(
        background = appColors.background,
        surface = appColors.surface,
        lightShadow = Color.Transparent,
        darkShadow = Color.Transparent,
        textPrimary = appColors.textPrimary,
        textSecondary = appColors.textSecondary,
        accent = appColors.accent,
        isDark = darkTheme,
        miniPlayerSurface = appColors.miniPlayerSurface,
        miniPlayerLightShadow = Color.Transparent,
        miniPlayerDarkShadow = Color.Transparent,
        miniPlayerTextPrimary = appColors.miniPlayerTextPrimary,
        miniPlayerTextSecondary = appColors.miniPlayerTextSecondary,
        miniPlayerAccent = appColors.accent
    )

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalNeumorphicColors provides bridgedNeumorphicColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// Alias for backwards compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appAccent: AppAccent = AppAccent.WHITE,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = LilithTheme(darkTheme = darkTheme, appAccent = appAccent, dynamicColor = dynamicColor, content = content)
