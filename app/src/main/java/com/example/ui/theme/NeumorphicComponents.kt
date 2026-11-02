package com.example.ui.theme

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import com.example.MainNavigationTab
import com.example.R
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modifier that applies true Neumorphic depth (Raised, Pressed/Inset, or Flat)
 * using drawWithCache and native Canvas Paint shadow layers.
 */
fun Modifier.neumorphic(
    shape: Shape = RoundedCornerShape(NeumorphicTokens.RadiusMedium),
    level: NeumorphLevel = NeumorphLevel.MEDIUM,
    style: NeumorphStyle = NeumorphStyle.RAISED,
    lightShadowColorOverride: Color? = null,
    darkShadowColorOverride: Color? = null,
    backgroundColorOverride: Color? = null
): Modifier = this.then(
    Modifier.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val offsetPx = when (level) {
            NeumorphLevel.SMALL -> 3.dp.toPx()
            NeumorphLevel.MEDIUM -> 6.dp.toPx()
            NeumorphLevel.LARGE -> 10.dp.toPx()
        }
        val blurPx = when (level) {
            NeumorphLevel.SMALL -> 5.dp.toPx()
            NeumorphLevel.MEDIUM -> 10.dp.toPx()
            NeumorphLevel.LARGE -> 16.dp.toPx()
        }

        val baseAndroidPath = Path()
        when (outline) {
            is Outline.Rectangle -> {
                baseAndroidPath.addRect(
                    outline.rect.left,
                    outline.rect.top,
                    outline.rect.right,
                    outline.rect.bottom,
                    Path.Direction.CW
                )
            }
            is Outline.Rounded -> {
                val rr = outline.roundRect
                baseAndroidPath.addRoundRect(
                    RectF(rr.left, rr.top, rr.right, rr.bottom),
                    floatArrayOf(
                        rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y,
                        rr.topRightCornerRadius.x, rr.topRightCornerRadius.y,
                        rr.bottomRightCornerRadius.x, rr.bottomRightCornerRadius.y,
                        rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y
                    ),
                    Path.Direction.CW
                )
            }
            is Outline.Generic -> {
                baseAndroidPath.set(outline.path.asAndroidPath())
            }
        }

        onDrawBehind {
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                val colors = if (backgroundColorOverride != null) {
                    NeumorphicColors(
                        background = backgroundColorOverride,
                        surface = backgroundColorOverride,
                        lightShadow = lightShadowColorOverride ?: Color.White.copy(alpha = 0.6f),
                        darkShadow = darkShadowColorOverride ?: Color.Black.copy(alpha = 0.2f),
                        textPrimary = Color.White,
                        textSecondary = Color.White.copy(alpha = 0.7f),
                        accent = NeumorphicTokens.DarkAccent,
                        isDark = true
                    )
                } else {
                    NeumorphicTokens.LightColors // fallback, overridden below in runtime composables via theme
                }

                val surfaceCol = backgroundColorOverride ?: colors.surface
                val lightShadow = lightShadowColorOverride ?: colors.lightShadow
                val darkShadow = darkShadowColorOverride ?: colors.darkShadow

                when (style) {
                    NeumorphStyle.FLAT -> {
                        val fillPaint = Paint().apply {
                            isAntiAlias = true
                            color = surfaceCol.toArgb()
                            this.style = Paint.Style.FILL
                        }
                        native.drawPath(baseAndroidPath, fillPaint)
                    }

                    NeumorphStyle.RAISED -> {
                        // 1. Dark Shadow (Bottom-Right)
                        val darkPaint = Paint().apply {
                            isAntiAlias = true
                            color = surfaceCol.toArgb()
                            this.style = Paint.Style.FILL
                            setShadowLayer(blurPx, offsetPx, offsetPx, darkShadow.toArgb())
                        }
                        native.drawPath(baseAndroidPath, darkPaint)

                        // 2. Light Shadow (Top-Left)
                        val lightPaint = Paint().apply {
                            isAntiAlias = true
                            color = surfaceCol.toArgb()
                            this.style = Paint.Style.FILL
                            setShadowLayer(blurPx, -offsetPx, -offsetPx, lightShadow.toArgb())
                        }
                        native.drawPath(baseAndroidPath, lightPaint)

                        // 3. Crisp Surface Fill
                        val basePaint = Paint().apply {
                            isAntiAlias = true
                            color = surfaceCol.toArgb()
                            this.style = Paint.Style.FILL
                        }
                        native.drawPath(baseAndroidPath, basePaint)
                    }

                    NeumorphStyle.PRESSED -> {
                        // 1. Draw base surface fill
                        val basePaint = Paint().apply {
                            isAntiAlias = true
                            color = surfaceCol.toArgb()
                            this.style = Paint.Style.FILL
                        }
                        native.drawPath(baseAndroidPath, basePaint)

                        // 2. Inner Shadows via Canvas Clipping
                        val checkpoint = native.save()
                        native.clipPath(baseAndroidPath)

                        val strokeWidth = offsetPx * 2f

                        // Top-Left Inset Shadow (Dark)
                        val innerDarkPaint = Paint().apply {
                            isAntiAlias = true
                            this.style = Paint.Style.STROKE
                            this.strokeWidth = strokeWidth
                            color = darkShadow.toArgb()
                            setShadowLayer(blurPx, offsetPx, offsetPx, darkShadow.toArgb())
                        }
                        native.drawPath(baseAndroidPath, innerDarkPaint)

                        // Bottom-Right Inset Shadow (Light)
                        val innerLightPaint = Paint().apply {
                            isAntiAlias = true
                            this.style = Paint.Style.STROKE
                            this.strokeWidth = strokeWidth
                            color = lightShadow.toArgb()
                            setShadowLayer(blurPx, -offsetPx, -offsetPx, lightShadow.toArgb())
                        }
                        native.drawPath(baseAndroidPath, innerLightPaint)

                        native.restoreToCount(checkpoint)
                    }
                }
            }
        }
    }
)

