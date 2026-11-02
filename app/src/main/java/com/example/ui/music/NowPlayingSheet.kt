package com.example.ui.music

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.example.R
import com.example.data.model.AudioFormatInfo
import com.example.data.model.AudioTrack
import com.example.ui.common.MediaFormatBadge
import com.example.ui.common.MusicThumbnail
import com.example.ui.common.TimeUtils
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicColors
import com.example.ui.theme.NeumorphicIconButton
import com.example.ui.theme.NeumorphicSlider
import com.example.ui.theme.NeumorphicTheme
import com.example.ui.theme.NeumorphicTokens
import com.example.ui.theme.neumorphic

/**
 * Neumorphic full-screen Now Playing view.
 *
 * Rebalanced without the secondary bottom row:
 * - Scaled up album art taking advantage of freed vertical space
 * - Smooth marquee for long titles and artist/album with soft fade edges
 * - Interactive and responsive seek bar with tap-to-seek and live drag preview
 * - Scaled-down ergonomic playback controls anchored toward the bottom
 */
@Composable
fun NowPlayingSheet(
    track: AudioTrack?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    formatInfo: AudioFormatInfo,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenQueueSheet: () -> Unit,
    modifier: Modifier = Modifier,
    // Kept with default values for backward compatibility
    playbackSpeed: Float = 1.0f,
    sleepTimerRemainingSec: Int = 0,
    onOpenSpeedDialog: () -> Unit = {},
    onOpenSleepTimerDialog: () -> Unit = {},
    onOpenEqualizerDialog: () -> Unit = {}
) {
    if (track == null) return

    val colors = NeumorphicTheme.colors
    BackHandler(onBack = onCollapse)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(currentPositionMs.toFloat()) }

    // When track changes, reset slider drag state
    LaunchedEffect(track.id) {
        isDraggingSlider = false
    }

    // While dragging, display the live dragged position; ignore incoming player ticks
    val displayPositionMs = if (isDraggingSlider) {
        dragPositionMs.toLong()
    } else {
        currentPositionMs
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("now_playing_screen")
    ) {
        val screenHeight = maxHeight
        val isCompactHeight = screenHeight < 720.dp

        // Dynamic album art size scaling: up to 340dp on tall phones, 260dp on compact screens
        val maxArtWidth = (maxWidth - 36.dp).coerceAtMost(if (isCompactHeight) 260.dp else 340.dp)
        val spacingLarge = if (isCompactHeight) 12.dp else 20.dp
        val spacingMedium = if (isCompactHeight) 8.dp else 14.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Header: Circular Neumorphic Collapse (~40dp), Title, Queue (~40dp) with 48dp touch targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NeumorphicIconButton(
                        onClick = onCollapse,
                        size = 40.dp,
                        level = NeumorphLevel.SMALL,
                        contentDescription = "Tutup",
                        icon = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        modifier = Modifier.testTag("collapse_now_playing_button")
                    )
                }

                Text(
                    text = stringResource(R.string.now_playing),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NeumorphicIconButton(
                        onClick = onOpenQueueSheet,
                        size = 40.dp,
                        level = NeumorphLevel.SMALL,
                        contentDescription = stringResource(R.string.queue_title),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.testTag("open_queue_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacingLarge))

            // Enlarged Album Art: Raised container with soft depth & small inset border
            val artShape = RoundedCornerShape(NeumorphicTokens.RadiusLarge)
            Box(
                modifier = Modifier
                    .size(maxArtWidth)
                    .aspectRatio(1f)
                    .neumorphic(
                        shape = artShape,
                        level = NeumorphLevel.LARGE,
                        style = NeumorphStyle.RAISED,
                        lightShadowColorOverride = colors.lightShadow,
                        darkShadowColorOverride = colors.darkShadow,
                        backgroundColorOverride = colors.surface
                    )
                    .clip(artShape)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(NeumorphicTokens.RadiusLarge - 4.dp)),
                contentAlignment = Alignment.Center
            ) {
                val artTarget = track.albumArtUri?.takeIf { it.isNotBlank() } ?: track.uri
                MusicThumbnail(
                    artTarget = artTarget,
                    contentDescription = track.album,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(spacingLarge))

            // Track Title & Artist/Album with smooth Marquee and soft fade edges
            NowPlayingHeaderInfo(
                title = track.title,
                artistAndAlbum = "${track.artist} • ${track.album}",
                trackId = track.id,
                colors = colors
            )

            Spacer(modifier = Modifier.height(spacingMedium))

            // Neumorphic Inset Technical Badge (LOSSY / LOSSLESS)
            MediaFormatBadge(
                formatInfo = formatInfo,
                showDetails = true,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(spacingLarge))

            // Neumorphic Seek Bar (Draggable across entire 48dp height + tap-to-seek)
            val maxDuration = durationMs.coerceAtLeast(1L).toFloat()
            val sliderPos = displayPositionMs.coerceIn(0L, durationMs).toFloat()

            NeumorphicSlider(
                value = sliderPos,
                onValueChange = {
                    isDraggingSlider = true
                    dragPositionMs = it
                },
                valueRange = 0f..maxDuration,
                onValueChangeFinished = {
                    onSeek(dragPositionMs.toLong())
                    isDraggingSlider = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_slider")
            )

            // Elapsed and Duration labels (live updates while dragging)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = TimeUtils.formatDuration(displayPositionMs),
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = TimeUtils.formatDuration(durationMs),
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(spacingLarge))

            // Main Playback Controls: Shuffle (~50dp), Previous (~50dp), Play/Pause (~72dp), Next (~50dp), Repeat (~50dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle (Toggle: Pressed when active)
                NeumorphicIconButton(
                    onClick = onToggleShuffle,
                    size = 50.dp,
                    level = NeumorphLevel.SMALL,
                    isActive = shuffleEnabled,
                    contentDescription = "Acak",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = if (shuffleEnabled) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    modifier = Modifier.testTag("toggle_shuffle_button")
                )

                // Previous
                NeumorphicIconButton(
                    onClick = onSkipPrevious,
                    size = 50.dp,
                    level = NeumorphLevel.SMALL,
                    contentDescription = stringResource(R.string.control_previous),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.testTag("skip_previous_button")
                )

                // Play/Pause Main Button (~72dp container with ~30dp icon)
                NeumorphicIconButton(
                    onClick = onTogglePlayPause,
                    size = 72.dp,
                    level = NeumorphLevel.LARGE,
                    contentDescription = if (isPlaying) stringResource(R.string.control_pause) else stringResource(R.string.control_play),
                    icon = {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(30.dp)
                        )
                    },
                    modifier = Modifier.testTag("main_play_pause_button")
                )

                // Next
                NeumorphicIconButton(
                    onClick = onSkipNext,
                    size = 50.dp,
                    level = NeumorphLevel.SMALL,
                    contentDescription = stringResource(R.string.control_next),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    modifier = Modifier.testTag("skip_next_button")
                )

                // Repeat (Toggle: Pressed when not off)
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                NeumorphicIconButton(
                    onClick = onToggleRepeat,
                    size = 50.dp,
                    level = NeumorphLevel.SMALL,
                    isActive = isRepeatActive,
                    contentDescription = "Ulang",
                    icon = {
                        val icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isRepeatActive) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    },
                    modifier = Modifier.testTag("toggle_repeat_button")
                )
            }

            // Bottom breathing room anchored toward the bottom
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

