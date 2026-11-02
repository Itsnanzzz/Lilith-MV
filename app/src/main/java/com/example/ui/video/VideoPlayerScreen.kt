package com.example.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.data.model.AspectRatioMode
import com.example.data.model.VideoItem
import com.example.ui.common.TimeUtils
import com.example.ui.music.PlaybackSpeedDialog
import com.example.ui.theme.VideoOverlayTokens
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    viewModel: VideoPlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sidePadding = if (isLandscape) 24.dp else 16.dp

    val uiState by viewModel.uiState.collectAsState()

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSubtitleSettingsDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    var floatingModeMessage by remember { mutableStateOf<String?>(null) }
    var isDraggingSeekBar by remember { mutableStateOf(false) }

    val isAnimationDisabled = remember(context) {
        try {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (e: Exception) {
            false
        }
    }

    // Cancel auto-hide when any dialog or slider dragging is active
    val isOverlayInteractionActive = showAudioDialog ||
            showSubtitleDialog ||
            showSubtitleSettingsDialog ||
            showSpeedDialog ||
            isDraggingSeekBar

    LaunchedEffect(isOverlayInteractionActive) {
        if (isOverlayInteractionActive) {
            viewModel.cancelControlsAutoHide()
        } else if (uiState.controlsVisible && uiState.isPlaying) {
            viewModel.scheduleControlsAutoHide()
        }
    }

    // Auto-hide floating aspect ratio label after ~1.2s
    LaunchedEffect(floatingModeMessage) {
        if (floatingModeMessage != null) {
            delay(1200)
            floatingModeMessage = null
        }
    }

    // Keep screen on during video playback and handle immersive system bars
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Load video into ViewModel
    LaunchedEffect(video.uri) {
        viewModel.loadVideo(video)
    }

    // Handle rotation lock toggle
    LaunchedEffect(uiState.isLockedRotation) {
        activity?.requestedOrientation = if (uiState.isLockedRotation) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
    ) {
        // ExoPlayer PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    player = viewModel.player
                    resizeMode = when (uiState.aspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                }
            },
            update = { view ->
                view.player = viewModel.player
                view.resizeMode = when (uiState.aspectRatioMode) {
                    AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture detector overlay: tap to toggle controls, double tap for seek, vertical drag for brightness/volume
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            viewModel.toggleControlsVisibility()
                        },
                        onDoubleTap = { offset ->
                            val width = size.width
                            if (offset.x < width / 2) {
                                viewModel.doubleTapSeek(-10)
                            } else {
                                viewModel.doubleTapSeek(10)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val width = size.width
                        val normalizedDelta = -dragAmount / size.height
                        if (change.position.x < width / 2) {
                            // Left side: adjust brightness
                            val cur = uiState.brightnessLevel ?: 0.5f
                            val newB = (cur + normalizedDelta).coerceIn(0.05f, 1.0f)
                            activity?.let { act ->
                                val lp = act.window.attributes
                                lp.screenBrightness = newB
                                act.window.attributes = lp
                            }
                            viewModel.setBrightnessLevel(newB)
                        } else {
                            // Right side: adjust volume
                            viewModel.adjustVolume(normalizedDelta)
                        }
                    }
                }
        )

        // HUD Feedback: Volume, Brightness, Double-tap Seek
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Seek delta indicator
            uiState.seekDelta?.let { delta ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (delta > 0) Icons.Default.Forward10 else Icons.Default.Replay10,
                            contentDescription = null,
                            tint = VideoOverlayTokens.Accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (delta > 0) "+$delta dtk" else "$delta dtk",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            // Volume HUD
            uiState.volumeLevel?.let { vol ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = VideoOverlayTokens.Accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(vol * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            // Brightness HUD
            uiState.brightnessLevel?.let { b ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BrightnessMedium,
                            contentDescription = null,
                            tint = VideoOverlayTokens.Accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(b * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
            }

            // Floating Aspect Ratio Mode Badge (auto-hidden after ~1.2s)
            AnimatedVisibility(
                visible = floatingModeMessage != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = floatingModeMessage.orEmpty(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Resume Prompt Card ("Lanjutkan dari X:XX?")
        if (uiState.resumePositionMs != null) {
            val resumeFormatted = TimeUtils.formatDuration(uiState.resumePositionMs!!)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.88f))
                    .testTag("resume_video_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.video_resume_prompt, resumeFormatted),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.resumeFromSavedPosition() },
                        colors = ButtonDefaults.buttonColors(containerColor = VideoOverlayTokens.Accent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("resume_button")
                    ) {
                        Text(stringResource(R.string.video_resume_action), fontSize = 12.sp, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { viewModel.dismissResumePrompt() }) {
                        Text(stringResource(R.string.video_start_over), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }
        }

        // Unsupported codec error banner
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(18.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // CONTROL OVERLAYS (Top Bar, Center Play/Pause, Bottom Controls)
        val animSpec = if (isAnimationDisabled) snap() else tween<Float>(durationMillis = 200)

        AnimatedVisibility(
            visible = uiState.controlsVisible,
            enter = fadeIn(animSpec),
            exit = fadeOut(animSpec)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 1. Top Scrim (Black ~60% to Transparent, 96dp + status bar)
                val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp + statusBarInset)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(VideoOverlayTokens.ScrimTop, Color.Transparent)
                            )
                        )
                )

                // 2. Bottom Scrim (Transparent to Black ~70%, 140dp + navigation bar)
                val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp + navBarInset)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, VideoOverlayTokens.ScrimBottom)
                            )
                        )
                )

                // 3. Main Controls Structure
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // --- TOP ACTION BAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = sidePadding, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VideoOverlayIconButton(
                            onClick = onBack,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            testTag = "video_back_button"
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Video Title with smooth looping marquee if long
                        Text(
                            text = video.title,
                            color = VideoOverlayTokens.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = if (!isAnimationDisabled) TextOverflow.Clip else TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (!isAnimationDisabled) {
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
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Action Buttons at end with 8dp spacing
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Aspect Ratio Mode Button
                            VideoOverlayIconButton(
                                onClick = {
                                    viewModel.cycleAspectRatio()
                                    viewModel.scheduleControlsAutoHide()
                                    // Trigger floating pill feedback
                                    val nextMode = when (uiState.aspectRatioMode) {
                                        AspectRatioMode.FIT -> AspectRatioMode.FILL
                                        AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                                        AspectRatioMode.ZOOM -> AspectRatioMode.FIT
                                    }
                                    floatingModeMessage = when (nextMode) {
                                        AspectRatioMode.FIT -> context.getString(R.string.aspect_ratio_mode_fit)
                                        AspectRatioMode.FILL -> context.getString(R.string.aspect_ratio_mode_fill)
                                        AspectRatioMode.ZOOM -> context.getString(R.string.aspect_ratio_mode_zoom)
                                    }
                                },
                                icon = Icons.Default.AspectRatio,
                                contentDescription = stringResource(R.string.aspect_ratio_fit),
                                testTag = "aspect_ratio_button"
                            )

                            // Audio Tracks Button
                            VideoOverlayIconButton(
                                onClick = { showAudioDialog = true },
                                icon = Icons.Default.Audiotrack,
                                contentDescription = stringResource(R.string.audio_tracks),
                                testTag = "audio_track_button"
                            )

                            // Subtitles Button (Active state if a subtitle track is active)
                            VideoOverlayIconButton(
                                onClick = { showSubtitleDialog = true },
                                icon = Icons.Default.Subtitles,
                                contentDescription = stringResource(R.string.subtitle_title),
                                isActive = uiState.selectedSubtitleId != null,
                                testTag = "subtitle_track_button"
                            )

                            // Picture in Picture Button
                            VideoOverlayIconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity.enterPictureInPictureMode(params)
                                    }
                                },
                                icon = Icons.Default.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.pip_mode),
                                testTag = "pip_mode_button"
                            )
                        }
                    }

                    // --- CENTER PLAY / PAUSE BUTTON ---
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoOverlayPlayPauseButton(
                            isPlaying = uiState.isPlaying,
                            isAnimationDisabled = isAnimationDisabled,
                            onClick = {
                                viewModel.togglePlayPause()
                                viewModel.scheduleControlsAutoHide()
                            },
                            modifier = Modifier.testTag("video_play_pause_button")
                        )
                    }

                    // --- BOTTOM BAR CONTROLS ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = sidePadding, vertical = 8.dp)
                    ) {
                        // Isolated Seek Bar composable so position polling doesn't recompose the full screen
                        VideoSeekBar(
                            viewModel = viewModel,
                            onDragStateChanged = { dragging ->
                                isDraggingSeekBar = dragging
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bottom Control Row: Rotation Lock, Loop, Speed (Consistent ~40dp circles, 8dp spacing)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rotation Lock Button
                            VideoOverlayIconButton(
                                onClick = {
                                    viewModel.toggleRotationLock()
                                    viewModel.scheduleControlsAutoHide()
                                },
                                icon = if (uiState.isLockedRotation) Icons.Default.Lock else Icons.Default.ScreenRotation,
                                contentDescription = stringResource(R.string.rotation_lock),
                                isActive = uiState.isLockedRotation,
                                testTag = "rotation_lock_button"
                            )

                            // Loop Button
                            VideoOverlayIconButton(
                                onClick = {
                                    viewModel.toggleLooping()
                                    viewModel.scheduleControlsAutoHide()
                                },
                                icon = Icons.Default.Loop,
                                contentDescription = if (uiState.isLooping) stringResource(R.string.loop_on) else stringResource(R.string.loop_off),
                                isActive = uiState.isLooping,
                                testTag = "loop_toggle_button"
                            )

                            // Playback Speed Button (Displays e.g. "1.0x" in 12sp semi-bold inside 40dp circle)
                            VideoOverlayTextButton(
                                onClick = { showSpeedDialog = true },
                                text = "${uiState.playbackSpeed}x",
                                contentDescription = stringResource(R.string.playback_speed),
                                isActive = uiState.playbackSpeed != 1.0f,
                                testTag = "video_speed_button"
                            )
                        }
                    }
                }
            }
        }
    }

    // Audio Track Selection Dialog
    if (showAudioDialog) {
        AudioTrackDialog(
            audioTracks = uiState.audioTracks,
            onSelectTrack = { viewModel.selectAudioTrack(it) },
            onDismiss = { showAudioDialog = false }
        )
    }

    // Subtitle Track Selection Dialog
    if (showSubtitleDialog) {
        SubtitleTrackDialog(
            tracks = uiState.subtitleTracks,
            selectedId = uiState.selectedSubtitleId,
            onSelectTrack = { viewModel.selectSubtitleTrack(it) },
            onAddExternalSubtitle = { uri, name -> viewModel.addExternalSubtitle(uri, name) },
            onOpenSettings = {
                showSubtitleDialog = false
                showSubtitleSettingsDialog = true
            },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    // Subtitle Customization Settings Dialog
    if (showSubtitleSettingsDialog) {
        SubtitleSettingsDialog(
            settings = uiState.subtitleSettings,
            onSave = { viewModel.updateSubtitleSettings(it) },
            onDismiss = { showSubtitleSettingsDialog = false }
        )
    }

    // Playback Speed Dialog
    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

// =========================================================================
// ISOLATED VIDEO SEEK BAR COMPOSABLE
// =========================================================================

/**
 * Isolated Seek Bar composable reading player position independently so that
 * high-frequency position ticks do not trigger recomposition of the rest of the overlay.
 */
@Composable
fun VideoSeekBar(
    viewModel: VideoPlayerViewModel,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val livePositionMs by viewModel.currentPositionMs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val durationMs = uiState.durationMs.coerceAtLeast(1L)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val activeFraction = if (isDragging) {
        dragFraction
    } else {
        (livePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    val displayTimeMs = if (isDragging) {
        (dragFraction * durationMs).toLong()
    } else {
        livePositionMs
    }

    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 20.dp else 14.dp,
        animationSpec = tween(durationMillis = 150),
        label = "seek_thumb_size"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Time Label (Tabular figures to prevent digit jitter)
            Text(
                text = TimeUtils.formatDuration(displayTimeMs),
                color = VideoOverlayTokens.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum")
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Seek Bar Slider Track + Thumb (Taller 48dp invisible touch box)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .pointerInput(durationMs) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                onDragStateChanged(true)
                                dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                val targetMs = (dragFraction * durationMs).toLong()
                                viewModel.seekTo(targetMs)
                                isDragging = false
                                onDragStateChanged(false)
                            },
                            onDragCancel = {
                                isDragging = false
                                onDragStateChanged(false)
                            },
                            onHorizontalDrag = { change, _ ->
                                change.consume()
                                dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                            }
                        )
                    }
                    .pointerInput(durationMs) {
                        detectTapGestures(
                            onTap = { offset ->
                                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                val targetMs = (frac * durationMs).toLong()
                                viewModel.seekTo(targetMs)
                            }
                        )
                    }
                    .testTag("video_seek_slider"),
                contentAlignment = Alignment.CenterStart
            ) {
                val totalWidth = maxWidth
                val thumbOffset = totalWidth * activeFraction

                // 4dp Thin Background Track (White ~30% alpha)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(VideoOverlayTokens.TrackBackground)
                )

                // 4dp Progress Fill Track (Accent color #8E86FF, crisp edges)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(activeFraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(VideoOverlayTokens.Accent)
                )

                // White Thumb (14dp resting, grows to 20dp when dragging, no glow)
                Box(
                    modifier = Modifier
                        .offset(x = (thumbOffset - (thumbSize / 2)).coerceIn(0.dp, totalWidth - thumbSize))
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(VideoOverlayTokens.ThumbNormal)
                )

                // Floating Time Bubble above thumb while dragging
                if (isDragging) {
                    val bubbleOffset = (thumbOffset - 24.dp).coerceIn(0.dp, totalWidth - 48.dp)
                    Box(
                        modifier = Modifier
                            .offset(x = bubbleOffset, y = (-26).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = TimeUtils.formatDuration((dragFraction * durationMs).toLong()),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = TextStyle(fontFeatureSettings = "tnum")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right Duration Label
            Text(
                text = TimeUtils.formatDuration(durationMs),
                color = VideoOverlayTokens.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(fontFeatureSettings = "tnum")
            )
        }
    }
}

// =========================================================================
// REUSABLE VIDEO OVERLAY BUTTON COMPONENTS
// =========================================================================

/**
 * Standard overlay icon button: flat translucent black circle (40dp visual, 48dp touch target),
 * white icon (20dp), pressed state raises alpha to ~65% with 150ms animation, no outer shadow/glow.
 * When active: icon turns accent color and a 4dp accent dot appears below it.
 */
@Composable
fun VideoOverlayIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.65f else 0.45f,
        animationSpec = tween(durationMillis = 150),
        label = "overlay_icon_btn_alpha"
    )

    Box(
        modifier = modifier
            .size(48.dp) // Minimum 48dp accessibility touch target
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = baseAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = if (isActive) VideoOverlayTokens.Accent else VideoOverlayTokens.IconNormal,
                    modifier = Modifier.size(iconSize)
                )
                if (isActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(VideoOverlayTokens.Accent)
                    )
                }
            }
        }
    }
}