/**
 * A basic Neumorphic container providing background, shape, and shadow depth.
 */
@Composable
fun NeumorphicSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(NeumorphicTokens.RadiusMedium),
    level: NeumorphLevel = NeumorphLevel.MEDIUM,
    style: NeumorphStyle = NeumorphStyle.RAISED,
    content: @Composable () -> Unit
) {
    val colors = NeumorphicTheme.colors
    Box(
        modifier = modifier
            .neumorphic(
                shape = shape,
                level = level,
                style = style,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(shape)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            content()
        }
    }
}

/**
 * A Neumorphic Card container.
 */
@Composable
fun NeumorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(NeumorphicTokens.RadiusMedium),
    level: NeumorphLevel = NeumorphLevel.MEDIUM,
    style: NeumorphStyle = NeumorphStyle.RAISED,
    contentPadding: Dp = NeumorphicTokens.Space16,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .neumorphic(
                shape = shape,
                level = level,
                style = style,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .then(clickModifier)
            .clip(shape)
            .then(if (contentPadding > 0.dp) Modifier.padding(contentPadding) else Modifier)
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.textPrimary) {
            content()
        }
    }
}

/**
 * Fully circular Neumorphic Icon Button that animates from Raised to Pressed on touch.
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentStyle = when {
        !enabled -> NeumorphStyle.FLAT
        isPressed || isActive -> NeumorphStyle.PRESSED
        else -> NeumorphStyle.RAISED
    }

    val iconTint = when {
        !enabled -> colors.textSecondary.copy(alpha = 0.4f)
        isActive -> colors.accent
        else -> colors.textPrimary
    }

    val baseModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        modifier = baseModifier
            .size(size)
            .neumorphic(
                shape = CircleShape,
                level = level,
                style = currentStyle,
                lightShadowColorOverride = lightShadowColorOverride ?: colors.lightShadow,
                darkShadowColorOverride = darkShadowColorOverride ?: colors.darkShadow,
                backgroundColorOverride = backgroundColorOverride ?: colors.surface
            )
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
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

/**
 * Neumorphic Toggle Button that stays pressed when toggled on.
 */
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
 * Neumorphic Slider with an Inset track, filled accent progress, and a Raised thumb.
 */
