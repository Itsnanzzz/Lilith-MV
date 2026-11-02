package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val NeumorphicDarkColorScheme = darkColorScheme(
    primary = NeumorphicTokens.DarkAccent,
    onPrimary = NeumorphicTokens.DarkBackground,
    primaryContainer = NeumorphicTokens.DarkSurface,
    onPrimaryContainer = NeumorphicTokens.DarkAccent,
    secondary = NeumorphicTokens.DarkAccent,
    onSecondary = NeumorphicTokens.DarkBackground,
    secondaryContainer = NeumorphicTokens.DarkSurface,
    onSecondaryContainer = NeumorphicTokens.DarkAccent,
    background = NeumorphicTokens.DarkBackground,
    onBackground = NeumorphicTokens.DarkTextPrimary,
    surface = NeumorphicTokens.DarkSurface,
    onSurface = NeumorphicTokens.DarkTextPrimary,
    surfaceVariant = NeumorphicTokens.DarkSurface,
    onSurfaceVariant = NeumorphicTokens.DarkTextSecondary,
    outline = NeumorphicTokens.DarkDarkShadow,
    outlineVariant = NeumorphicTokens.DarkLightShadow
)

private val NeumorphicLightColorScheme = lightColorScheme(
    primary = NeumorphicTokens.LightAccent,
    onPrimary = NeumorphicTokens.LightBackground,
    primaryContainer = NeumorphicTokens.LightSurface,
    onPrimaryContainer = NeumorphicTokens.LightAccent,
    secondary = NeumorphicTokens.LightAccent,
    onSecondary = NeumorphicTokens.LightBackground,
    secondaryContainer = NeumorphicTokens.LightSurface,
    onSecondaryContainer = NeumorphicTokens.LightAccent,
    background = NeumorphicTokens.LightBackground,
    onBackground = NeumorphicTokens.LightTextPrimary,
    surface = NeumorphicTokens.LightSurface,
    onSurface = NeumorphicTokens.LightTextPrimary,
    surfaceVariant = NeumorphicTokens.LightSurface,
    onSurfaceVariant = NeumorphicTokens.LightTextSecondary,
    outline = NeumorphicTokens.LightDarkShadow,
    outlineVariant = NeumorphicTokens.LightLightShadow
)

@Composable
fun LilithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You dynamic color is disabled per neumorphism design requirements
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val neumorphicColors = if (darkTheme) NeumorphicTokens.DarkColors else NeumorphicTokens.LightColors
    val colorScheme = if (darkTheme) NeumorphicDarkColorScheme else NeumorphicLightColorScheme

    CompositionLocalProvider(
        LocalNeumorphicColors provides neumorphicColors
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
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = LilithTheme(darkTheme = darkTheme, dynamicColor = dynamicColor, content = content)
