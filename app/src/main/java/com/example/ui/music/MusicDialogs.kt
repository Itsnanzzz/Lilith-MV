package com.example.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.model.AudioTrack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.playback.EqualizerState
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicChip
import com.example.ui.theme.NeumorphicSlider
import com.example.ui.theme.NeumorphicSwitch
import com.example.ui.theme.NeumorphicTextField
import com.example.ui.theme.NeumorphicTheme
import com.example.ui.theme.NeumorphicTokens
import com.example.ui.theme.neumorphic

@Composable
fun EqualizerDialog(
    state: EqualizerState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NeumorphicTheme.colors
    var localBass by remember(state.bassBoostStrength) { mutableFloatStateOf(state.bassBoostStrength.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = colors.accent
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.equalizer_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!state.isAvailable) {
                    Text(
                        text = stringResource(R.string.equalizer_not_supported),
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.equalizer_enable),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                        NeumorphicSwitch(
                            checked = state.isEnabled,
                            onCheckedChange = onToggleEnabled,
                            modifier = Modifier.testTag("equalizer_toggle_switch")
                        )
                    }

                    if (state.isEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.equalizer_preset),
                            fontSize = 13.sp,
                            color = colors.accent,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(state.presets) { index, presetName ->
                                NeumorphicChip(
                                    selected = state.currentPreset == index,
                                    onClick = { onSelectPreset(index) },
                                    label = { Text(presetName, fontSize = 12.sp) },
                                    modifier = Modifier.testTag("preset_chip_$index")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.equalizer_bass_boost),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${(localBass / 10).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent
                            )
                        }

                        NeumorphicSlider(
                            value = localBass,
                            onValueChange = {
                                localBass = it
                                onSetBassBoost(it.toInt())
                            },
                            valueRange = 0f..1000f,
                            modifier = Modifier.testTag("bass_boost_slider")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = colors.accent
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.playback_speed),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                speeds.chunked(3).forEach { rowSpeeds ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowSpeeds.forEach { speed ->
                            val isSelected = currentSpeed == speed
                            val style = if (isSelected) NeumorphStyle.PRESSED else NeumorphStyle.RAISED
                            val chipShape = RoundedCornerShape(NeumorphicTokens.RadiusSmall)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .neumorphic(
                                        shape = chipShape,
                                        level = NeumorphLevel.SMALL,
                                        style = style,
                                        lightShadowColorOverride = colors.lightShadow,
                                        darkShadowColorOverride = colors.darkShadow,
                                        backgroundColorOverride = colors.surface
                                    )
                                    .clip(chipShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {
                                            onSelectSpeed(speed)
                                            onDismiss()
                                        }
                                    )
                                    .padding(vertical = 12.dp)
                                    .testTag("speed_chip_$speed"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${speed}x",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) colors.accent else colors.textPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    remainingSeconds: Int,
    onSetTimerMinutes: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val options = listOf(0, 15, 30, 45, 60, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AvTimer,
                    contentDescription = null,
                    tint = colors.accent
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sleep_timer),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (remainingSeconds > 0) {
                    val minutes = remainingSeconds / 60
                    val seconds = remainingSeconds % 60
                    Text(
                        text = "Aktif: %02d:%02d tersisa".format(minutes, seconds),
                        fontSize = 13.sp,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                options.forEach { mins ->
                    val rowShape = RoundedCornerShape(NeumorphicTokens.RadiusSmall)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(
                                shape = rowShape,
                                level = NeumorphLevel.SMALL,
                                style = NeumorphStyle.RAISED,
                                lightShadowColorOverride = colors.lightShadow,
                                darkShadowColorOverride = colors.darkShadow,
                                backgroundColorOverride = colors.surface
                            )
                            .clip(rowShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    onSetTimerMinutes(mins)
                                    onDismiss()
                                }
                            )
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (mins == 0) stringResource(R.string.sleep_timer_off)
                            else stringResource(R.string.sleep_timer_minutes, mins),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = colors.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NeumorphicTheme.colors
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.playlist_create),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        },
        text = {
            NeumorphicTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.playlist_name_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_name_input")
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("playlist_save_button")
            ) {
                Text(
                    text = stringResource(R.string.playlist_save),
                    fontWeight = FontWeight.Bold,
                    color = if (name.isNotBlank()) colors.accent else colors.textSecondary.copy(alpha = 0.5f)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.playlist_cancel), color = colors.textSecondary)
            }
        }
    )
}

/**
 * Confirmation dialog for permanent deletion of audio tracks.
 * - Neumorphic styling with soft surface background and rounded container.
 * - Emphasizes that this deletes the file from device storage and is distinct from playlist removal.
 * - Action buttons: "Batal" (neutral/default) and "Hapus" (bold destructive red, shows loading indicator when active).
 */
@Composable
fun DeletePermanentConfirmationDialog(
    track: AudioTrack,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = NeumorphicTheme.colors
    val destructiveColor = Color(0xFFE57373)

    AlertDialog(
        onDismissRequest = {
            if (!isDeleting) onDismiss()
        },
        containerColor = colors.surface,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .neumorphic(
                        shape = CircleShape,
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.RAISED,
                        lightShadowColorOverride = colors.lightShadow,
                        darkShadowColorOverride = colors.darkShadow,
                        backgroundColorOverride = colors.surface
                    )
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = destructiveColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.delete_permanent_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = track.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.delete_permanent_message, track.title),
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isDeleting,
                modifier = Modifier.testTag("delete_permanent_confirm_button")
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = destructiveColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = stringResource(R.string.delete_permanent_confirm),
                    fontWeight = FontWeight.Bold,
                    color = if (isDeleting) colors.textSecondary else destructiveColor
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                modifier = Modifier.testTag("delete_permanent_cancel_button")
            ) {
                Text(
                    text = stringResource(R.string.delete_permanent_cancel),
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary
                )
            }
        }
    )
}