@Composable
fun NeumorphicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    testTag: String? = null
) {
    val colors = NeumorphicTheme.colors
    val density = LocalDensity.current

    val normalized = if (valueRange.endInclusive > valueRange.start) {
        ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
    } else 0f

    val baseModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentValueRange by rememberUpdatedState(valueRange)

    BoxWithConstraints(
        modifier = baseModifier
            .fillMaxWidth()
            .height(48.dp) // Generous 48dp touch target for entire slider
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val totalW = size.width.toFloat()
                    val thumbR = with(density) { 11.dp.toPx() }
                    val trackW = (totalW - thumbR * 2).coerceAtLeast(1f)

                    fun calcVal(xPos: Float): Float {
                        val fraction = ((xPos - thumbR) / trackW).coerceIn(0f, 1f)
                        return currentValueRange.start + fraction * (currentValueRange.endInclusive - currentValueRange.start)
                    }

                    currentOnValueChange(calcVal(down.position.x))

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (change.changedToUpIgnoreConsumed()) {
                            change.consume()
                            break
                        }
                        change.consume()
                        currentOnValueChange(calcVal(change.position.x))
                    }
                    currentOnValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val thumbRadius = 11.dp
        val thumbRadiusPx = with(density) { thumbRadius.toPx() }
        val trackWidthPx = (totalWidthPx - thumbRadiusPx * 2).coerceAtLeast(1f)

        // Inset Track Well (clean, thin 8dp track)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .neumorphic(
                    shape = RoundedCornerShape(NeumorphicTokens.RadiusPill),
                    level = NeumorphLevel.SMALL,
                    style = NeumorphStyle.PRESSED,
                    lightShadowColorOverride = colors.lightShadow,
                    darkShadowColorOverride = colors.darkShadow,
                    backgroundColorOverride = colors.surface
                )
                .clip(RoundedCornerShape(NeumorphicTokens.RadiusPill))
        ) {
            // Accent progress fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(normalized)
                    .height(8.dp)
                    .clip(RoundedCornerShape(NeumorphicTokens.RadiusPill))
                    .background(colors.accent)
            )
        }

        // Draggable Thumb (visual only, touch handled by parent BoxWithConstraints)
        val thumbOffsetPx = normalized * trackWidthPx
        val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

        Box(
            modifier = Modifier
                .offset(x = thumbOffsetDp)
                .size(thumbRadius * 2)
                .neumorphic(
                    shape = CircleShape,
                    level = NeumorphLevel.SMALL,
                    style = NeumorphStyle.RAISED,
                    lightShadowColorOverride = colors.lightShadow,
                    darkShadowColorOverride = colors.darkShadow,
                    backgroundColorOverride = colors.surface
                )
                .clip(CircleShape)
        )
    }
}

/**
 * Neumorphic Switch with an Inset well track and a smoothly sliding Raised thumb.
 */
@Composable
fun NeumorphicSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphicTheme.colors
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 2.dp,
        animationSpec = tween(durationMillis = NeumorphicTokens.AnimDurationMs),
        label = "thumb_offset"
    )

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .neumorphic(
                shape = RoundedCornerShape(NeumorphicTokens.RadiusPill),
                level = NeumorphLevel.SMALL,
                style = NeumorphStyle.PRESSED,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(RoundedCornerShape(NeumorphicTokens.RadiusPill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Track tint when active
        if (checked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.accent.copy(alpha = 0.25f))
            )
        }

        // Raised thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .padding(2.dp)
                .neumorphic(
                    shape = CircleShape,
                    level = NeumorphLevel.SMALL,
                    style = NeumorphStyle.RAISED,
                    lightShadowColorOverride = colors.lightShadow,
                    darkShadowColorOverride = colors.darkShadow,
                    backgroundColorOverride = if (checked) colors.accent else colors.surface
                )
                .clip(CircleShape)
        )
    }
}

/**
 * Neumorphic Chip for filters and tabs: Raised when inactive, Pressed with accent indicator when selected.
 */
