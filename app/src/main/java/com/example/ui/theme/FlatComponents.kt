package com.example.ui.theme

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Checks if system animations are disabled via developer options.
 */
@Composable
fun rememberIsAnimationDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
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
}

/**
 * Flat Icon Button: No container by default, 48dp touch target, ~24dp icon.
 * Optional 40dp tonal circle variant using surfaceMuted.
 */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    isTonalCircle: Boolean = false,
    isActive: Boolean = false,
    tintOverride: Color? = null,
    iconSize: Dp = 22.dp,
    testTag: String? = null
) {
    val colors = AppThemeTokens.colors
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!isAnimationDisabled && isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = AppTokens.AnimDurationShort),
        label = "app_icon_button_scale"
    )

    val iconColor = tintOverride ?: when {
        isActive -> colors.accentText
        else -> colors.textSecondary
    }

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (isTonalCircle) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconColor,
                    modifier = Modifier.size(iconSize)
                )
                if (isActive) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(colors.accent)
                    )
                }
            }
        }
    }
}

/**
 * Primary Button: Pill shape, accent fill, onAccent text & icon.
 */
@Composable
fun AppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val colors = AppThemeTokens.colors
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!isAnimationDisabled && isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = AppTokens.AnimDurationShort),
        label = "primary_btn_scale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(AppTokens.RadiusPill),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .scale(scale)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = colors.onAccent,
                style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

/**
 * Secondary Button: Pill shape, surfaceMuted fill, textPrimary text & icon.
 */
@Composable
fun AppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    val colors = AppThemeTokens.colors
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!isAnimationDisabled && isPressed) 0.96f else 1.0f,
        animationSpec = tween(durationMillis = AppTokens.AnimDurationShort),
        label = "secondary_btn_scale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(AppTokens.RadiusPill),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceMuted,
            contentColor = colors.textPrimary
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier
            .scale(scale)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = colors.textPrimary,
                style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

/**
 * Filter Chip: 32dp tall pill.
 * Unselected = surfaceMuted fill + textSecondary.
 * Selected = accent fill + onAccent + SemiBold.
 */
@Composable
fun AppChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val colors = AppThemeTokens.colors
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!isAnimationDisabled && isPressed) 0.95f else 1.0f,
        animationSpec = tween(durationMillis = AppTokens.AnimDurationShort),
        label = "chip_scale"
    )

    val containerColor = if (selected) colors.accent else colors.surfaceMuted
    val textColor = if (selected) colors.onAccent else colors.textSecondary

    Box(
        modifier = modifier
            .height(32.dp)
            .scale(scale)
            .clip(RoundedCornerShape(AppTokens.RadiusPill))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/**
 * Search Field: Pill shape, 40dp tall, surfaceMuted fill, no border, 20dp search icon, 14sp text.
 */
@Composable
fun AppSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = { onQueryChange("") },
    onSearch: () -> Unit = {},
    testTag: String? = null
) {
    val colors = AppThemeTokens.colors

    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(AppTokens.RadiusPill))
            .background(colors.surfaceMuted)
            .padding(horizontal = 14.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.textSecondary,
                        style = AppTypography.body
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = AppTypography.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.delete),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Clean Flat Slider: 4dp track in hairline color, progress in accent, 12dp accent thumb,
 * taller invisible 48dp touch area, local drag state with seekTo on release only.
 */
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val colors = AppThemeTokens.colors
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(value) }

    val activeFraction = if (isDragging) dragFraction else value.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .height(48.dp) // Minimum 48dp touch area
            .pointerInput(enabled, onValueChangeFinished) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        if (onValueChangeFinished != null) {
                            onValueChange(dragFraction)
                        }
                    },
                    onDragEnd = {
                        onValueChange(dragFraction)
                        onValueChangeFinished?.invoke()
                        isDragging = false
                    },
                    onDragCancel = {
                        onValueChangeFinished?.invoke()
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        if (onValueChangeFinished != null) {
                            onValueChange(dragFraction)
                        }
                    }
                )
            }
            .pointerInput(enabled, onValueChangeFinished) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val target = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(target)
                        onValueChangeFinished?.invoke()
                    }
                )
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val totalWidth = maxWidth
        val thumbOffset = totalWidth * activeFraction

        // 4dp Hairline Track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.hairline)
        )

        // 4dp Progress Track
        Box(
            modifier = Modifier
                .fillMaxWidth(activeFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent)
        )

        // 12dp Thumb
        Box(
            modifier = Modifier
                .offset(x = (thumbOffset - 6.dp).coerceIn(0.dp, totalWidth - 12.dp))
                .size(12.dp)
                .clip(CircleShape)
                .background(colors.accent)
        )
    }
}

