package com.example.ui.music

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppIconButton
import com.example.ui.theme.AppSlider
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import com.example.ui.theme.rememberIsAnimationDisabled

/**
 * Clean, minimal, flat full-screen Now Playing view matching reference layout:
 * - Flat top bar: chevron-down left, "Sedang Memutar" (16sp SemiBold) centered, queue icon right
 * - Artwork: square with 28dp radius, no shadow/glow, clean surfaceMuted placeholder
 * - Title: 22sp Bold centered with smooth marquee and soft fade edges
 * - Artist & Album: 15sp Medium centered
 * - Format Badge: codec, sample rate, bit depth centered below artist
 * - Seek Bar: flat AppSlider with 12sp tabular figures below (current left, duration right)
 * - Controls row: Shuffle, Previous, giant 72dp filled primary circle Play/Pause button, Next, Repeat
 * - Subtle secondary chips for Speed, Sleep Timer, and Equalizer
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
    playbackSpeed: Float = 1.0f,
    sleepTimerRemainingSec: Int = 0,
    onOpenSpeedDialog: () -> Unit = {},
    onOpenSleepTimerDialog: () -> Unit = {},
    onOpenEqualizerDialog: () -> Unit = {}
) {
    if (track == null) return

    val colors = AppThemeTokens.colors
    BackHandler(onBack = onCollapse)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(currentPositionMs.toFloat()) }

    LaunchedEffect(track.id) {
        isDraggingSlider = false
    }

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
        val maxArtWidth = (maxWidth - 48.dp).coerceAtMost(if (isCompactHeight) 280.dp else 340.dp)
        val spacingLarge = if (isCompactHeight) 12.dp else 20.dp
        val spacingMedium = if (isCompactHeight) 8.dp else 12.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Top Bar: Flat Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconButton(
                    onClick = onCollapse,
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.close),
                    iconSize = 26.dp,
                    tintOverride = colors.textPrimary,
                    testTag = "collapse_now_playing_button"
                )

                Text(
                    text = stringResource(R.string.now_playing),
                    style = AppTypography.screenTitle.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )

                AppIconButton(
                    onClick = onOpenQueueSheet,
                    icon = Icons.Default.QueueMusic,
                    contentDescription = stringResource(R.string.queue_title),
                    iconSize = 22.dp,
                    tintOverride = colors.textPrimary,
                    testTag = "open_queue_button"
                )
            }

            Spacer(modifier = Modifier.height(spacingLarge))

            // 2. Large Artwork: Square, 28dp radius, flat subtle surfaceMuted well
            val artShape = RoundedCornerShape(AppTokens.Radius28)
            Box(
                modifier = Modifier
                    .size(maxArtWidth)
                    .aspectRatio(1f)
                    .clip(artShape)
                    .background(colors.surfaceMuted),
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

            // 3. Track Title & Artist/Album (Centered, 22sp Bold title, smooth marquee)
            NowPlayingHeaderInfo(
                title = track.title,
                artistAndAlbum = "${track.artist} • ${track.album}",
                trackId = track.id,
                colors = colors
            )

            Spacer(modifier = Modifier.height(spacingMedium))

            // 4. Format Info Badge (FLAC / AAC / MP3, Lossless / Lossy, Sample Rate)
            MediaFormatBadge(
                formatInfo = formatInfo,
                showDetails = true,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(spacingLarge))

            // 5. Seek Bar (Flat AppSlider)
            val maxDuration = durationMs.coerceAtLeast(1L).toFloat()
            val sliderFraction = (displayPositionMs.toFloat() / maxDuration).coerceIn(0f, 1f)

            AppSlider(
                value = sliderFraction,
                onValueChange = { frac ->
                    isDraggingSlider = true
                    dragPositionMs = frac * maxDuration
                },
                onValueChangeFinished = {
                    onSeek(dragPositionMs.toLong())
                    isDraggingSlider = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("now_playing_slider")
            )

            // Timestamps: Elapsed and Duration (12sp tabular figures)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = TimeUtils.formatDuration(displayPositionMs),
                    style = AppTypography.caption,
                    color = colors.textSecondary
                )
                Text(
                    text = TimeUtils.formatDuration(durationMs),
                    style = AppTypography.caption,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(spacingLarge))

            // 6. Main Playback Controls:
            // Shuffle (~48dp), Previous (~48dp), Giant 72dp Play/Pause Circle, Next (~48dp), Repeat (~48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button (Active = textPrimary + surfaceMuted circle + 4dp dot below)
                NowPlayingToggleButton(
                    icon = Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.shuffle_on),
                    isActive = shuffleEnabled,
                    onClick = onToggleShuffle,
                    colors = colors,
                    testTag = "toggle_shuffle_button"
                )

                // Skip Previous Button (48dp flat icon)
                AppIconButton(
                    onClick = onSkipPrevious,
                    icon = Icons.Default.SkipPrevious,
                    contentDescription = stringResource(R.string.control_previous),
                    iconSize = 28.dp,
                    tintOverride = colors.textPrimary,
                    testTag = "skip_previous_button"
                )

                // Giant 72dp Primary Filled Play/Pause Circle (Matches Reference Design)
                MainPlayPauseCircleButton(
                    isPlaying = isPlaying,
                    onClick = onTogglePlayPause,
                    colors = colors
                )

                // Skip Next Button (48dp flat icon)
                AppIconButton(
                    onClick = onSkipNext,
                    icon = Icons.Default.SkipNext,
                    contentDescription = stringResource(R.string.control_next),
                    iconSize = 28.dp,
                    tintOverride = colors.textPrimary,
                    testTag = "skip_next_button"
                )

                // Repeat Button (Active = textPrimary + surfaceMuted circle + 4dp dot below)
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                NowPlayingToggleButton(
                    icon = repeatIcon,
                    contentDescription = stringResource(R.string.repeat_mode_all),
                    isActive = isRepeatActive,
                    onClick = onToggleRepeat,
                    colors = colors,
                    testTag = "toggle_repeat_button"
                )
            }

            Spacer(modifier = Modifier.height(spacingLarge))

            // 7. Secondary Controls Row: Speed, Sleep Timer, Equalizer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryUtilityPill(
                    icon = Icons.Default.Speed,
                    label = "${playbackSpeed}x",
                    contentDescription = stringResource(R.string.playback_speed),
                    onClick = onOpenSpeedDialog,
                    colors = colors,
                    isActive = playbackSpeed != 1.0f
                )

                Spacer(modifier = Modifier.width(10.dp))

                val timerLabel = if (sleepTimerRemainingSec > 0) {
                    "${((sleepTimerRemainingSec + 59) / 60)}m"
                } else {
                    stringResource(R.string.sleep_timer)
                }
                SecondaryUtilityPill(
                    icon = Icons.Default.AvTimer,
                    label = timerLabel,
                    contentDescription = stringResource(R.string.sleep_timer),
                    onClick = onOpenSleepTimerDialog,
                    colors = colors,
                    isActive = sleepTimerRemainingSec > 0
                )

                Spacer(modifier = Modifier.width(10.dp))

                SecondaryUtilityPill(
                    icon = Icons.Default.Equalizer,
                    label = stringResource(R.string.equalizer_title),
                    contentDescription = stringResource(R.string.equalizer_title),
                    onClick = onOpenEqualizerDialog,
                    colors = colors
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Giant 72dp Primary Play/Pause Circle matching the reference image.
 */
