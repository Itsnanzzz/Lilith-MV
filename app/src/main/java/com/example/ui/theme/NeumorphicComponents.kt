package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainNavigationTab
import com.example.R

/**
 * Clean Flat modifier replacing Neumorphic shadows with simple background color and shape.
 * Zero blur, zero glow, zero shadows.
 */
fun Modifier.neumorphic(
    shape: Shape = RoundedCornerShape(12.dp),
    level: NeumorphLevel = NeumorphLevel.MEDIUM,
    style: NeumorphStyle = NeumorphStyle.RAISED,
    lightShadowColorOverride: Color? = null,
    darkShadowColorOverride: Color? = null,
    backgroundColorOverride: Color? = null
): Modifier = this.then(
    if (backgroundColorOverride != null) {
        Modifier.background(backgroundColorOverride, shape)
    } else {
        Modifier
    }
)

/**
 * Flat Surface container.
 */
@Composable
fun NeumorphicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    level: NeumorphLevel = NeumorphLevel.MEDIUM,
    style: NeumorphStyle = NeumorphStyle.RAISED,
    content: @Composable () -> Unit
) {
    val colors = NeumorphicTheme.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            content()
        }
    }
}

/**
 * Flat Card container.
 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    level: NeumorphLevel = NeumorphLevel.MEDIUM,
    style: NeumorphStyle = NeumorphStyle.RAISED,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .then(clickModifier)
            .clip(shape)
            .background(colors.surface)
            .then(if (contentPadding > 0.dp) Modifier.padding(contentPadding) else Modifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            content()
        }
    }
}

/**
 * Flat Icon Button with 48dp minimum touch target and clean scale feedback.
 */
@Composable
fun NeumorphicIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 48.dp,
    level: NeumorphLevel = NeumorphLevel.MEDIUM,
    isActive: Boolean = false,
    enabled: Boolean = true,
    lightShadowColorOverride: Color? = null,
    darkShadowColorOverride: Color? = null,
    backgroundColorOverride: Color? = null,
    testTag: String? = null,
    icon: @Composable () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val context = LocalContext.current
    val isAnimationDisabled = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!isAnimationDisabled && isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "btn_scale"
    )

    val appColors = AppThemeTokens.colors
    val iconTint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.4f)
        isActive -> appColors.accentText
        else -> colors.textPrimary
    }

    val baseModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        modifier = baseModifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .then(if (backgroundColorOverride != null) Modifier.background(backgroundColorOverride) else Modifier)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides iconTint) {
            icon()
        }
    }
}

@Composable
fun NeumorphicToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 48.dp,
    icon: @Composable () -> Unit
) {
    NeumorphicIconButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        contentDescription = contentDescription,
        size = size,
        isActive = checked,
        icon = icon
    )
}

@Composable
fun NeumorphicToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = 48.dp,
    icon: @Composable (isChecked: Boolean) -> Unit
) {
    NeumorphicIconButton(
        onClick = { onCheckedChange(!checked) },
        modifier = modifier,
        contentDescription = contentDescription,
        size = size,
        isActive = checked,
        icon = { icon(checked) }
    )
}

/**
 * Flat Slider: 4dp track in hairline, solid accent/primary progress, 12dp thumb, 48dp touch height.
 */
@Composable
fun NeumorphicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    accentColorOverride: Color? = null,
    onValueChangeFinished: (() -> Unit)? = null,
    testTag: String? = null
) {
    val colors = NeumorphicTheme.colors
    val appColors = AppThemeTokens.colors
    val accent = accentColorOverride ?: appColors.foregroundAccent
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalizedVal = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(normalizedVal) }

    val activeFraction = if (isDragging) dragFraction else normalizedVal

    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val finalVal = valueRange.start + dragFraction * rangeSpan
                        onValueChange(finalVal)
                        onValueChangeFinished?.invoke()
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            }
            .pointerInput(enabled, valueRange) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        val finalVal = valueRange.start + frac * rangeSpan
                        onValueChange(finalVal)
                        onValueChangeFinished?.invoke()
                    }
                )
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val thumbOffset = totalWidth * activeFraction

        // Hairline track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (colors.isDark) Color(0xFF262C3A) else Color(0xFFE6E8EE))
        )

        // Progress track
        Box(
            modifier = Modifier
                .fillMaxWidth(activeFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )

        // 12dp Thumb
        Box(
            modifier = Modifier
                .offset(x = (thumbOffset - 6.dp).coerceIn(0.dp, totalWidth - 12.dp))
                .size(12.dp)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

/**
 * Flat Switch with clean slide transition.
 */
@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AppSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier
    )
}