/**
 * Displays Title and Artist/Album text with a smooth looping marquee when overflowing,
 * soft gradient fade edges on left & right, and fallback to single-line ellipsis if animations are disabled.
 */
@Composable
private fun NowPlayingHeaderInfo(
    title: String,
    artistAndAlbum: String,
    trackId: Long,
    colors: NeumorphicColors,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f
            )
            scale > 0f
        } catch (_: Exception) {
            true
        }
    }

    // Keyed by trackId to cleanly restart marquee whenever a new track starts playing
    key(trackId) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track Title with Marquee & Fading Edges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .fadingHorizontalEdges(edgeWidth = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (animationsEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (animationsEnabled) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            repeatDelayMillis = 1000,
                            initialDelayMillis = 1500,
                            spacing = MarqueeSpacing(28.dp),
                            velocity = 40.dp
                        )
                    } else {
                        Modifier
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Artist & Album with Marquee & Fading Edges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .fadingHorizontalEdges(edgeWidth = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = artistAndAlbum,
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (animationsEnabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = if (animationsEnabled) {
                        Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            animationMode = MarqueeAnimationMode.Immediately,
                            repeatDelayMillis = 1000,
                            initialDelayMillis = 1500,
                            spacing = MarqueeSpacing(28.dp),
                            velocity = 40.dp
                        )
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/**
 * Masks the content with soft transparent fade edges on the left and right sides.
 */
private fun Modifier.fadingHorizontalEdges(edgeWidth: Dp = 16.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edgeWidthPx = edgeWidth.toPx()
        if (edgeWidthPx > 0f && size.width > edgeWidthPx * 2) {
            val fadeBrush = Brush.horizontalGradient(
                0f to Color.Transparent,
                (edgeWidthPx / size.width) to Color.Black,
                (1f - edgeWidthPx / size.width) to Color.Black,
                1f to Color.Transparent
            )
            drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
        }
    }
