package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.example.MainNavigationTab
import com.example.R
import com.example.data.model.AudioFormatInfo
import com.example.data.model.AudioTrack
import com.example.ui.common.MediaFormatBadge
import com.example.ui.common.MusicThumbnail
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.LilithTheme

/**
 * Global toggle to control label visibility on navigation bar.
 * Set to false per specification: icon-only navigation pill with zero reserved space for labels.
 */
const val SHOW_NAV_LABELS = false

/**
 * CompositionLocal providing the measured floating bottom navigation group height
 * (including margins and system navigation bar inset) to be used as content padding in lists.
 */
val LocalBottomContentPadding = compositionLocalOf { 0.dp }

private data class NavTabItem(
    val tab: MainNavigationTab,
    val stringResId: Int,
    val itemTag: String
)

/**
 * Floating Navigation Area featuring TWO SEPARATE FLOATING elements:
 * 1. Dark Mini Player Card on top (when a song exists), 20dp corner radius, 16dp horizontal margin.
 * 2. 10dp vertical gap.
 * 3. Icon-only outlined navigation bar shaped like a floating pill (56dp height, 28dp corner radius,
 *    16dp horizontal margin, surfaceMuted fill, 1dp subtle border, and soft low shadow).
 * 4. Vertical gradient scrim behind the floating group from transparent to theme background color.
 * 5. Hidden completely when the on-screen keyboard (IME) is visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingBottomBar(
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
    modifier: Modifier = Modifier,
    onHeightChanged: ((Int) -> Unit)? = null
) {
    val colors = AppThemeTokens.colors
    val context = LocalContext.current
    val density = LocalDensity.current
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

    val scaleAnimSpec: AnimationSpec<Float> = if (isAnimationDisabled) {
        snap()
    } else {
        tween(durationMillis = 200, easing = FastOutSlowInEasing)
    }

    // Hide floating group when on-screen keyboard (IME) is visible so it does not ride up
    val isImeVisible = WindowInsets.isImeVisible

    // Bottom margin of the group: max(navigationBars inset + 8dp, 16dp)
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val groupBottomMargin = max(navBarsBottom + 8.dp, 16.dp)

    AnimatedVisibility(
        visible = !isImeVisible,
        enter = fadeIn(animationSpec = if (isAnimationDisabled) snap() else tween(durationMillis = 150)),
        exit = fadeOut(animationSpec = if (isAnimationDisabled) snap() else tween(durationMillis = 150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    onHeightChanged?.invoke(size.height)
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            // Vertical gradient scrim behind the floating group:
            // Goes from transparent at top to theme background color at bottom.
            // Does not intercept touches (no clickable/pointer modifier).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.background.copy(alpha = 0.75f),
                                colors.background
                            )
                        )
                    )
            )

            // Floating elements group (aligned left and right with 16dp horizontal margins)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = if (isAnimationDisabled) snap() else tween(durationMillis = 200)
                    )
                    .padding(bottom = groupBottomMargin)
            ) {
                // 1. Top Element: Dark Mini Player Card (20dp radius, 16dp horizontal margin)
                if (currentTrack != null) {
                    val miniPlayerShape = RoundedCornerShape(AppTokens.Radius20)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .shadow(
                                elevation = 5.dp,
                                shape = miniPlayerShape,
                                clip = false
                            )
                            .clip(miniPlayerShape)
                            .background(colors.miniPlayerSurface, miniPlayerShape)
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

                                // Play/Pause Button (36dp circle button matching design)
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

                            // Thin Progress Line (~2.5dp along bottom edge of card)
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

                    // 10dp gap between mini player and navigation pill
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 2. Bottom Element: Floating Navigation Pill (Icon-only, 56dp height, 28dp radius)
                val pillShape = RoundedCornerShape(28.dp)
                val pillBorderColor = if (colors.isDark) {
                    Color.White.copy(alpha = 0.06f)
                } else {
                    Color.Black.copy(alpha = 0.06f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp)
                        .shadow(
                            elevation = 5.dp,
                            shape = pillShape,
                            clip = false
                        )
                        .clip(pillShape)
                        .background(colors.surfaceMuted, pillShape)
                        .border(1.dp, pillBorderColor, pillShape)
                        .testTag("bottom_nav_bar")
                ) {
                    val tabs = remember {
                        listOf(
                            NavTabItem(MainNavigationTab.MUSIC, R.string.nav_music, "nav_music_item"),
                            NavTabItem(MainNavigationTab.VIDEO, R.string.nav_video, "nav_video_item"),
                            NavTabItem(MainNavigationTab.SETTINGS, R.string.nav_settings, "nav_settings_item")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { (tab, stringResId, itemTag) ->
                            val isSelected = selectedTab == tab
                            val title = stringResource(stringResId)

                            // All three tabs use thin-line vector drawables 24dp, selected or not
                            val iconResId = when (tab) {
                                MainNavigationTab.MUSIC -> R.drawable.ic_nav_music
                                MainNavigationTab.VIDEO -> R.drawable.ic_nav_video
                                MainNavigationTab.SETTINGS -> R.drawable.ic_nav_settings
                            }

                            // Selected: icon in accentText, unselected: icon in textSecondary
                            val animatedIconColor by animateColorAsState(
                                targetValue = if (isSelected) colors.accentText else colors.textSecondary,
                                animationSpec = colorAnimSpec,
                                label = "nav_icon_color_${tab.name}"
                            )

                            // Selected: 1.0f scale, unselected: 0.92f scale (subtle, no bounce)
                            val animatedScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.0f else 0.92f,
                                animationSpec = scaleAnimSpec,
                                label = "nav_icon_scale_${tab.name}"
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
                                        // Set text semantics for testing compatibility without visual label footprint
                                        this.text = AnnotatedString(title)
                                    }
                                    .testTag(itemTag),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = iconResId),
                                    contentDescription = null,
                                    tint = animatedIconColor,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .graphicsLayer {
                                            scaleX = animatedScale
                                            scaleY = animatedScale
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility signature delegating directly to FloatingBottomBar.
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
    modifier: Modifier = Modifier,
    onHeightChanged: ((Int) -> Unit)? = null
) {
    FloatingBottomBar(
        selectedTab = selectedTab,
        onTabSelected = onTabSelected,
        currentTrack = currentTrack,
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs,
        durationMs = durationMs,
        formatInfo = formatInfo,
        onTogglePlayPause = onTogglePlayPause,
        onSkipNext = onSkipNext,
        onClickMiniPlayer = onClickMiniPlayer,
        modifier = modifier,
        onHeightChanged = onHeightChanged
    )
}

// =========================================================================
// PREVIEWS
// =========================================================================

private val SampleAudioTrack = AudioTrack(
    id = 1L,
    uri = "content://audio/1",
    title = "Astaga Bercanda (Lossless Edition)",
    artist = "Akbar Chalay, Mingse",
    album = "Download",
    durationMs = 166000L
)

private val SampleFormatInfo = AudioFormatInfo(
    codec = "FLAC",
    isLossless = true,
    bitDepth = 24,
    sampleRateHz = 48000
)

@Preview(name = "Floating Bar Light - With Track (Music) - 1.0x", fontScale = 1.0f, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Preview(name = "Floating Bar Light - With Track (Music) - 1.3x", fontScale = 1.3f, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun FloatingBottomBarLightWithTrackPreview() {
    LilithTheme(darkTheme = false) {
        Box(modifier = Modifier.background(Color(0xFFFFFFFF))) {
            FloatingBottomBar(
                selectedTab = MainNavigationTab.MUSIC,
                onTabSelected = {},
                currentTrack = SampleAudioTrack,
                isPlaying = true,
                currentPositionMs = 52000L,
                durationMs = 166000L,
                formatInfo = SampleFormatInfo,
                onTogglePlayPause = {},
                onSkipNext = {},
                onClickMiniPlayer = {}
            )
        }
    }
}

@Preview(name = "Floating Bar Dark - With Track (Video) - 1.0x", fontScale = 1.0f, showBackground = true, backgroundColor = 0xFF0E1117)
@Preview(name = "Floating Bar Dark - With Track (Video) - 1.3x", fontScale = 1.3f, showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun FloatingBottomBarDarkWithTrackPreview() {
    LilithTheme(darkTheme = true) {
        Box(modifier = Modifier.background(Color(0xFF0E1117))) {
            FloatingBottomBar(
                selectedTab = MainNavigationTab.VIDEO,
                onTabSelected = {},
                currentTrack = SampleAudioTrack,
                isPlaying = false,
                currentPositionMs = 52000L,
                durationMs = 166000L,
                formatInfo = SampleFormatInfo,
                onTogglePlayPause = {},
                onSkipNext = {},
                onClickMiniPlayer = {}
            )
        }
    }
}

@Preview(name = "Floating Bar Light - No Track (Settings) - 1.0x", fontScale = 1.0f, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Preview(name = "Floating Bar Light - No Track (Settings) - 1.3x", fontScale = 1.3f, showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun FloatingBottomBarLightNoTrackPreview() {
    LilithTheme(darkTheme = false) {
        Box(modifier = Modifier.background(Color(0xFFFFFFFF))) {
            FloatingBottomBar(
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

@Preview(name = "Floating Bar Dark - No Track (Music) - 1.0x", fontScale = 1.0f, showBackground = true, backgroundColor = 0xFF0E1117)
@Preview(name = "Floating Bar Dark - No Track (Music) - 1.3x", fontScale = 1.3f, showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun FloatingBottomBarDarkNoTrackPreview() {
    LilithTheme(darkTheme = true) {
        Box(modifier = Modifier.background(Color(0xFF0E1117))) {
            FloatingBottomBar(
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
