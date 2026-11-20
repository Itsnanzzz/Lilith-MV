package com.example.playback

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppAccent
import com.example.ui.common.MusicThumbnail
import com.example.ui.common.TimeUtils
import com.example.ui.common.WavyTrack
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.LilithTheme
import com.example.ui.theme.rememberIsAnimationDisabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

object IslandDimensions {
    val CompactWidth: Dp = 176.dp
    val CompactMinHeight: Dp = 36.dp
    val ExpandedHeight: Dp = 104.dp
    val ExpandedCornerRadius: Dp = 28.dp
}

@Immutable
data class IslandProgressSnapshot(
    val progress: Float = 0f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

@Immutable
data class IslandUiState(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val artTarget: String? = null,
    val isPlaying: Boolean = false,
    val isExpanded: Boolean = false,
    val appAccent: AppAccent = AppAccent.WHITE,
    val isDarkTheme: Boolean = true,
    val isAroundCamera: Boolean = false,
    val compactWidthDp: Dp = IslandDimensions.CompactWidth,
    val compactHeightDp: Dp = IslandDimensions.CompactMinHeight,
    val middleGapDp: Dp = 0.dp,
    val maxExpandedWidthDp: Dp = 340.dp,
    val expandedHeightDp: Dp = IslandDimensions.ExpandedHeight,
    val expandedTopPaddingDp: Dp = 14.dp,
    val initialProgress: Float = 0f,
    val initialPositionMs: Long = 0L,
    val initialDurationMs: Long = 0L,
    val isPreviewSample: Boolean = false
)

@Composable
fun DynamicIslandPill(
    state: IslandUiState,
    onReadProgress: () -> IslandProgressSnapshot,
    onExpand: () -> Unit,
    onOpenApp: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onSwipeUpDismiss: () -> Unit,
    onTouchDownChanged: (Boolean) -> Unit,
    onInteraction: () -> Unit,
    onCollapseSettled: () -> Unit = {}
) {
    LilithTheme(
        darkTheme = state.isDarkTheme,
        appAccent = state.appAccent,
        dynamicColor = false
    ) {
        val colors = AppThemeTokens.colors
        val isAnimationDisabled = rememberIsAnimationDisabled()
        val density = LocalDensity.current
        val dismissThresholdPx = remember(density) { with(density) { 24.dp.toPx() } }

        val compactWidth = if (state.isAroundCamera) {
            state.compactWidthDp.coerceIn(96.dp, 140.dp)
        } else {
            IslandDimensions.CompactWidth
        }
        val compactHeight = if (state.isAroundCamera) {
            state.compactHeightDp.coerceIn(24.dp, 34.dp)
        } else {
            state.compactHeightDp.coerceAtLeast(IslandDimensions.CompactMinHeight)
        }
        val expandedWidth = state.maxExpandedWidthDp
        val expandedHeight = if (state.isAroundCamera) {
            state.expandedHeightDp.coerceAtLeast(IslandDimensions.ExpandedHeight)
        } else {
            IslandDimensions.ExpandedHeight
        }

        val targetWidth = if (state.isExpanded) expandedWidth else compactWidth
        val targetHeight = if (state.isExpanded) expandedHeight else compactHeight
        val targetRadius = if (state.isExpanded) {
            IslandDimensions.ExpandedCornerRadius
        } else {
            compactHeight / 2f
        }

        val sizeAnimSpec = if (isAnimationDisabled) {
            snap<Dp>()
        } else {
            tween<Dp>(durationMillis = 250, easing = FastOutSlowInEasing)
        }

        val pillWidth by animateDpAsState(
            targetValue = targetWidth,
            animationSpec = sizeAnimSpec,
            label = "island_pill_width"
        )
        val pillHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = sizeAnimSpec,
            label = "island_pill_height"
        )
        val pillCornerRadius by animateDpAsState(
            targetValue = targetRadius,
            animationSpec = sizeAnimSpec,
            label = "island_pill_radius"
        )

        LaunchedEffect(state.isExpanded, compactWidth, compactHeight) {
            if (!state.isExpanded) {
                snapshotFlow {
                    pillWidth == compactWidth && pillHeight == compactHeight
                }.first { it }
                onCollapseSettled()
            }
        }

        val pillInteractionSource = remember { MutableInteractionSource() }
        val isPressed by pillInteractionSource.collectIsPressedAsState()
        LaunchedEffect(isPressed) {
            onTouchDownChanged(isPressed)
            if (!isPressed) {
                onInteraction()
            }
        }

        val nowPlayingA11yDesc = stringResource(
            R.string.island_now_playing_desc,
            state.title,
            state.artist
        )
        val pillShape = RoundedCornerShape(pillCornerRadius)

        Box(
            modifier = Modifier
                .size(width = pillWidth, height = pillHeight)
                .clip(pillShape)
                .background(colors.surface)
                .border(1.dp, colors.hairline, pillShape)
                .semantics { contentDescription = nowPlayingA11yDesc }
                .pointerInput(state.mediaId, dismissThresholdPx) {
                    var totalDragY = 0f
                    var dismissed = false
                    detectVerticalDragGestures(
                        onDragStart = {
                            totalDragY = 0f
                            dismissed = false
                            onTouchDownChanged(true)
                        },
                        onDragEnd = {
                            onTouchDownChanged(false)
                            onInteraction()
                        },
                        onDragCancel = {
                            onTouchDownChanged(false)
                            onInteraction()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            totalDragY += dragAmount
                            if (!dismissed && totalDragY < -dismissThresholdPx) {
                                dismissed = true
                                change.consume()
                                onSwipeUpDismiss()
                            }
                        }
                    )
                }
                .clickable(
                    interactionSource = pillInteractionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = {
                        onInteraction()
                        if (state.isExpanded) {
                            onOpenApp()
                        } else {
                            onExpand()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state.isExpanded,
                transitionSpec = {
                    if (isAnimationDisabled) {
                        fadeIn(snap()) togetherWith fadeOut(snap())
                    } else {
                        fadeIn(tween(durationMillis = 180, delayMillis = 50)) togetherWith
                            fadeOut(tween(durationMillis = 110))
                    }
                },
                label = "island_mode_content"
            ) { expanded ->
                if (!expanded) {
                    CompactPillBody(
                        state = state,
                        isAnimationDisabled = isAnimationDisabled
                    )
                } else {
                    ExpandedPillBody(
                        state = state,
                        onReadProgress = onReadProgress,
                        isAnimationDisabled = isAnimationDisabled,
                        onPrevious = {
                            onInteraction()
                            onPrevious()
                        },
                        onTogglePlayPause = {
                            onInteraction()
                            onTogglePlayPause()
                        },
                        onNext = {
                            onInteraction()
                            onNext()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPillBody(
    state: IslandUiState,
    isAnimationDisabled: Boolean
) {
    val colors = AppThemeTokens.colors

    if (!state.isAroundCamera) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                MusicThumbnail(
                    artTarget = state.artTarget,
                    contentDescription = stringResource(R.string.island_artwork_desc),
                    modifier = Modifier.fillMaxSize()
                )
            }

            CompactEqualizerBars(
                isPlaying = state.isPlaying,
                isAnimationDisabled = isAnimationDisabled,
                accentColor = colors.accentText
            )
        }
    } else {
        val compactWidth = state.compactWidthDp.coerceIn(96.dp, 140.dp)
        val compactHeight = state.compactHeightDp.coerceIn(24.dp, 34.dp)
        val artSize = (compactHeight - 8.dp).coerceAtLeast(16.dp)
        val minSideWingsWidth = artSize + 24.dp
        val middleGap = state.middleGapDp.coerceIn(0.dp, (compactWidth - minSideWingsWidth).coerceAtLeast(0.dp))

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(artSize)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    MusicThumbnail(
                        artTarget = state.artTarget,
                        contentDescription = stringResource(R.string.island_artwork_desc),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(middleGap))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                CompactEqualizerBars(
                    isPlaying = state.isPlaying,
                    isAnimationDisabled = isAnimationDisabled,
                    accentColor = colors.accentText,
                    barHeight = 12.dp,
                    barWidth = 2.5.dp,
                    barSpacing = 2.dp,
                    endPadding = 0.dp
                )
            }
        }
    }
}

@Composable
private fun CompactEqualizerBars(
    isPlaying: Boolean,
    isAnimationDisabled: Boolean,
    accentColor: Color,
    barHeight: Dp = 14.dp,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.5.dp,
    endPadding: Dp = 2.dp
) {
    val barFactors = if (isPlaying && !isAnimationDisabled) {
        val transition = rememberInfiniteTransition(label = "island_eq_transition")
        val bar1 by transition.animateFloat(
            initialValue = 0.30f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "island_eq_bar1"
        )
        val bar2 by transition.animateFloat(
            initialValue = 0.90f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 360, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "island_eq_bar2"
        )
        val bar3 by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.88f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 480, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "island_eq_bar3"
        )
        listOf(bar1, bar2, bar3)
    } else {
        listOf(0.42f, 0.78f, 0.54f)
    }

    Row(
        modifier = Modifier
            .height(barHeight)
            .padding(end = endPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(barSpacing)
    ) {
        barFactors.forEach { factor ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(factor.coerceIn(0.25f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(accentColor)
            )
        }
    }
}

@Composable
private fun ExpandedPillBody(
    state: IslandUiState,
    onReadProgress: () -> IslandProgressSnapshot,
    isAnimationDisabled: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit
) {
    val colors = AppThemeTokens.colors

    var progressSnapshot by remember(state.mediaId, state.isPreviewSample) {
        mutableStateOf(
            IslandProgressSnapshot(
                progress = state.initialProgress.coerceIn(0f, 1f),
                positionMs = state.initialPositionMs,
                durationMs = state.initialDurationMs
            )
        )
    }

    LaunchedEffect(state.isExpanded, state.isPlaying, state.mediaId, state.isPreviewSample) {
        if (!state.isExpanded || state.isPreviewSample) return@LaunchedEffect
        progressSnapshot = onReadProgress()
        while (isActive && state.isPlaying) {
            delay(500L)
            progressSnapshot = onReadProgress()
        }
    }

    val progressStateDesc = if (progressSnapshot.durationMs > 0L) {
        val posRoundedMs = (progressSnapshot.positionMs.coerceAtLeast(0L) / 1000L) * 1000L
        stringResource(
            R.string.island_progress_state_desc,
            TimeUtils.formatDuration(posRoundedMs),
            TimeUtils.formatDuration(progressSnapshot.durationMs)
        )
    } else {
        val pct = (progressSnapshot.progress.coerceIn(0f, 1f) * 100f).roundToInt()
        stringResource(R.string.island_progress_percent_desc, pct)
    }

    val topPadding = if (state.isAroundCamera) state.expandedTopPaddingDp else 14.dp

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = topPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                MusicThumbnail(
                    artTarget = state.artTarget,
                    contentDescription = stringResource(R.string.island_artwork_desc),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.title,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (isAnimationDisabled) TextOverflow.Ellipsis else TextOverflow.Clip,
                    modifier = if (!isAnimationDisabled) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            repeatDelayMillis = 1000,
                            initialDelayMillis = 1200,
                            spacing = MarqueeSpacing(24.dp),
                            velocity = 36.dp
                        )
                    } else {
                        Modifier
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = state.artist,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onPrevious),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.control_previous),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = state.isPlaying,
                        animationSpec = if (isAnimationDisabled) snap() else tween(180),
                        label = "island_play_pause_crossfade"
                    ) { playing ->
                        Icon(
                            imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) {
                                stringResource(R.string.control_pause)
                            } else {
                                stringResource(R.string.control_play)
                            },
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.control_next),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        key(state.mediaId, state.isPreviewSample) {
            WavyTrack(
                progress = progressSnapshot.progress,
                playing = state.isPlaying,
                color = colors.accentText,
                trackColor = colors.accentText.copy(alpha = 0.28f),
                strokeWidth = 3.dp,
                amplitude = 2.5.dp,
                wavelength = 22.dp,
                height = 14.dp,
                visible = state.isExpanded,
                stateDescriptionText = progressStateDesc,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            )
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Composable
private fun IslandPreviewContainer(state: IslandUiState) {
    LilithTheme(darkTheme = state.isDarkTheme, appAccent = state.appAccent, dynamicColor = false) {
        val bgColor = AppThemeTokens.colors.surfaceMuted
        Box(
            modifier = Modifier
                .background(bgColor)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            DynamicIslandPill(
                state = state,
                onReadProgress = {
                    IslandProgressSnapshot(
                        progress = state.initialProgress,
                        positionMs = state.initialPositionMs,
                        durationMs = state.initialDurationMs
                    )
                },
                onExpand = {},
                onOpenApp = {},
                onPrevious = {},
                onTogglePlayPause = {},
                onNext = {},
                onSwipeUpDismiss = {},
                onTouchDownChanged = {},
                onInteraction = {}
            )
        }
    }
}

@Preview(name = "Compact Island - Light Playing", showBackground = true)
@Composable
private fun PreviewCompactIslandLightPlaying() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_compact_light",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = true,
            isExpanded = false,
            appAccent = AppAccent.CYAN,
            isDarkTheme = false
        )
    )
}

@Preview(name = "Compact Island - Dark Paused", showBackground = true)
@Composable
private fun PreviewCompactIslandDarkPaused() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_compact_dark",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = false,
            isExpanded = false,
            appAccent = AppAccent.CYAN,
            isDarkTheme = true
        )
    )
}

