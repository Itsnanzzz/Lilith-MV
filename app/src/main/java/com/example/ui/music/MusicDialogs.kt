package com.example.ui.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.db.PlaylistEntity
import com.example.data.model.AudioTrack
import com.example.data.model.SongExtraInfo
import com.example.playback.AudioFormatDetector
import com.example.playback.EqualizerState
import com.example.ui.common.TimeUtils
import com.example.ui.theme.AppChip
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppSecondaryButton
import com.example.ui.theme.AppSlider
import com.example.ui.theme.AppSwitch
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun EqualizerDialog(
    state: EqualizerState,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPreset: (Int) -> Unit,
    onSetBassBoost: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors
    var localBass by remember(state.bassBoostStrength) { mutableFloatStateOf(state.bassBoostStrength.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.equalizer_title),
                    style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (!state.isAvailable) {
                    Text(
                        text = stringResource(R.string.equalizer_not_supported),
                        style = AppTypography.body,
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
                            style = AppTypography.body.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                        AppSwitch(
                            checked = state.isEnabled,
                            onCheckedChange = onToggleEnabled
                        )
                    }

                    if (state.isEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = stringResource(R.string.equalizer_preset),
                            style = AppTypography.label,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(state.presets) { index, presetName ->
                                AppChip(
                                    text = presetName,
                                    selected = state.currentPreset == index,
                                    onClick = { onSelectPreset(index) }
                                )
                            }
                        }

                        if (state.isAvailable) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.equalizer_bass_boost),
                                    style = AppTypography.label,
                                    color = colors.textSecondary
                                )
                                Text(
                                    text = "${(localBass / 10).toInt()}%",
                                    style = AppTypography.label,
                                    color = colors.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            AppSlider(
                                value = localBass / 1000f,
                                onValueChange = { frac ->
                                    val newBass = (frac * 1000).toInt()
                                    localBass = newBass.toFloat()
                                    onSetBassBoost(newBass)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppPrimaryButton(
                text = stringResource(R.string.close),
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun SleepTimerDialog(
    remainingSeconds: Int = 0,
    onSetTimerMinutes: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors
    val timerOptions = listOf(15, 30, 45, 60, 90)
    val remainingMinutes = if (remainingSeconds > 0) ((remainingSeconds + 59) / 60).toInt() else null

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AvTimer,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sleep_timer),
                    style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (remainingMinutes != null && remainingMinutes > 0) {
                    Text(
                        text = stringResource(R.string.sleep_timer_active, remainingMinutes),
                        style = AppTypography.body.copy(fontWeight = FontWeight.Medium),
                        color = colors.primary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                }

                Text(
                    text = stringResource(R.string.sleep_timer_select_duration),
                    style = AppTypography.label,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    timerOptions.take(4).forEach { mins ->
                        AppChip(
                            text = "${mins}m",
                            selected = remainingMinutes == mins,
                            onClick = {
                                onSetTimerMinutes(mins)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (remainingMinutes != null && remainingMinutes > 0) {
                    AppSecondaryButton(
                        text = stringResource(R.string.sleep_timer_off),
                        onClick = {
                            onSetTimerMinutes(0)
                            onDismiss()
                        }
                    )
                }
                AppPrimaryButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss
                )
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
    val colors = AppThemeTokens.colors
    val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.playback_speed),
                    style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                speedPresets.forEach { speed ->
                    val isSelected = currentSpeed == speed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTokens.Radius12))
                            .background(if (isSelected) colors.surfaceMuted else Color.Transparent)
                            .clickable {
                                onSelectSpeed(speed)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${speed}x",
                            color = colors.textPrimary,
                            style = AppTypography.body.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppPrimaryButton(
                text = stringResource(R.string.close),
                onClick = onDismiss
            )
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.playlist_create),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(AppTokens.RadiusPill))
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (playlistName.isEmpty()) {
                        Text(
                            text = stringResource(R.string.playlist_name_hint),
                            color = colors.textSecondary,
                            style = AppTypography.body
                        )
                    }
                    BasicTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        singleLine = true,
                        textStyle = AppTypography.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("playlist_name_input")
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSecondaryButton(
                    text = stringResource(R.string.playlist_cancel),
                    onClick = onDismiss
                )
                AppPrimaryButton(
                    text = stringResource(R.string.playlist_save),
                    onClick = {
                        if (playlistName.isNotBlank()) {
                            onConfirm(playlistName.trim())
                        }
                    },
                    testTag = "save_playlist_button"
                )
            }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistEntity>,
    onSelectPlaylist: (PlaylistEntity) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.add_to_playlist),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (playlists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.playlist_empty),
                        style = AppTypography.body,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppTokens.Radius12))
                                    .clickable {
                                        onSelectPlaylist(playlist)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = playlist.name,
                                    style = AppTypography.body.copy(fontWeight = FontWeight.Medium),
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSecondaryButton(
                    text = stringResource(R.string.playlist_cancel),
                    onClick = onDismiss
                )
                AppPrimaryButton(
                    text = stringResource(R.string.playlist_create),
                    onClick = {
                        onDismiss()
                        onCreateNewPlaylist()
                    }
                )
            }
        }
    )
}

