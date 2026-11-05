package com.example.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainNavigationTab
import com.example.R
import com.example.data.model.AudioFormatInfo
import com.example.data.model.AudioTrack
import com.example.ui.common.MediaFormatBadge
import com.example.ui.common.MusicThumbnail
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import com.example.ui.theme.LilithTheme

/**
 * Global toggle to control label visibility on navigation bar.
 */
const val SHOW_NAV_LABELS = true

/**
 * Clean, flat Unified Docked Bottom Panel combining:
 * 1. Mini player row inside a sleek dark rounded card (matching reference layout).
 * 2. 3-tab navigation bar (Music, Video, Settings).
 * Completely straight, edge-to-edge rectangular panel (0dp radius) with a 1dp hairline divider at top.
 */
@Composable
fun UnifiedDockedBottomBar(
    selectedTab: MainNavigationTab,
    onTabSelected: (MainNavigationTab) -> Unit,
    currentTrack: AudioTrack?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    formatInfo: AudioFormatInfo,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onClickMiniPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeTokens.colors
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isAnimationDisabled = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    val colorAnimSpec: AnimationSpec<Color> = if (isAnimationDisabled) {
        snap()
    } else {
        tween(durationMillis = 200, easing = FastOutSlowInEasing)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .background(colors.surface, RectangleShape)
            .testTag("docked_bottom_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = if (isAnimationDisabled) snap() else tween(durationMillis = 250)
                )
                .navigationBarsPadding()
        ) {
            // Clean 1dp straight hairline divider spanning edge-to-edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.hairline)
            )

            // 1. Mini Player Card (Only visible when a song exists)
            // Matches Reference: Dark rounded pill/card with album art, title, artist, play/pause circle, next icon, and progress line
            if (currentTrack != null) {
                val cardShape = RoundedCornerShape(AppTokens.Radius16)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(cardShape)
                        .background(colors.miniPlayerSurface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClickMiniPlayer
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

                            // Title & Artist + Lossy/Lossless badge
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

                            // Play/Pause Button (36dp filled circle matching reference)
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

                            // Next Button (36dp flat icon)
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

                        // Thin Progress Line (~2dp along bottom of card)
                        val progress = if (durationMs > 0) {
                            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

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

            // 2. Navigation Row (3 tabs: Music, Video, Settings)
            val tabs = remember {
                listOf(
                    Triple(MainNavigationTab.MUSIC, R.string.nav_music, "nav_music_item"),
                    Triple(MainNavigationTab.VIDEO, R.string.nav_video, "nav_video_item"),
                    Triple(MainNavigationTab.SETTINGS, R.string.nav_settings, "nav_settings_item")
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (SHOW_NAV_LABELS) 56.dp else 50.dp)
                    .testTag("bottom_nav_bar"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { (tab, stringResId, testTag) ->
                    val isSelected = selectedTab == tab
                    val title = stringResource(stringResId)

                    val iconVector = when (tab) {
                        MainNavigationTab.MUSIC -> if (isSelected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote
                        MainNavigationTab.VIDEO -> if (isSelected) Icons.Filled.Videocam else Icons.Outlined.Videocam
                        MainNavigationTab.SETTINGS -> if (isSelected) Icons.Filled.Settings else Icons.Outlined.Settings
                    }

                    val animatedItemColor by animateColorAsState(
                        targetValue = if (isSelected) colors.accentText else colors.textSecondary,
                        animationSpec = colorAnimSpec,
                        label = "nav_item_color_${tab.name}"
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
                        if (SHOW_NAV_LABELS) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    tint = animatedItemColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = animatedItemColor,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = animatedItemColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(name = "Docked Bar Light - With Track (Music)", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun UnifiedDockedBottomBarLightWithTrackPreview() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier.background(Color(0xFFFFFFFF))
        ) {
            UnifiedDockedBottomBar(
                selectedTab = MainNavigationTab.MUSIC,
                onTabSelected = {},
                currentTrack = AudioTrack(
                    id = 1L,
                    uri = "content://audio/1",
                    title = "Astaga Bercanda (Lossless Edition)",
                    artist = "Akbar Chalay, Mingse",
                    album = "Download",
                    durationMs = 166000L
                ),
                isPlaying = true,
                currentPositionMs = 52000L,
                durationMs = 166000L,
                formatInfo = AudioFormatInfo(codec = "FLAC", isLossless = true, bitDepth = 24, sampleRateHz = 48000),
                onTogglePlayPause = {},
                onSkipNext = {},
                onClickMiniPlayer = {}
            )
        }
    }
}

@Preview(name = "Docked Bar Dark - With Track (Video)", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun UnifiedDockedBottomBarDarkWithTrackPreview() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier.background(Color(0xFF0E1117))
        ) {
            UnifiedDockedBottomBar(
                selectedTab = MainNavigationTab.VIDEO,
                onTabSelected = {},
                currentTrack = AudioTrack(
                    id = 1L,
                    uri = "content://audio/1",
                    title = "Astaga Bercanda (Lossless Edition)",
                    artist = "Akbar Chalay, Mingse",
                    album = "Download",
                    durationMs = 166000L
                ),
                isPlaying = false,
                currentPositionMs = 52000L,
                durationMs = 166000L,
                formatInfo = AudioFormatInfo(codec = "AAC", isLossless = false, bitrateKbps = 320, sampleRateHz = 44100),
                onTogglePlayPause = {},
                onSkipNext = {},
                onClickMiniPlayer = {}
            )
        }
    }
}

@Preview(name = "Docked Bar Light - No Track (Settings)", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun UnifiedDockedBottomBarLightNoTrackPreview() {
    LilithTheme(darkTheme = false) {
        Box(
            modifier = Modifier.background(Color(0xFFFFFFFF))
        ) {
            UnifiedDockedBottomBar(
                selectedTab = MainNavigationTab.SETTINGS,
                onTabSelected = {},
                currentTrack = null,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                formatInfo = AudioFormatInfo(),
                onTogglePlayPause = {},
                onSkipNext = {},
                onClickMiniPlayer = {}
            )
        }
    }
}

@Preview(name = "Docked Bar Dark - No Track (Music)", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun UnifiedDockedBottomBarDarkNoTrackPreview() {
    LilithTheme(darkTheme = true) {
        Box(
            modifier = Modifier.background(Color(0xFF0E1117))
        ) {
            UnifiedDockedBottomBar(
                selectedTab = MainNavigationTab.MUSIC,
                onTabSelected = {},
                currentTrack = null,
                isPlaying = false,
                currentPositionMs = 0L,
                durationMs = 0L,
                formatInfo = AudioFormatInfo(),
                onTogglePlayPause = {},
                onSkipNext = {},
                onClickMiniPlayer = {}
            )
        }
    }
}