@Preview(name = "Expanded Island - Light Playing 40%", showBackground = true)
@Composable
private fun PreviewExpandedIslandLightPlaying40() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_expanded_light_40",
            title = "Message In A Bottle (Taylor's Version)",
            artist = "Taylor Swift",
            isPlaying = true,
            isExpanded = true,
            appAccent = AppAccent.CYAN,
            isDarkTheme = false,
            initialProgress = 0.40f,
            initialPositionMs = 88_000L,
            initialDurationMs = 220_000L
        )
    )
}

@Preview(name = "Expanded Island - Dark Playing 0%", showBackground = true)
@Composable
private fun PreviewExpandedIslandDarkPlaying0() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_expanded_dark_0",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = true,
            isExpanded = true,
            appAccent = AppAccent.GREEN,
            isDarkTheme = true,
            initialProgress = 0f,
            initialPositionMs = 0L,
            initialDurationMs = 286_000L
        )
    )
}

@Preview(name = "Expanded Island - Light Paused 100%", showBackground = true)
@Composable
private fun PreviewExpandedIslandLightPaused100() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_expanded_light_100",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = false,
            isExpanded = true,
            appAccent = AppAccent.BLUE,
            isDarkTheme = false,
            initialProgress = 1f,
            initialPositionMs = 286_000L,
            initialDurationMs = 286_000L
        )
    )
}

