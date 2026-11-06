package com.example.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AudioFormatInfo
import com.example.data.model.AudioTrack
import com.example.ui.common.MediaFormatBadge
import com.example.ui.common.MusicThumbnail
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.LilithTheme

/**
 * Standalone clean, flat Mini Player Card matching reference layout:
 * - Sleek dark container (miniPlayerSurface) with 16dp rounded corners
 * - 40dp album art with 8dp radius
 * - SemiBold white title, muted artist, format badge
 * - 36dp circular white play/pause button
 * - Flat skip next icon button
 * - 2.5dp progress line along bottom edge
 */
@Composable
fun MiniPlayer(
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    formatInfo: AudioFormatInfo,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val colors = AppThemeTokens.colors
    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val cardShape = RoundedCornerShape(AppTokens.Radius16)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(cardShape)
            .background(colors.miniPlayerSurface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("mini_player")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art (~40dp, rounded 8dp)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius8))
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    val artTarget = currentTrack.albumArtUri?.takeIf { it.isNotBlank() } ?: currentTrack.uri
                    MusicThumbnail(
                        artTarget = artTarget,
                        contentDescription = currentTrack.album,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Title, Artist, and Format badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.miniPlayerTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MediaFormatBadge(
                            formatInfo = formatInfo,
                            surfaceOverride = colors.miniPlayerSurface,
                            textColorOverride = colors.miniPlayerTextSecondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = currentTrack.artist,
                            fontSize = 12.sp,
                            color = colors.miniPlayerTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play/Pause Button (36dp filled circle)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.miniPlayerTextPrimary)
                        .clickable(
                            role = Role.Button,
                            onClick = onTogglePlayPause
                        )
                        .testTag("mini_player_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.control_pause) else stringResource(R.string.control_play),
                        tint = colors.miniPlayerSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Skip Next Button (36dp flat icon)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = onSkipNext
                        )
                        .testTag("mini_player_skip_next"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.control_next),
                        tint = colors.miniPlayerTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Progress line along bottom edge of card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(colors.miniPlayerTextSecondary.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(colors.miniPlayerProgress)
                )
            }
        }
    }
}

@Preview(name = "MiniPlayer - Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun MiniPlayerLightPreview() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .background(Color(0xFFFFFFFF))
                .padding(16.dp)
        ) {
            MiniPlayer(
                currentTrack = AudioTrack(
                    id = 1L,
                    uri = "content://audio/1",
                    title = "Astaga Bercanda (Lossless Edition)",
                    artist = "Akbar Chalay, Mingse",
                    album = "Download",
                    durationMs = 166000L
                ),
                isPlaying = true,
                currentPositionMs = 45000L,
                durationMs = 166000L,
                formatInfo = AudioFormatInfo(codec = "FLAC", isLossless = true, bitDepth = 24, sampleRateHz = 48000),
                onTogglePlayPause = {},
                onSkipNext = {},
                onClick = {}
            )
        }
    }
}

@Preview(name = "MiniPlayer - Dark", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun MiniPlayerDarkPreview() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(Color(0xFF0E1117))
                .padding(16.dp)
        ) {
            MiniPlayer(
                currentTrack = AudioTrack(
                    id = 1L,
                    uri = "content://audio/1",
                    title = "Astaga Bercanda (Lossless Edition)",
                    artist = "Akbar Chalay, Mingse",
                    album = "Download",
                    durationMs = 166000L
                ),
                isPlaying = false,
                currentPositionMs = 45000L,
                durationMs = 166000L,
                formatInfo = AudioFormatInfo(codec = "AAC", isLossless = false, bitrateKbps = 320, sampleRateHz = 44100),
                onTogglePlayPause = {},
                onSkipNext = {},
                onClick = {}
            )
        }
    }
}