/**
 * Flat Chip for tabs and filters.
 */
@Composable
fun NeumorphicChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val appColors = AppThemeTokens.colors
    val containerColor = if (selected) colors.accent else (if (colors.isDark) Color(0xFF1A1F2B) else Color(0xFFF3F4F7))
    val contentColor = if (selected) appColors.onAccent else colors.textSecondary

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .then(
                if (selected && appColors.isLightWhiteAccent) {
                    Modifier.border(1.dp, appColors.hairline, RoundedCornerShape(999.dp))
                } else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides TextStyle(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
        ) {
            label()
        }
    }
}

/**
 * Flat Text Field for search.
 */
@Composable
fun NeumorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    val colors = NeumorphicTheme.colors
    val bg = if (colors.isDark) Color(0xFF1A1F2B) else Color(0xFFF3F4F7)

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (leadingIcon != null) {
                CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                    leadingIcon()
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder != null) {
                    CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                        placeholder()
                    }
                }

                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(AppThemeTokens.colors.foregroundAccent),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                CompositionLocalProvider(LocalContentColor provides colors.textSecondary) {
                    trailingIcon()
                }
            }
        }
    }
}

@Composable
fun NeumorphicTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true
) {
    NeumorphicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = { Text(placeholder) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine
    )
}

/**
 * Flat Bottom Navigation Bar (Docked full width or floating pill).
 */
@Composable
fun NeumorphicFloatingBottomBar(
    selectedTab: MainNavigationTab,
    onTabSelected: (MainNavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphicTheme.colors
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MainNavigationTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val iconVector = when (tab) {
                    MainNavigationTab.MUSIC -> if (isSelected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote
                    MainNavigationTab.VIDEO -> if (isSelected) Icons.Filled.Videocam else Icons.Outlined.Videocam
                    MainNavigationTab.SETTINGS -> if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings
                }
                val label = when (tab) {
                    MainNavigationTab.MUSIC -> stringResource(R.string.nav_music)
                    MainNavigationTab.VIDEO -> stringResource(R.string.nav_video)
                    MainNavigationTab.SETTINGS -> stringResource(R.string.nav_settings)
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable(
                            role = Role.Tab,
                            onClick = {
                                if (!isSelected) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTabSelected(tab)
                                }
                            }
                        )
                        .semantics {
                            this.selected = isSelected
                            this.contentDescription = label
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = label,
                        tint = if (isSelected) AppThemeTokens.colors.accentText else colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun NeumorphicBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = NeumorphicTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(colors.surface),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun RowScope.NeumorphicBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (isSelected: Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val colors = NeumorphicTheme.colors
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .weight(1f)
            .height(48.dp)
            .clickable(
                role = Role.Tab,
                onClick = {
                    if (!selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                }
            )
            .semantics {
                this.selected = selected
                this.contentDescription = contentDescription ?: label
            },
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (selected) AppThemeTokens.colors.accentText else colors.textSecondary
        ) {
            icon(selected)
        }
    }
}

@Composable
fun RowScope.NeumorphicBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    NeumorphicBottomBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp)) },
        label = label,
        modifier = modifier,
        contentDescription = contentDescription
    )
}

// =========================================================================
// CLEAN FLAT PREVIEWS (REPLACING OLD NEUMORPHIC PREVIEWS)
// =========================================================================

@Preview(name = "Clean Components - Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewFlatComponentsBridgeLight() {
    LilithTheme(darkTheme = false) {
        PreviewFlatComponentsLight()
    }
}

@Preview(name = "Clean Components - Dark", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun PreviewFlatComponentsBridgeDark() {
    LilithTheme(darkTheme = true) {
        PreviewFlatComponentsDark()
    }
}