@Preview(name = "Expanded Island - Dark Paused 40%", showBackground = true)
@Composable
private fun PreviewExpandedIslandDarkPaused40() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_expanded_dark_40",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = false,
            isExpanded = true,
            appAccent = AppAccent.CYAN,
            isDarkTheme = true,
            initialProgress = 0.40f,
            initialPositionMs = 114_000L,
            initialDurationMs = 286_000L
        )
    )
}

@Preview(name = "Around Camera (30dp) - Compact Light", showBackground = true)
@Composable
private fun PreviewAroundCameraCompactLight() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_around_compact_light",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = true,
            isExpanded = false,
            appAccent = AppAccent.CYAN,
            isDarkTheme = false,
            isAroundCamera = true,
            compactWidthDp = 106.dp,
            compactHeightDp = 34.dp,
            middleGapDp = 38.dp,
            expandedHeightDp = 126.dp,
            expandedTopPaddingDp = 36.dp
        )
    )
}

@Preview(name = "Around Camera (30dp) - Compact Dark", showBackground = true)
@Composable
private fun PreviewAroundCameraCompactDark() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_around_compact_dark",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = false,
            isExpanded = false,
            appAccent = AppAccent.CYAN,
            isDarkTheme = true,
            isAroundCamera = true,
            compactWidthDp = 106.dp,
            compactHeightDp = 34.dp,
            middleGapDp = 38.dp,
            expandedHeightDp = 126.dp,
            expandedTopPaddingDp = 36.dp
        )
    )
}