@Composable
private fun MainPlayPauseCircleButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    colors: AppColors
) {
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!isAnimationDisabled && isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = AppTokens.AnimDurationShort),
        label = "main_play_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(colors.accent)
            .then(
                if (colors.isLightWhiteAccent) {
                    Modifier.border(1.dp, colors.hairline, CircleShape)
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .testTag("main_play_pause_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) stringResource(R.string.control_pause) else stringResource(R.string.control_play),
            tint = colors.onAccent,
            modifier = Modifier.size(34.dp)
        )
    }
}

/**
 * Toggle button for Shuffle and Repeat.
 * When active: textPrimary icon + 40dp surfaceMuted circle + 4dp dot below.
 * When inactive: textSecondary icon in transparent 48dp box.
 */
@Composable
private fun NowPlayingToggleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    onClick: () -> Unit,
    colors: AppColors,
    testTag: String
) {
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!isAnimationDisabled && isPressed) 0.94f else 1.0f,
        animationSpec = tween(durationMillis = AppTokens.AnimDurationShort),
        label = "toggle_btn_scale"
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = colors.accentText,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(colors.foregroundAccent)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Compact secondary utility pill for Speed, Sleep Timer, and Equalizer.
 */
@Composable
private fun SecondaryUtilityPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    colors: AppColors,
    isActive: Boolean = false
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(AppTokens.RadiusPill))
            .background(if (isActive) colors.surfaceMuted else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) colors.accentText else colors.textSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isActive) colors.accentText else colors.textSecondary
        )
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
    colors: AppColors,
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

    key(trackId) {
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track Title with Marquee & Fading Edges (22sp Bold from AppTypography.nowPlayingTitle)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .fadingHorizontalEdges(edgeWidth = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = AppTypography.nowPlayingTitle,
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
                    style = AppTypography.body,
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