/**
 * Themed Switch: On = accent track + onAccent thumb, Off = hairline track.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = AppThemeTokens.colors

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onAccent,
            checkedTrackColor = colors.accent,
            uncheckedThumbColor = colors.textSecondary,
            uncheckedTrackColor = colors.hairline,
            uncheckedBorderColor = Color.Transparent
        ),
        modifier = modifier
    )
}

/**
 * Segmented Control (e.g. Light / Dark / System theme picker).
 */
@Composable
fun <T> AppSegmentedControl(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier,
    itemTestTagProvider: ((T) -> String)? = null
) {
    val colors = AppThemeTokens.colors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(AppTokens.RadiusPill))
            .background(colors.surfaceMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            val containerColor = if (isSelected) colors.accent else Color.Transparent
            val textColor = if (isSelected) colors.onAccent else colors.textSecondary

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(AppTokens.RadiusPill))
                    .background(containerColor)
                    .clickable(role = Role.Tab) { onItemSelected(item) }
                    .semantics { this.selected = isSelected }
                    .then(if (itemTestTagProvider != null) Modifier.testTag(itemTestTagProvider(item)) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = labelProvider(item),
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Technical Audio Badge (LOSSY / LOSSLESS):
 * Tiny pill (~16dp tall, 10sp caps, 0.4sp letter spacing).
 * LOSSY = transparent with 1dp hairline border and textSecondary text.
 * LOSSLESS = accent fill with onAccent text.
 */
@Composable
fun AppBadge(
    isLossless: Boolean,
    modifier: Modifier = Modifier,
    text: String = if (isLossless) "LOSSLESS" else "LOSSY"
) {
    val colors = AppThemeTokens.colors

    Box(
        modifier = modifier
            .height(16.dp)
            .clip(RoundedCornerShape(AppTokens.RadiusPill))
            .then(
                if (isLossless) {
                    Modifier.background(colors.accent)
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = colors.hairline,
                        shape = RoundedCornerShape(AppTokens.RadiusPill)
                    )
                }
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (isLossless) colors.onAccent else colors.textSecondary,
            style = AppTypography.badge
        )
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(name = "Flat Components - Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewFlatComponentsLight() {
    LilithTheme(darkTheme = false) {
        FlatComponentsDemo()
    }
}

@Preview(name = "Flat Components - Dark", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun PreviewFlatComponentsDark() {
    LilithTheme(darkTheme = true) {
        FlatComponentsDemo()
    }
}

@Composable
private fun FlatComponentsDemo() {
    val colors = AppThemeTokens.colors
    var query by remember { mutableStateOf("") }
    var sliderVal by remember { mutableFloatStateOf(0.45f) }
    var switchVal by remember { mutableStateOf(true) }
    var chipSelected by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Flat Minimal Components", style = AppTypography.screenTitle, color = colors.textPrimary)

        AppSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Search songs, artists, videos…"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip("Songs", selected = chipSelected == 0, onClick = { chipSelected = 0 })
            AppChip("Albums", selected = chipSelected == 1, onClick = { chipSelected = 1 })
            AppChip("Artists", selected = chipSelected == 2, onClick = { chipSelected = 2 })
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppPrimaryButton("Play", onClick = {}, icon = Icons.Default.PlayArrow)
            AppSecondaryButton("Shuffle", onClick = {})
            AppIconButton(onClick = {}, icon = Icons.Default.PlayArrow, contentDescription = null, isTonalCircle = true)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppBadge(isLossless = true)
            AppBadge(isLossless = false)
            Spacer(modifier = Modifier.weight(1f))
            AppSwitch(checked = switchVal, onCheckedChange = { switchVal = it })
        }

        AppSlider(value = sliderVal, onValueChange = { sliderVal = it })
    }
}