@Preview(name = "Around Camera (30dp) - Expanded Light", showBackground = true)
@Composable
private fun PreviewAroundCameraExpandedLight() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_around_expanded_light",
            title = "Message In A Bottle (Taylor's Version)",
            artist = "Taylor Swift",
            isPlaying = true,
            isExpanded = true,
            appAccent = AppAccent.CYAN,
            isDarkTheme = false,
            isAroundCamera = true,
            compactWidthDp = 106.dp,
            compactHeightDp = 34.dp,
            middleGapDp = 38.dp,
            expandedHeightDp = 126.dp,
            expandedTopPaddingDp = 36.dp,
            initialProgress = 0.40f,
            initialPositionMs = 88_000L,
            initialDurationMs = 220_000L
        )
    )
}

@Preview(name = "Around Camera (30dp) - Expanded Dark", showBackground = true)
@Composable
private fun PreviewAroundCameraExpandedDark() {
    IslandPreviewContainer(
        IslandUiState(
            mediaId = "preview_around_expanded_dark",
            title = "Midnight Echoes",
            artist = "Lilith Studio",
            isPlaying = true,
            isExpanded = true,
            appAccent = AppAccent.GREEN,
            isDarkTheme = true,
            isAroundCamera = true,
            compactWidthDp = 106.dp,
            compactHeightDp = 34.dp,
            middleGapDp = 38.dp,
            expandedHeightDp = 126.dp,
            expandedTopPaddingDp = 36.dp,
            initialProgress = 0.40f,
            initialPositionMs = 114_000L,
            initialDurationMs = 286_000L
        )
    )
}