/**
 * Standard overlay text button (e.g. Speed "1.0x"): flat translucent black circle (40dp visual,
 * 48dp touch target), 12sp semi-bold text, pressed state raises alpha to ~65%, no outer shadow/glow.
 * When active (speed != 1.0x): text turns accent color and a 4dp accent dot appears below it.
 */
@Composable
fun VideoOverlayTextButton(
    onClick: () -> Unit,
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    size: Dp = 40.dp,
    testTag: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.65f else 0.45f,
        animationSpec = tween(durationMillis = 150),
        label = "overlay_text_btn_alpha"
    )

    Box(
        modifier = modifier
            .size(48.dp) // Minimum 48dp accessibility touch target
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = baseAlpha)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = text,
                    color = if (isActive) VideoOverlayTokens.Accent else VideoOverlayTokens.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isActive) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(VideoOverlayTokens.Accent)
                    )
                }
            }
        }
    }
}

/**
 * Center Play/Pause button: 64dp translucent dark circle, 32dp white icon, smooth crossfade, no glow.
 */
@Composable
fun VideoOverlayPlayPauseButton(
    isPlaying: Boolean,
    isAnimationDisabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.65f else 0.45f,
        animationSpec = tween(durationMillis = 150),
        label = "overlay_center_btn_alpha"
    )

    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = baseAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isAnimationDisabled) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Jeda" else "Putar",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        } else {
            Crossfade(
                targetState = isPlaying,
                animationSpec = tween(durationMillis = 150),
                label = "play_pause_crossfade"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Jeda" else "Putar",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(name = "Video Overlay - Dark Video Background (Portrait)", widthDp = 360, heightDp = 640)
@Composable
fun VideoOverlayDarkPortraitPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14161B))
    ) {
        VideoOverlayPreviewContent(isLandscape = false)
    }
}

