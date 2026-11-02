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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioFormatInfo
import com.example.data.model.AudioTrack
import com.example.ui.common.MediaFormatBadge
import com.example.ui.common.MusicThumbnail
import com.example.ui.theme.LilithTheme
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicIconButton
import com.example.ui.theme.NeumorphicTheme
import com.example.ui.theme.neumorphic

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

    val colors = NeumorphicTheme.colors
    val progress = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val panelShape = RoundedCornerShape(20.dp)
    // Reduce shadow offset/blur by one step in dark theme (MEDIUM -> SMALL) to eliminate glowing halo
    val panelLevel = if (colors.isDark) NeumorphLevel.SMALL else NeumorphLevel.MEDIUM

    // NOTE: This is an intentional exception to the "all surfaces share the background color"
    // neumorphic rule: the mini player acts as a floating layer, so it gets its own tonal surface.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .neumorphic(
                shape = panelShape,
                level = panelLevel,
                style = NeumorphStyle.RAISED,
                lightShadowColorOverride = colors.miniPlayerLightShadow,
                darkShadowColorOverride = colors.miniPlayerDarkShadow,
                backgroundColorOverride = colors.miniPlayerSurface
            )
            .clip(panelShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .testTag("mini_player")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val trackTopCorners = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            // Progress Track: in dark theme, avoid blur filters so there is no outer halo or bleeding
            val trackModifier = if (colors.isDark) {
                Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .clip(trackTopCorners)
                    .background(colors.miniPlayerDarkShadow)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .neumorphic(
                        shape = trackTopCorners,
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.PRESSED,
                        lightShadowColorOverride = colors.miniPlayerLightShadow,
                        darkShadowColorOverride = colors.miniPlayerDarkShadow,
                        backgroundColorOverride = colors.miniPlayerSurface
                    )
                    .clip(trackTopCorners)
            }

            Box(modifier = trackModifier) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(trackTopCorners)
                        .background(colors.miniPlayerAccent)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art container: Raised rounded container using mini player surface and shadows
                val artShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .neumorphic(
                            shape = artShape,
                            level = NeumorphLevel.SMALL,
                            style = NeumorphStyle.RAISED,
                            lightShadowColorOverride = colors.miniPlayerLightShadow,
                            darkShadowColorOverride = colors.miniPlayerDarkShadow,
                            backgroundColorOverride = colors.miniPlayerSurface
                        )
                        .clip(artShape)
                        .padding(2.dp) // Inset border well
                        .clip(RoundedCornerShape(10.dp)),
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

                // Track Title, Artist, Lossy/Lossless badge
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
                            lightShadowOverride = colors.miniPlayerLightShadow,
                            darkShadowOverride = colors.miniPlayerDarkShadow,
                            textColorOverride = if (formatInfo.isLossless) colors.miniPlayerAccent else colors.miniPlayerTextSecondary
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

                // Neumorphic Play/Pause Button: raised circular button derived from mini player surface
                NeumorphicIconButton(
                    onClick = onTogglePlayPause,
                    size = 40.dp,
                    level = NeumorphLevel.SMALL,
                    contentDescription = if (isPlaying) "Jeda" else "Putar",
                    lightShadowColorOverride = colors.miniPlayerLightShadow,
                    darkShadowColorOverride = colors.miniPlayerDarkShadow,
                    backgroundColorOverride = colors.miniPlayerSurface,
                    icon = {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = colors.miniPlayerAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    modifier = Modifier.testTag("mini_player_play_pause")
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Neumorphic Skip Button: raised circular button derived from mini player surface
                NeumorphicIconButton(
                    onClick = onSkipNext,
                    size = 40.dp,
                    level = NeumorphLevel.SMALL,
                    contentDescription = "Berikutnya",
                    lightShadowColorOverride = colors.miniPlayerLightShadow,
                    darkShadowColorOverride = colors.miniPlayerDarkShadow,
                    backgroundColorOverride = colors.miniPlayerSurface,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = colors.miniPlayerTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier.testTag("mini_player_skip_next")
                )
            }
        }
    }
}

@Preview(name = "MiniPlayer - Light", showBackground = true, backgroundColor = 0xFFE0E5EC)
@Composable
fun MiniPlayerLightPreview() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .background(Color(0xFFE0E5EC))
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

@Preview(name = "MiniPlayer - Dark", showBackground = true, backgroundColor = 0xFF2B2F36)
@Composable
fun MiniPlayerDarkPreview() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(Color(0xFF2B2F36))
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