@Composable
fun NeumorphicChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val style = if (selected) NeumorphStyle.PRESSED else NeumorphStyle.RAISED
    val contentColor = if (selected) colors.accent else colors.textPrimary

    Box(
        modifier = modifier
            .height(32.dp)
            .neumorphic(
                shape = RoundedCornerShape(NeumorphicTokens.RadiusPill),
                level = NeumorphLevel.SMALL,
                style = style,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(RoundedCornerShape(NeumorphicTokens.RadiusPill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalTextStyle provides TextStyle(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp)
        ) {
            label()
        }
    }
}

/**
 * Neumorphic Inset Text Field for search and forms.
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

    Box(
        modifier = modifier
            .height(40.dp)
            .neumorphic(
                shape = RoundedCornerShape(NeumorphicTokens.RadiusMedium),
                level = NeumorphLevel.SMALL,
                style = NeumorphStyle.PRESSED,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(RoundedCornerShape(NeumorphicTokens.RadiusMedium))
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
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(colors.accent),
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
 * Modern Icon-Only Floating Neumorphic Bottom Navigation Bar.
 * - Floating capsule pill with fully rounded ends (half-height radius, 29dp for 58dp height).
 * - Raised neumorphic container using lightest shadow level (SMALL).
 * - Compact centered width (~280dp, ~78% screen width) giving ~93dp touch area per item.
 * - Smooth ~200ms animated sliding circular INSET (Pressed) container (~44dp) for the active tab.
 * - Extra accent-colored dot (~4dp) below the icon inside the inset container.
 * - Instant snap transition when system "remove animations" setting is active.
 * - Accessible TalkBack semantics (Role.Tab, selected state, contentDescription) and >=48dp touch targets.
 * - Light haptic feedback (TextHandleMove) on tab selection change.
 */
@Composable
fun NeumorphicFloatingBottomBar(
    selectedTab: MainNavigationTab,
    onTabSelected: (MainNavigationTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphicTheme.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isAnimationDisabled = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    val floatAnimSpec: AnimationSpec<Float> = if (isAnimationDisabled) {
        snap()
    } else {
        tween(durationMillis = 200, easing = FastOutSlowInEasing)
    }

    val colorAnimSpec: AnimationSpec<Color> = if (isAnimationDisabled) {
        snap()
    } else {
        tween(durationMillis = 200)
    }

    val tabs = remember {
        listOf(
            Triple(MainNavigationTab.MUSIC, R.string.nav_music, "nav_music_item"),
            Triple(MainNavigationTab.VIDEO, R.string.nav_video, "nav_video_item"),
            Triple(MainNavigationTab.SETTINGS, R.string.nav_settings, "nav_settings_item")
        )
    }

    val selectedIndex = when (selectedTab) {
        MainNavigationTab.MUSIC -> 0
        MainNavigationTab.VIDEO -> 1
        MainNavigationTab.SETTINGS -> 2
    }

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = floatAnimSpec,
        label = "nav_indicator_offset"
    )

    val barHeight = 58.dp
    val barShape = RoundedCornerShape(29.dp) // Half height = fully rounded pill ends

    BoxWithConstraints(
        modifier = modifier
            .widthIn(min = 260.dp, max = 320.dp)
            .fillMaxWidth(0.78f)
            .height(barHeight)
            .neumorphic(
                shape = barShape,
                level = NeumorphLevel.SMALL,
                style = NeumorphStyle.RAISED,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(barShape)
    ) {
        val slotWidth = maxWidth / 3

        // Sliding Inset (Pressed) container (~44dp) with accent dot (~4dp)
        val indicatorSize = 44.dp
        val indicatorOffset = (slotWidth * (animatedIndex + 0.5f)) - (indicatorSize / 2)

        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .align(Alignment.CenterStart)
                .size(indicatorSize)
                .neumorphic(
                    shape = CircleShape,
                    level = NeumorphLevel.SMALL,
                    style = NeumorphStyle.PRESSED,
                    lightShadowColorOverride = colors.lightShadow,
                    darkShadowColorOverride = colors.darkShadow,
                    backgroundColorOverride = colors.surface
                )
                .clip(CircleShape)
        ) {
            // Small accent-colored dot (~4dp) inside the selected container as an extra indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
            )
        }

        // Tab items row (full width, 3 equal clickable slots >= 48dp)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, (tab, stringResId, testTag) ->
                val isSelected = selectedTab == tab
                val title = stringResource(stringResId)

                val iconVector = when (tab) {
                    MainNavigationTab.MUSIC -> if (isSelected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote
                    MainNavigationTab.VIDEO -> if (isSelected) Icons.Filled.Videocam else Icons.Outlined.Videocam
                    MainNavigationTab.SETTINGS -> if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings
                }

                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) colors.accent else colors.textSecondary,
                    animationSpec = colorAnimSpec,
                    label = "icon_color_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (selectedTab != tab) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTabSelected(tab)
                                }
                            }
                        )
                        .semantics {
                            this.role = Role.Tab
                            this.selected = isSelected
                            this.contentDescription = title
                        }
                        .testTag(testTag),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null, // Handled by container semantics
                        tint = iconColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Traditional Neumorphic Bottom Navigation Bar container and items (preserved for backwards-compatibility).
 */
@Composable
fun NeumorphicBottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val colors = NeumorphicTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .neumorphic(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                level = NeumorphLevel.LARGE,
                style = NeumorphStyle.RAISED,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .padding(vertical = 6.dp, horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphicTheme.colors
    val style = if (selected) NeumorphStyle.PRESSED else NeumorphStyle.FLAT
    val itemColor = if (selected) colors.accent else colors.textSecondary

    Column(
        modifier = modifier
            .weight(1f)
            .height(48.dp)
            .neumorphic(
                shape = RoundedCornerShape(14.dp),
                level = NeumorphLevel.SMALL,
                style = style,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides itemColor) {
            icon()
        }
        Spacer(modifier = Modifier.height(2.dp))
        CompositionLocalProvider(
            LocalContentColor provides itemColor,
            LocalTextStyle provides TextStyle(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        ) {
            label()
        }
    }
}

@Composable
fun RowScope.NeumorphicBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    NeumorphicBottomBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label) },
        modifier = modifier
    )
}

