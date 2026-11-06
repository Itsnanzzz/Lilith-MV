package com.example.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SpatialAudio
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.playback.EqualizerBandInfo
import com.example.playback.EqualizerController
import com.example.playback.EqualizerState
import com.example.playback.PRESET_CUSTOM
import com.example.ui.theme.AppChip
import com.example.ui.theme.AppIconButton
import com.example.ui.theme.AppSlider
import com.example.ui.theme.AppSwitch
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTypography
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEqualizerSheet(
    state: EqualizerState,
    onToggleEnabled: (Boolean) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onSelectPreset: (String) -> Unit,
    onResetBands: () -> Unit,
    onToggleBassBoost: (Boolean) -> Unit,
    onBassBoostChange: (Int) -> Unit,
    onToggleVirtualizer: (Boolean) -> Unit,
    onVirtualizerChange: (Int) -> Unit,
    onPreampChange: (Float) -> Unit,
    onResetPreamp: () -> Unit,
    onSaveCustomPreset: (String) -> Unit,
    onDeleteCustomPreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSavePresetDialog by remember { mutableStateOf(false) }
    var presetNameToSave by remember { mutableStateOf("") }
    var presetToDelete by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.hairline)
            )
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Icon, Title, Master Toggle, Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.equalizer_title),
                            style = AppTypography.screenTitle.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                        Text(
                            text = if (state.isEnabled) {
                                state.activePreset
                            } else {
                                stringResource(R.string.equalizer_bypassed)
                            },
                            style = AppTypography.caption,
                            color = if (state.isEnabled) colors.accentText else colors.textSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppSwitch(
                        checked = state.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        modifier = Modifier.testTag("equalizer_master_switch")
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    AppIconButton(
                        onClick = onDismiss,
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        isTonalCircle = true,
                        testTag = "equalizer_close_button"
                    )
                }
            }

            // Presets Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.equalizer_preset),
                    style = AppTypography.label.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textSecondary
                )
                if (state.activePreset == PRESET_CUSTOM) {
                    TextButton(
                        onClick = {
                            presetNameToSave = ""
                            showSavePresetDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.equalizer_save_preset),
                            style = AppTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Presets Horizontal Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(state.presets) { presetName ->
                    val isSelected = state.activePreset == presetName
                    val isCustomNamed = state.customPresets.containsKey(presetName)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppChip(
                            text = presetName,
                            selected = isSelected,
                            onClick = {
                                if (state.isEnabled) {
                                    onSelectPreset(presetName)
                                }
                            }
                        )
                        if (isCustomNamed && isSelected) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceMuted)
                                    .clickable { presetToDelete = presetName },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.equalizer_delete_preset),
                                    tint = colors.destructive,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Graphic Equalizer Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceMuted)
                    .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${stringResource(R.string.equalizer_graphic_title)} (${state.bands.size}-Band)",
                                style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary
                            )
                        }

                        TextButton(
                            onClick = onResetBands,
                            enabled = state.isEnabled,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = if (state.isEnabled) colors.accent else colors.disabled,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.reset),
                                style = AppTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = if (state.isEnabled) colors.accentText else colors.disabled
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Graphic Equalizer Vertical Sliders with Zero-Line Canvas
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val availableWidth = maxWidth
                        val bandCount = state.bands.size
                        val needsScroll = bandCount > 6 && availableWidth < (bandCount * 46).dp

                        val rowModifier = if (needsScroll) {
                            Modifier.horizontalScroll(rememberScrollState())
                        } else {
                            Modifier.fillMaxWidth()
                        }

                        Box(modifier = rowModifier) {
                            // Zero reference line across all sliders
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(210.dp)
                            ) {
                                val yZero = 24.dp.toPx() + 65.dp.toPx() // Top label offset + half of 130dp track
                                drawLine(
                                    color = colors.hairline.copy(alpha = 0.7f),
                                    start = Offset(0f, yZero),
                                    end = Offset(size.width, yZero),
                                    strokeWidth = 1.5f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                )
                            }

                            Row(
                                modifier = if (needsScroll) Modifier else Modifier.fillMaxWidth(),
                                horizontalArrangement = if (needsScroll) Arrangement.spacedBy(8.dp) else Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                state.bands.forEach { band ->
                                    VerticalBandSlider(
                                        band = band,
                                        enabled = state.isEnabled,
                                        accentColor = colors.accent,
                                        textColor = colors.textPrimary,
                                        secondaryColor = colors.textSecondary,
                                        hairlineColor = colors.hairline,
                                        onGainChanged = { newGain ->
                                            onBandGainChange(band.index, newGain)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Additional Audio Effects Section
            Text(
                text = stringResource(R.string.equalizer_effects_title),
                style = AppTypography.label.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Bass Boost Card
            EffectCard(
                title = stringResource(R.string.equalizer_bass_boost),
                icon = Icons.Default.VolumeUp,
                enabled = state.isEnabled && state.isBassBoostSupported,
                isChecked = state.isBassBoostEnabled,
                onCheckedChange = onToggleBassBoost,
                valueLabel = "${(state.bassBoostStrength / 10f).roundToInt()}%",
                sliderValue = state.bassBoostStrength / 1000f,
                onSliderChange = { frac ->
                    onBassBoostChange((frac * 1000f).roundToInt())
                },
                unsupportedMessage = if (!state.isBassBoostSupported) stringResource(R.string.equalizer_bass_not_supported) else null
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Virtualizer Card
            EffectCard(
                title = stringResource(R.string.equalizer_virtualizer),
                icon = Icons.Default.SurroundSound,
                enabled = state.isEnabled && state.isVirtualizerSupported,
                isChecked = state.isVirtualizerEnabled,
                onCheckedChange = onToggleVirtualizer,
                valueLabel = "${(state.virtualizerStrength / 10f).roundToInt()}%",
                sliderValue = state.virtualizerStrength / 1000f,
                onSliderChange = { frac ->
                    onVirtualizerChange((frac * 1000f).roundToInt())
                },
                unsupportedMessage = if (!state.isVirtualizerSupported) stringResource(R.string.equalizer_virtualizer_not_supported) else null
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Preamp Card
            PreampCard(
                gainDb = state.preampGainDb,
                enabled = state.isEnabled && state.isPreampSupported,
                onGainChange = onPreampChange,
                onReset = onResetPreamp,
                unsupportedMessage = if (!state.isPreampSupported) stringResource(R.string.equalizer_preamp_not_supported) else null
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Save Custom Preset Dialog
    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.equalizer_save_preset),
                    style = AppTypography.screenTitle.copy(fontSize = 18.sp),
                    color = colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = presetNameToSave,
                    onValueChange = { presetNameToSave = it },
                    placeholder = { Text(stringResource(R.string.equalizer_custom_preset_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.hairline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = presetNameToSave.trim()
                        if (name.isNotEmpty()) {
                            onSaveCustomPreset(name)
                            showSavePresetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    )
                ) {
                    Text(stringResource(R.string.playlist_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false }) {
                    Text(stringResource(R.string.playlist_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }

    // Delete Custom Preset Confirmation
    presetToDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = {
                Text(
                    text = stringResource(R.string.equalizer_delete_preset),
                    style = AppTypography.screenTitle.copy(fontSize = 18.sp),
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete preset \"$name\"?",
                    style = AppTypography.body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCustomPreset(name)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.destructive,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text(stringResource(R.string.playlist_cancel), color = colors.textSecondary)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
private fun VerticalBandSlider(
    band: EqualizerBandInfo,
    enabled: Boolean,
    accentColor: Color,
    textColor: Color,
    secondaryColor: Color,
    hairlineColor: Color,
    onGainChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val gain = band.gainDb
    val isNonZero = kotlin.math.abs(gain) >= 0.1f
    val gainLabel = when {
        gain > 0.05f -> String.format(Locale.US, "+%.1f", gain)
        gain < -0.05f -> String.format(Locale.US, "%.1f", gain)
        else -> "0.0"
    }

    val minDb = band.minGainDb
    val maxDb = band.maxGainDb
    val rangeDb = (maxDb - minDb).coerceAtLeast(1f)

    Column(
        modifier = modifier
            .width(44.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Gain value above
        Text(
            text = gainLabel,
            style = AppTypography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = if (isNonZero && enabled) accentColor else secondaryColor,
            maxLines = 1,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical Track Box
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(130.dp)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            onGainChanged(0f)
                        },
                        onTap = { offset ->
                            val heightPx = size.height.toFloat()
                            val frac = (1f - (offset.y / heightPx)).coerceIn(0f, 1f)
                            val newGain = minDb + frac * rangeDb
                            val finalGain = if (kotlin.math.abs(newGain) < 0.3f) 0f else newGain
                            onGainChanged(finalGain)
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        val heightPx = size.height.toFloat()
                        val frac = (1f - (change.position.y / heightPx)).coerceIn(0f, 1f)
                        val newGain = minDb + frac * rangeDb
                        val finalGain = if (kotlin.math.abs(newGain) < 0.3f) 0f else newGain
                        onGainChanged(finalGain)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackWidth = 5.dp.toPx()
                val centerX = size.width / 2f
                val topY = 10.dp.toPx()
                val bottomY = size.height - 10.dp.toPx()
                val trackHeight = bottomY - topY
                val centerY = topY + trackHeight * (maxDb / rangeDb)

                // Background track
                drawRoundRect(
                    color = hairlineColor,
                    topLeft = Offset(centerX - trackWidth / 2f, topY),
                    size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f, trackWidth / 2f)
                )

                // Thumb Y position
                val currentFrac = ((gain - minDb) / rangeDb).coerceIn(0f, 1f)
                val thumbY = bottomY - currentFrac * trackHeight

                // Center zero reference tick
                drawLine(
                    color = hairlineColor.copy(alpha = 0.9f),
                    start = Offset(centerX - 8.dp.toPx(), centerY),
                    end = Offset(centerX + 8.dp.toPx(), centerY),
                    strokeWidth = 2.dp.toPx()
                )

                // Active fill from center zero line to thumb
                if (enabled && kotlin.math.abs(gain) > 0.05f) {
                    val fillTop = kotlin.math.min(centerY, thumbY)
                    val fillHeight = kotlin.math.abs(thumbY - centerY)
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(centerX - trackWidth / 2f, fillTop),
                        size = androidx.compose.ui.geometry.Size(trackWidth, fillHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackWidth / 2f, trackWidth / 2f)
                    )
                }

                // Thumb circle
                val thumbRadius = 8.5.dp.toPx()
                val thumbColor = if (enabled) accentColor else hairlineColor
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(centerX, thumbY)
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f),
                    radius = thumbRadius,
                    center = Offset(centerX, thumbY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Frequency Label below
        Text(
            text = band.formattedFreq,
            style = AppTypography.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = if (enabled) textColor else secondaryColor,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EffectCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    valueLabel: String,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    unsupportedMessage: String? = null
) {
    val colors = AppThemeTokens.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceMuted)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (enabled && isChecked) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                }

                if (unsupportedMessage == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AnimatedVisibility(visible = isChecked) {
                            Text(
                                text = valueLabel,
                                style = AppTypography.caption.copy(fontWeight = FontWeight.Bold),
                                color = colors.accentText,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                        }
                        AppSwitch(
                            checked = isChecked,
                            onCheckedChange = onCheckedChange,
                            enabled = enabled
                        )
                    }
                }
            }

            if (unsupportedMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = unsupportedMessage,
                    style = AppTypography.caption,
                    color = colors.textSecondary
                )
            } else if (isChecked && enabled) {
                Spacer(modifier = Modifier.height(8.dp))
                AppSlider(
                    value = sliderValue,
                    onValueChange = onSliderChange,
                    enabled = enabled && isChecked
                )
            }
        }
    }
}

@Composable
private fun PreampCard(
    gainDb: Float,
    enabled: Boolean,
    onGainChange: (Float) -> Unit,
    onReset: () -> Unit,
    unsupportedMessage: String? = null
) {
    val colors = AppThemeTokens.colors
    val gainLabel = when {
        gainDb > 0.05f -> String.format(Locale.US, "+%.1f dB", gainDb)
        gainDb < -0.05f -> String.format(Locale.US, "%.1f dB", gainDb)
        else -> "0.0 dB"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceMuted)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SpatialAudio,
                        contentDescription = null,
                        tint = if (enabled && kotlin.math.abs(gainDb) > 0.05f) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.equalizer_preamp),
                        style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                }

                if (unsupportedMessage == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = gainLabel,
                            style = AppTypography.caption.copy(fontWeight = FontWeight.Bold),
                            color = if (kotlin.math.abs(gainDb) > 0.05f) colors.accentText else colors.textSecondary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        TextButton(
                            onClick = onReset,
                            enabled = enabled && kotlin.math.abs(gainDb) > 0.05f,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.reset),
                                style = AppTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = if (enabled && kotlin.math.abs(gainDb) > 0.05f) colors.accentText else colors.disabled
                            )
                        }
                    }
                }
            }

            if (unsupportedMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = unsupportedMessage,
                    style = AppTypography.caption,
                    color = colors.textSecondary
                )
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                // Slider mapping 0f..1f to -12f..+12f
                val sliderFrac = ((gainDb - (-12f)) / 24f).coerceIn(0f, 1f)
                AppSlider(
                    value = sliderFrac,
                    onValueChange = { frac ->
                        val newGain = -12f + frac * 24f
                        val snapped = if (kotlin.math.abs(newGain) < 0.25f) 0f else newGain
                        onGainChange(snapped)
                    },
                    enabled = enabled
                )
            }
        }
    }
}