/**
 * Permanent Delete Confirmation Dialog:
 * - 24dp radius container in surface
 * - 18sp SemiBold title
 * - 14sp textSecondary body with warning
 * - "Cancel" AppSecondaryButton
 * - "Delete" pill button filled with destructive color and white text
 */
@Composable
fun DeletePermanentConfirmationDialog(
    track: AudioTrack,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.delete_permanent_title),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
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
                    style = AppTypography.listTitle,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.delete_permanent_message, track.title),
                    style = AppTypography.body,
                    color = colors.textSecondary,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                shape = RoundedCornerShape(AppTokens.RadiusPill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.destructive,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.testTag("delete_permanent_confirm_button")
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.delete_permanent_confirm),
                    color = Color.White,
                    style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        },
        dismissButton = {
            AppSecondaryButton(
                text = stringResource(R.string.delete_permanent_cancel),
                onClick = onDismiss,
                testTag = "delete_permanent_cancel_button"
            )
        }
    )
}

/**
 * Flat Song Details Dialog:
 * Displays track metadata immediately and lazily loads codec/bitrate/sample rate/bit depth/channels.
 * Long-press on Path copies it to clipboard and triggers onCopyPath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongInfoDialog(
    track: AudioTrack,
    onDismiss: () -> Unit,
    onCopyPath: suspend () -> Unit,
    loadExtraInfo: suspend (AudioTrack) -> SongExtraInfo
) {
    val colors = AppThemeTokens.colors
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var extraInfo by remember(track.uri) { mutableStateOf<SongExtraInfo?>(null) }

    LaunchedEffect(track.uri) {
        extraInfo = loadExtraInfo(track)
    }

    val loadingText = stringResource(R.string.info_loading)
    val unknownText = stringResource(R.string.info_unknown)
    val formatInfo = remember(track.mimeType) {
        AudioFormatDetector.detectFromMime(track.mimeType)
    }
    val qualityText = when {
        !track.mimeType.isNullOrBlank() -> {
            if (formatInfo.isLossless) stringResource(R.string.format_lossless) else stringResource(R.string.format_lossy)
        }
        track.extension.lowercase(Locale.ROOT) in setOf("flac", "alac", "wav", "aiff", "pcm") -> {
            stringResource(R.string.format_lossless)
        }
        track.extension.isNotBlank() -> {
            stringResource(R.string.format_lossy)
        }
        else -> unknownText
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.song_info_title),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SongInfoDetailRow(label = stringResource(R.string.info_name), value = track.fullDisplayName)
                    SongInfoDetailRow(label = stringResource(R.string.info_title), value = track.title)
                    SongInfoDetailRow(label = stringResource(R.string.info_artist), value = track.artist)
                    SongInfoDetailRow(label = stringResource(R.string.info_album), value = track.album)

                    val displayPath = track.folderPath
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTokens.Radius8))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    clipboardManager.setText(AnnotatedString(displayPath))
                                    coroutineScope.launch {
                                        onCopyPath()
                                    }
                                }
                            )
                            .padding(vertical = 2.dp)
                            .testTag("song_info_path")
                    ) {
                        Text(
                            text = stringResource(R.string.info_path),
                            style = AppTypography.caption,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayPath,
                            style = AppTypography.body.copy(fontSize = 13.sp),
                            color = colors.primary
                        )
                    }

                    val sizeStr = if (track.size > 0) TimeUtils.formatFileSize(track.size) else unknownText
                    SongInfoDetailRow(label = stringResource(R.string.info_size), value = sizeStr)
                    SongInfoDetailRow(label = stringResource(R.string.info_duration), value = TimeUtils.formatDuration(track.durationMs))

                    val typeStr = track.mimeType?.takeIf { it.isNotBlank() } ?: unknownText
                    SongInfoDetailRow(label = stringResource(R.string.info_type), value = typeStr)
                    SongInfoDetailRow(label = stringResource(R.string.info_quality), value = qualityText)

                    SongInfoDetailRow(
                        label = stringResource(R.string.info_codec),
                        value = extraInfo?.codec ?: loadingText
                    )
                    SongInfoDetailRow(
                        label = stringResource(R.string.info_bitrate),
                        value = extraInfo?.bitrate ?: loadingText
                    )
                    SongInfoDetailRow(
                        label = stringResource(R.string.info_sample_rate),
                        value = extraInfo?.sampleRate ?: loadingText
                    )
                    SongInfoDetailRow(
                        label = stringResource(R.string.info_bit_depth),
                        value = extraInfo?.bitDepth ?: loadingText
                    )
                    SongInfoDetailRow(
                        label = stringResource(R.string.info_channels),
                        value = extraInfo?.channels ?: loadingText
                    )

                    val addedStr = if (track.dateAdded > 0) TimeUtils.formatTimestamp(track.dateAdded) else unknownText
                    SongInfoDetailRow(label = stringResource(R.string.info_date_added), value = addedStr)

                    val modStr = if (track.dateModified > 0) TimeUtils.formatTimestamp(track.dateModified) else unknownText
                    SongInfoDetailRow(label = stringResource(R.string.info_date_modified), value = modStr)
                }
            }
        },
        confirmButton = {
            AppSecondaryButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                testTag = "info_dialog_close_button"
            )
        }
    )
}

@Composable
private fun SongInfoDetailRow(label: String, value: String) {
    val colors = AppThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypography.caption,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = AppTypography.body,
            color = colors.textPrimary
        )
    }
}
