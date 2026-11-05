package com.example.ui.video

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.SubtitleSettings
import com.example.data.model.SubtitleTrackInfo
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppSecondaryButton
import com.example.ui.theme.AppSlider
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography

/**
 * Clean Flat Subtitle Track Selection Dialog.
 */
@Composable
fun SubtitleTrackDialog(
    tracks: List<SubtitleTrackInfo>,
    selectedId: String?,
    onSelectTrack: (String?) -> Unit,
    onAddExternalSubtitle: (Uri, String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors

    val safSubLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val name = it.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
            onAddExternalSubtitle(it, name)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(AppTokens.Radius24),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.subtitle_title),
                        style = AppTypography.screenTitle.copy(fontSize = 18.sp),
                        color = colors.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.subtitle_settings),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.subtitle_ass_note),
                    style = AppTypography.caption,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Option: OFF
                    item {
                        val isOffSelected = selectedId == null
                        val itemShape = RoundedCornerShape(AppTokens.Radius12)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(if (isOffSelected) colors.surfaceMuted else Color.Transparent)
                                .clickable {
                                    onSelectTrack(null)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.subtitle_none),
                                style = AppTypography.body.copy(
                                    fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = colors.textPrimary
                            )
                            if (isOffSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = colors.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Available Subtitle Tracks
                    items(tracks) { track ->
                        val isSelected = track.id == selectedId || track.isSelected
                        val itemShape = RoundedCornerShape(AppTokens.Radius12)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(itemShape)
                                .background(if (isSelected) colors.surfaceMuted else Color.Transparent)
                                .clickable {
                                    onSelectTrack(track.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.label,
                                    style = AppTypography.body.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = colors.textPrimary
                                )
                                if (track.isExternal) {
                                    Text(
                                        text = stringResource(R.string.subtitle_external_file),
                                        style = AppTypography.caption,
                                        color = colors.textSecondary
                                    )
                                }
                            }
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

                Spacer(modifier = Modifier.height(14.dp))

                // Button to add external file
                AppSecondaryButton(
                    text = stringResource(R.string.subtitle_add_external),
                    icon = Icons.Default.Add,
                    onClick = {
                        safSubLauncher.launch(
                            arrayOf(
                                "application/x-subrip",
                                "text/vtt",
                                "text/x-ssa",
                                "text/plain",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_external_subtitle_button")
                )
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

/**
 * Clean Flat Subtitle Customization Dialog.
 */
@Composable
fun SubtitleSettingsDialog(
    settings: SubtitleSettings,
    onSave: (SubtitleSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors

    var fontSize by remember { mutableFloatStateOf(settings.fontSizeSp) }
    var textColorHex by remember { mutableFloatStateOf(settings.textColorHex.toFloat()) }
    var bgAlpha by remember {
        val alphaByte = ((settings.backgroundColorHex shr 24) and 0xFF).toFloat() / 255f
        mutableFloatStateOf(alphaByte)
    }
    var delaySec by remember { mutableFloatStateOf(settings.delayMs / 1000f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(AppTokens.Radius24),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.subtitle_settings),
                    style = AppTypography.screenTitle.copy(fontSize = 18.sp),
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Live Subtitle Preview Box
                Text(
                    text = stringResource(R.string.subtitle_live_preview),
                    style = AppTypography.label,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius12))
                        .background(Color.Black)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val previewBgColor = Color(red = 0f, green = 0f, blue = 0f, alpha = bgAlpha)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(previewBgColor)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.subtitle_sample_text),
                            color = Color(textColorHex.toInt()),
                            fontSize = fontSize.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Font Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_font_size),
                        style = AppTypography.label,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${fontSize.toInt()} sp",
                        style = AppTypography.label,
                        color = colors.primary
                    )
                }
                AppSlider(
                    value = ((fontSize - 12f) / 20f).coerceIn(0f, 1f),
                    onValueChange = { frac -> fontSize = 12f + frac * 20f },
                    modifier = Modifier.padding(top = 2.dp),
                    testTag = "subtitle_size_slider"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Text Color choices
                Text(
                    text = stringResource(R.string.subtitle_color),
                    style = AppTypography.label,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                val colorChoices = listOf(
                    0xFFFFFFFFL to stringResource(R.string.subtitle_color_white),
                    0xFFFFEB3BL to stringResource(R.string.subtitle_color_yellow),
                    0xFF00E5FFL to stringResource(R.string.subtitle_color_cyan)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorChoices.forEach { (colorHex, name) ->
                        val isSelected = textColorHex.toLong() == colorHex
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(AppTokens.RadiusPill))
                                .background(if (isSelected) colors.surfaceMuted else Color.Transparent)
                                .clickable { textColorHex = colorHex.toFloat() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color(colorHex)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Background Opacity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_background),
                        style = AppTypography.label,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${(bgAlpha * 100).toInt()}%",
                        style = AppTypography.label,
                        color = colors.primary
                    )
                }
                AppSlider(
                    value = bgAlpha,
                    onValueChange = { bgAlpha = it },
                    modifier = Modifier.padding(top = 2.dp),
                    testTag = "subtitle_bg_opacity_slider"
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Delay Adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_delay),
                        style = AppTypography.label,
                        color = colors.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.subtitle_delay_seconds, delaySec),
                        style = AppTypography.label,
                        color = colors.primary
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppTokens.RadiusPill))
                            .background(colors.surfaceMuted)
                            .clickable { delaySec -= 0.5f }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-0.5s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppTokens.RadiusPill))
                            .background(colors.surfaceMuted)
                            .clickable { delaySec = 0f }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.reset), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(AppTokens.RadiusPill))
                            .background(colors.surfaceMuted)
                            .clickable { delaySec += 0.5f }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+0.5s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
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
                    text = stringResource(R.string.playlist_save),
                    onClick = {
                        val alphaByte = (bgAlpha * 255).toLong() shl 24
                        val newBg = alphaByte or 0x000000L
                        onSave(
                            SubtitleSettings(
                                fontSizeSp = fontSize,
                                textColorHex = textColorHex.toLong(),
                                backgroundColorHex = newBg,
                                delayMs = (delaySec * 1000).toLong()
                            )
                        )
                        onDismiss()
                    }
                )
            }
        }
    )
}

/**
 * Clean Flat Audio Track Selection Dialog.
 */
@Composable
fun AudioTrackDialog(
    audioTracks: List<AudioTrackChoice>,
    onSelectTrack: (AudioTrackChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(AppTokens.Radius24),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Audiotrack,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.audio_tracks),
                    style = AppTypography.screenTitle.copy(fontSize = 18.sp),
                    color = colors.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (audioTracks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.audio_track_default),
                        style = AppTypography.body,
                        color = colors.textSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(audioTracks) { choice ->
                            val itemShape = RoundedCornerShape(AppTokens.Radius12)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(itemShape)
                                    .background(if (choice.isSelected) colors.surfaceMuted else Color.Transparent)
                                    .clickable {
                                        onSelectTrack(choice)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = choice.name,
                                    style = AppTypography.body.copy(
                                        fontWeight = if (choice.isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = colors.textPrimary
                                )
                                if (choice.isSelected) {
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
