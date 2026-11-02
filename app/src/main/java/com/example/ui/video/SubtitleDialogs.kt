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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.ui.theme.LocalNeumorphicColors
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicCard
import com.example.ui.theme.NeumorphicIconButton
import com.example.ui.theme.NeumorphicSlider
import com.example.ui.theme.neumorphic

@Composable
fun SubtitleTrackDialog(
    tracks: List<SubtitleTrackInfo>,
    selectedId: String?,
    onSelectTrack: (String?) -> Unit,
    onAddExternalSubtitle: (Uri, String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val nc = LocalNeumorphicColors.current

    // SAF picker for external subtitle file
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
        containerColor = nc.background,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .neumorphic(
                                shape = CircleShape,
                                level = NeumorphLevel.SMALL,
                                style = NeumorphStyle.RAISED
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = nc.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.subtitle_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = nc.textPrimary
                    )
                }

                NeumorphicIconButton(
                    onClick = onOpenSettings,
                    size = 38.dp,
                    level = NeumorphLevel.SMALL
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.subtitle_settings),
                        tint = nc.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ASS/SSA Note
                Text(
                    text = stringResource(R.string.subtitle_ass_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = nc.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Option: OFF
                    item {
                        val isOffSelected = selectedId == null
                        NeumorphicCard(
                            shape = RoundedCornerShape(14.dp),
                            level = NeumorphLevel.SMALL,
                            style = if (isOffSelected) NeumorphStyle.PRESSED else NeumorphStyle.RAISED,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTrack(null)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .neumorphic(
                                            shape = CircleShape,
                                            level = NeumorphLevel.SMALL,
                                            style = if (isOffSelected) NeumorphStyle.RAISED else NeumorphStyle.PRESSED
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isOffSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = nc.accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = stringResource(R.string.subtitle_none),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isOffSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isOffSelected) nc.accent else nc.textPrimary
                                )
                            }
                        }
                    }

                    // Available Subtitle Tracks
                    items(tracks) { track ->
                        val isSelected = track.id == selectedId || track.isSelected
                        NeumorphicCard(
                            shape = RoundedCornerShape(14.dp),
                            level = NeumorphLevel.SMALL,
                            style = if (isSelected) NeumorphStyle.PRESSED else NeumorphStyle.RAISED,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectTrack(track.id)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .neumorphic(
                                            shape = CircleShape,
                                            level = NeumorphLevel.SMALL,
                                            style = if (isSelected) NeumorphStyle.RAISED else NeumorphStyle.PRESSED
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = nc.accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) nc.accent else nc.textPrimary
                                    )
                                    if (track.isExternal) {
                                        Text(
                                            text = "Berkas Eksternal",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = nc.textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Button to add external file
                NeumorphicCard(
                    shape = RoundedCornerShape(14.dp),
                    level = NeumorphLevel.SMALL,
                    style = NeumorphStyle.RAISED,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            safSubLauncher.launch(
                                arrayOf(
                                    "application/x-subrip",
                                    "text/vtt",
                                    "text/x-ssa",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        }
                        .testTag("add_external_subtitle_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = nc.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.subtitle_add_external),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = nc.accent
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = nc.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SubtitleSettingsDialog(
    settings: SubtitleSettings,
    onSave: (SubtitleSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val nc = LocalNeumorphicColors.current

    var fontSize by remember { mutableFloatStateOf(settings.fontSizeSp) }
    var textColorHex by remember { mutableFloatStateOf(settings.textColorHex.toFloat()) }
    var bgAlpha by remember {
        val alphaByte = ((settings.backgroundColorHex shr 24) and 0xFF).toFloat() / 255f
        mutableFloatStateOf(alphaByte)
    }
    var delaySec by remember { mutableFloatStateOf(settings.delayMs / 1000f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = nc.background,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .neumorphic(
                            shape = CircleShape,
                            level = NeumorphLevel.SMALL,
                            style = NeumorphStyle.RAISED
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = nc.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.subtitle_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = nc.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Live Subtitle Preview Box (Inset style)
                Text(
                    text = "Pratinjau Langsung",
                    style = MaterialTheme.typography.labelMedium,
                    color = nc.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .neumorphic(
                            shape = RoundedCornerShape(14.dp),
                            level = NeumorphLevel.SMALL,
                            style = NeumorphStyle.PRESSED
                        )
                        .background(Color.Black.copy(alpha = 0.85f))
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
                            text = "Lilith MV — Contoh Teks Subtitle",
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = nc.textPrimary
                    )
                    Text(
                        text = "${fontSize.toInt()} sp",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = nc.accent
                    )
                }
                NeumorphicSlider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 12f..32f,
                    modifier = Modifier.padding(top = 4.dp),
                    testTag = "subtitle_size_slider"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Text Color choices
                Text(
                    text = stringResource(R.string.subtitle_color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = nc.textPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                val colorChoices = listOf(
                    0xFFFFFFFFL to "Putih",
                    0xFFFFEB3BL to "Kuning",
                    0xFF00E5FFL to "Cyan"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    colorChoices.forEach { (colorHex, name) ->
                        val isSelected = textColorHex.toLong() == colorHex
                        NeumorphicCard(
                            shape = RoundedCornerShape(12.dp),
                            level = NeumorphLevel.SMALL,
                            style = if (isSelected) NeumorphStyle.PRESSED else NeumorphStyle.RAISED,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { textColorHex = colorHex.toFloat() }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Background Opacity
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_background),
                        style = MaterialTheme.typography.bodyMedium,
                        color = nc.textPrimary
                    )
                    Text(
                        text = "${(bgAlpha * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = nc.accent
                    )
                }
                NeumorphicSlider(
                    value = bgAlpha,
                    onValueChange = { bgAlpha = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(top = 4.dp),
                    testTag = "subtitle_bg_opacity_slider"
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Delay Adjustment
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_delay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = nc.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.subtitle_delay_seconds, delaySec),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = nc.accent
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NeumorphicCard(
                        shape = RoundedCornerShape(12.dp),
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.RAISED,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { delaySec -= 0.5f }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("-0.5s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = nc.textPrimary)
                        }
                    }
                    NeumorphicCard(
                        shape = RoundedCornerShape(12.dp),
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.PRESSED,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { delaySec = 0f }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = nc.accent)
                        }
                    }
                    NeumorphicCard(
                        shape = RoundedCornerShape(12.dp),
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.RAISED,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { delaySec += 0.5f }
                    ) {
                        Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("+0.5s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = nc.textPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
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
            ) {
                Text(
                    text = stringResource(R.string.playlist_save),
                    color = nc.accent,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.playlist_cancel),
                    color = nc.textSecondary
                )
            }
        }
    )
}

@Composable
fun AudioTrackDialog(
    audioTracks: List<AudioTrackChoice>,
    onSelectTrack: (AudioTrackChoice) -> Unit,
    onDismiss: () -> Unit
) {
    val nc = LocalNeumorphicColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = nc.background,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .neumorphic(
                            shape = CircleShape,
                            level = NeumorphLevel.SMALL,
                            style = NeumorphStyle.RAISED
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = nc.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.audio_tracks),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = nc.textPrimary
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (audioTracks.isEmpty()) {
                    Text(
                        text = stringResource(R.string.audio_track_default),
                        style = MaterialTheme.typography.bodyMedium,
                        color = nc.textSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(audioTracks) { choice ->
                            NeumorphicCard(
                                shape = RoundedCornerShape(14.dp),
                                level = NeumorphLevel.SMALL,
                                style = if (choice.isSelected) NeumorphStyle.PRESSED else NeumorphStyle.RAISED,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectTrack(choice)
                                        onDismiss()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .neumorphic(
                                                shape = CircleShape,
                                                level = NeumorphLevel.SMALL,
                                                style = if (choice.isSelected) NeumorphStyle.RAISED else NeumorphStyle.PRESSED
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (choice.isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = nc.accent,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = choice.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (choice.isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (choice.isSelected) nc.accent else nc.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Tutup", color = nc.accent, fontWeight = FontWeight.Bold)
            }
        }
    )
}