@Preview(name = "Video Overlay - Bright Video Background (Portrait)", widthDp = 360, heightDp = 640)
@Composable
fun VideoOverlayBrightPortraitPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8EEF5))
    ) {
        VideoOverlayPreviewContent(isLandscape = false)
    }
}

@Preview(name = "Video Overlay - Dark Video Background (Landscape)", widthDp = 640, heightDp = 360)
@Composable
fun VideoOverlayDarkLandscapePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14161B))
    ) {
        VideoOverlayPreviewContent(isLandscape = true)
    }
}

@Preview(name = "Video Overlay - Bright Video Background (Landscape)", widthDp = 640, heightDp = 360)
@Composable
fun VideoOverlayBrightLandscapePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8EEF5))
    ) {
        VideoOverlayPreviewContent(isLandscape = true)
    }
}

@Composable
private fun VideoOverlayPreviewContent(isLandscape: Boolean) {
    val sidePadding = if (isLandscape) 24.dp else 16.dp
    Box(modifier = Modifier.fillMaxSize()) {
        // Top Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(VideoOverlayTokens.ScrimTop, Color.Transparent)
                    )
                )
        )

        // Bottom Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, VideoOverlayTokens.ScrimBottom)
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoOverlayIconButton(
                    onClick = {},
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VID_20260824_Sample_Cinematic_Video.mp4",
                    color = VideoOverlayTokens.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoOverlayIconButton(
                        onClick = {},
                        icon = Icons.Default.AspectRatio,
                        contentDescription = "Aspect Ratio"
                    )
                    VideoOverlayIconButton(
                        onClick = {},
                        icon = Icons.Default.Audiotrack,
                        contentDescription = "Audio Track"
                    )
                    VideoOverlayIconButton(
                        onClick = {},
                        icon = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        isActive = true
                    )
                    VideoOverlayIconButton(
                        onClick = {},
                        icon = Icons.Default.PictureInPictureAlt,
                        contentDescription = "PiP"
                    )
                }
            }

            // Center Play/Pause
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                VideoOverlayPlayPauseButton(
                    isPlaying = false,
                    isAnimationDisabled = false,
                    onClick = {}
                )
            }

            // Bottom Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding, vertical = 16.dp)
            ) {
                // Mock Seek Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "01:24",
                        color = VideoOverlayTokens.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(VideoOverlayTokens.TrackBackground)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(VideoOverlayTokens.Accent)
                        )
                        Box(
                            modifier = Modifier
                                .offset(x = 100.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(VideoOverlayTokens.ThumbNormal)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "04:12",
                        color = VideoOverlayTokens.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoOverlayIconButton(
                        onClick = {},
                        icon = Icons.Default.ScreenRotation,
                        contentDescription = "Kunci Orientasi",
                        isActive = false
                    )
                    VideoOverlayIconButton(
                        onClick = {},
                        icon = Icons.Default.Loop,
                        contentDescription = "Loop",
                        isActive = true
                    )
                    VideoOverlayTextButton(
                        onClick = {},
                        text = "1.0x",
                        contentDescription = "Kecepatan Putar",
                        isActive = false
                    )
                }
            }
        }
    }
}