// =========================================================================
// Previews for Neumorphic Components in both Light and Dark themes
// =========================================================================

@Preview(name = "Neumorphic Components - Light", showBackground = true)
@Composable
fun PreviewNeumorphicComponentsLight() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NeumorphicTheme.colors.background)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Neumorphic Soft UI (Light)",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NeumorphicTheme.colors.textPrimary)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeumorphicIconButton(onClick = {}, icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) })
                    NeumorphicIconButton(onClick = {}, icon = { Icon(Icons.Default.Check, contentDescription = null) }, isActive = true)
                    NeumorphicSwitch(checked = true, onCheckedChange = {})
                    NeumorphicSwitch(checked = false, onCheckedChange = {})
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeumorphicChip(selected = true, onClick = {}, label = { Text("Lossless") })
                    NeumorphicChip(selected = false, onClick = {}, label = { Text("Lossy") })
                }

                NeumorphicSlider(value = 0.6f, onValueChange = {})

                NeumorphicTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Cari lagu...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }
        }
    }
}

@Preview(name = "Neumorphic Components - Dark", showBackground = true)
@Composable
fun PreviewNeumorphicComponentsDark() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NeumorphicTheme.colors.background)
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Neumorphic Soft UI (Dark)",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NeumorphicTheme.colors.textPrimary)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NeumorphicIconButton(onClick = {}, icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) })
                    NeumorphicIconButton(onClick = {}, icon = { Icon(Icons.Default.Check, contentDescription = null) }, isActive = true)
                    NeumorphicSwitch(checked = true, onCheckedChange = {})
                    NeumorphicSwitch(checked = false, onCheckedChange = {})
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeumorphicChip(selected = true, onClick = {}, label = { Text("Lossless") })
                    NeumorphicChip(selected = false, onClick = {}, label = { Text("Lossy") })
                }

                NeumorphicSlider(value = 0.6f, onValueChange = {})

                NeumorphicTextField(
                    value = "",
                    onValueChange = {},
                    placeholder = { Text("Cari lagu...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                )
            }
        }
    }
}

// =========================================================================
// Floating Bottom Bar Previews (Light and Dark for all 3 tabs)
// =========================================================================

@Preview(name = "Floating Bottom Bar - Light (Music Selected)", showBackground = true)
@Composable
fun PreviewFloatingBottomBarLightMusic() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .background(NeumorphicTokens.LightBackground)
                .padding(24.dp)
        ) {
            NeumorphicFloatingBottomBar(
                selectedTab = MainNavigationTab.MUSIC,
                onTabSelected = {}
            )
        }
    }
}

@Preview(name = "Floating Bottom Bar - Light (Video Selected)", showBackground = true)
@Composable
fun PreviewFloatingBottomBarLightVideo() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .background(NeumorphicTokens.LightBackground)
                .padding(24.dp)
        ) {
            NeumorphicFloatingBottomBar(
                selectedTab = MainNavigationTab.VIDEO,
                onTabSelected = {}
            )
        }
    }
}

@Preview(name = "Floating Bottom Bar - Light (Settings Selected)", showBackground = true)
@Composable
fun PreviewFloatingBottomBarLightSettings() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .background(NeumorphicTokens.LightBackground)
                .padding(24.dp)
        ) {
            NeumorphicFloatingBottomBar(
                selectedTab = MainNavigationTab.SETTINGS,
                onTabSelected = {}
            )
        }
    }
}

@Preview(name = "Floating Bottom Bar - Dark (Music Selected)", showBackground = true)
@Composable
fun PreviewFloatingBottomBarDarkMusic() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(NeumorphicTokens.DarkBackground)
                .padding(24.dp)
        ) {
            NeumorphicFloatingBottomBar(
                selectedTab = MainNavigationTab.MUSIC,
                onTabSelected = {}
            )
        }
    }
}

@Preview(name = "Floating Bottom Bar - Dark (Video Selected)", showBackground = true)
@Composable
fun PreviewFloatingBottomBarDarkVideo() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(NeumorphicTokens.DarkBackground)
                .padding(24.dp)
        ) {
            NeumorphicFloatingBottomBar(
                selectedTab = MainNavigationTab.VIDEO,
                onTabSelected = {}
            )
        }
    }
}

@Preview(name = "Floating Bottom Bar - Dark (Settings Selected)", showBackground = true)
@Composable
fun PreviewFloatingBottomBarDarkSettings() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(NeumorphicTokens.DarkBackground)
                .padding(24.dp)
        ) {
            NeumorphicFloatingBottomBar(
                selectedTab = MainNavigationTab.SETTINGS,
                onTabSelected = {}
            )
        }
    }
}

