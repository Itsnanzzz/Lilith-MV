package com.example.ui.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.BrightnessMedium
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.example.ui.theme.AppFontFamily
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTypography
import com.example.data.model.AspectRatioMode
import com.example.data.model.VideoItem
import com.example.ui.common.TimeUtils
import com.example.ui.music.PlaybackSpeedDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private val TextWhitePrimary = Color.White
private val TextWhiteSecondary = Color.White.copy(alpha = 0.70f)
private val ScrimColor = Color.Black.copy(alpha = 0.35f)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: VideoItem,
    viewModel: VideoPlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeTokens.colors
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val sidePadding = if (isLandscape) 24.dp else 20.dp

    val initialRequestedOrientation = remember {
        activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    var userForcedOrientation by rememberSaveable { mutableStateOf<Int?>(null) }
    var lastRotationSwitchTimeMs by remember { mutableLongStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSubtitleSettingsDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    var floatingModeMessage by remember { mutableStateOf<String?>(null) }
    var isDraggingSeekBar by remember { mutableStateOf(false) }

    // Touch Lock: floating padlock button visibility (auto-hides after ~3s)
    var showUnlockButton by remember { mutableStateOf(false) }

    // Vertical gesture HUD state
    var isSwipingVolume by remember { mutableStateOf(false) }
    var isSwipingBrightness by remember { mutableStateOf(false) }
    var hudVolumeFraction by remember { mutableFloatStateOf(0f) }
    var hudBrightnessFraction by remember { mutableFloatStateOf(0.5f) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var showBrightnessHud by remember { mutableStateOf(false) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    val isAnimationDisabled = remember(context) {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }

    // Cancel auto-hide when any interactive overlay or dialog is active
    val isInteractiveActive = showAudioDialog ||
            showSubtitleDialog ||
            showSubtitleSettingsDialog ||
            showSpeedDialog ||
            showOverflowMenu ||
            isDraggingSeekBar ||
            isSwipingVolume ||
            isSwipingBrightness

    LaunchedEffect(isInteractiveActive, uiState.controlsVisible) {
        if (isInteractiveActive) {
            viewModel.cancelControlsAutoHide()
        } else if (uiState.controlsVisible && !uiState.isControlsLocked) {
            viewModel.scheduleControlsAutoHide(3000L)
        }
    }

    // Auto-hide floating aspect ratio badge after ~1.2s
    LaunchedEffect(floatingModeMessage) {
        if (floatingModeMessage != null) {
            delay(1200)
            floatingModeMessage = null
        }
    }

    // Auto-hide floating unlock button after ~3s
    LaunchedEffect(showUnlockButton) {
        if (showUnlockButton) {
            delay(3000)
            showUnlockButton = false
        }
    }

    // Window setup: keep screen on, edge-to-edge display cutout, system bar control
    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Display cutout short edges
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && window != null) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        // Capture initial system brightness to restore cleanly
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                val lp = window.attributes
                lp.screenBrightness = originalBrightness
                window.attributes = lp
            }
            activity?.requestedOrientation = initialRequestedOrientation
        }
    }

    // Clean mode system bars: hide status & nav bars when controls are hidden or touch is locked
    LaunchedEffect(uiState.controlsVisible, uiState.isControlsLocked) {
        val window = activity?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!uiState.controlsVisible || uiState.isControlsLocked) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    // Load video into ViewModel
    LaunchedEffect(video.uri) {
        viewModel.loadVideo(video)
    }

    // Restore forced orientation if returning from process/activity recreation
    LaunchedEffect(userForcedOrientation) {
        userForcedOrientation?.let { forced ->
            if (activity != null && activity.requestedOrientation != forced) {
                activity.requestedOrientation = forced
            }
        }
    }

    // Intercept back press when controls are locked
    BackHandler {
        if (uiState.isControlsLocked) {
            Toast.makeText(context, context.getString(R.string.screen_locked), Toast.LENGTH_SHORT).show()
            showUnlockButton = true
        } else {
            onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_player_screen")
    ) {
        // 1. ExoPlayer Surface View
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

        // 2. Audio-Only Mode Dark Card Overlay
        if (uiState.isAudioOnly) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF111216)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Headphones,
                        contentDescription = stringResource(R.string.audio_only),
                        tint = colors.accent,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = video.title,
                        color = TextWhitePrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.audio_only_on),
                        color = TextWhiteSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // 3. Gesture Handling Layer (Tap, Double Tap, Vertical Swipe outside 24dp screen edges)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.isControlsLocked) {
                    if (uiState.isControlsLocked) {
                        detectTapGestures(
                            onTap = {
                                showUnlockButton = true
                            }
                        )
                    } else {
                        detectTapGestures(
                            onTap = {
                                viewModel.toggleControlsVisibility()
                            },
                            onDoubleTap = { offset ->
                                val width = size.width
                                if (offset.x < width / 2) {
                                    viewModel.doubleTapSeek(-VideoPlayerViewModel.SEEK_DELTA_SECONDS)
                                } else {
                                    viewModel.doubleTapSeek(VideoPlayerViewModel.SEEK_DELTA_SECONDS)
                                }
                            }
                        )
                    }
                }
                .pointerInput(uiState.isControlsLocked) {
                    if (!uiState.isControlsLocked) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val width = size.width
                                // Ignore swipes in the outer 24dp screen edges
                                if (offset.x < 24.dp.toPx() || offset.x > width - 24.dp.toPx()) {
                                    return@detectVerticalDragGestures
                                }
                                if (offset.x < width / 2) {
                                    isSwipingBrightness = true
                                    showBrightnessHud = true
                                    val curLp = activity?.window?.attributes?.screenBrightness ?: -1f
                                    hudBrightnessFraction = if (curLp in 0f..1f) {
                                        curLp
                                    } else {
                                        try {
                                            Settings.System.getInt(
                                                context.contentResolver,
                                                Settings.System.SCREEN_BRIGHTNESS
                                            ) / 255f
                                        } catch (_: Exception) {
                                            0.5f
                                        }
                                    }
                                } else {
                                    isSwipingVolume = true
                                    showVolumeHud = true
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    hudVolumeFraction = curVol.toFloat() / maxVol
                                }
                            },
                            onDragEnd = {
                                isSwipingBrightness = false
                                isSwipingVolume = false
                            },
                            onDragCancel = {
                                isSwipingBrightness = false
                                isSwipingVolume = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val width = size.width
                                val normalizedDelta = -dragAmount / size.height.toFloat()

                                if (change.position.x < width / 2) {
                                    // Brightness on left half
                                    val newB = (hudBrightnessFraction + normalizedDelta).coerceIn(0.02f, 1.0f)
                                    hudBrightnessFraction = newB
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        lp.screenBrightness = newB
                                        act.window.attributes = lp
                                    }
                                    viewModel.setBrightnessLevel(newB)
                                } else {
                                    // Volume on right half
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val newFrac = (hudVolumeFraction + normalizedDelta).coerceIn(0f, 1f)
                                    hudVolumeFraction = newFrac
                                    val newVol = (newFrac * maxVol).toInt().coerceIn(0, maxVol)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                    viewModel.adjustVolume(0f)
                                }
                            }
                        )
                    }
                }
        )

        // Dismiss HUD bars ~800ms after vertical swipe release
        LaunchedEffect(isSwipingBrightness) {
            if (!isSwipingBrightness) {
                delay(800)
                showBrightnessHud = false
            }
        }
        LaunchedEffect(isSwipingVolume) {
            if (!isSwipingVolume) {
                delay(800)
                showVolumeHud = false
            }
        }

        // 4. Vertical Gesture Indicators (Left: Brightness, Right: Volume)
        AnimatedVisibility(
            visible = showBrightnessHud,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
        ) {
            VerticalGestureIndicator(
                icon = Icons.Outlined.BrightnessMedium,
                contentDescription = stringResource(R.string.brightness_level),
                fraction = hudBrightnessFraction,
                percentageText = "${(hudBrightnessFraction * 100).toInt()}%"
            )
        }

        AnimatedVisibility(
            visible = showVolumeHud,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(250)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 32.dp)
        ) {
            VerticalGestureIndicator(
                icon = if (hudVolumeFraction <= 0.01f) Icons.Default.VolumeOff else Icons.Outlined.VolumeUp,
                contentDescription = stringResource(R.string.volume_level),
                fraction = hudVolumeFraction,
                percentageText = "${(hudVolumeFraction * 100).toInt()}%"
            )
        }

        // 5. Floating Aspect Ratio Feedback Pill (auto-hides after ~1.2s)
        AnimatedVisibility(
            visible = floatingModeMessage != null,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = floatingModeMessage.orEmpty(),
                    color = TextWhitePrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 6. Floating Unlock Padlock Button (When controls are locked, tap reveals it for ~3s)
        AnimatedVisibility(
            visible = uiState.isControlsLocked && showUnlockButton,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        role = Role.Button,
                        onClick = {
                            viewModel.toggleTouchLock()
                            showUnlockButton = false
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = stringResource(R.string.touch_unlock),
                    tint = TextWhitePrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 7. Resume Playback Prompt Banner
        if (uiState.resumePositionMs != null) {
            val resumeFormatted = TimeUtils.formatVideoDuration(uiState.resumePositionMs!!)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .testTag("resume_video_card")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.video_resume_prompt, resumeFormatted),
                        color = TextWhitePrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.resumeFromSavedPosition() },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("resume_button")
                    ) {
                        Text(stringResource(R.string.video_resume_action), fontSize = 12.sp, color = colors.onAccent)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = { viewModel.dismissResumePrompt() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(stringResource(R.string.video_start_over), color = TextWhiteSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        // 8. Unsupported Codec Error Banner
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

        // 9. COMPLETE CONTROLS OVERLAY (Clean Mode toggles this entire overlay)
        val animSpec = if (isAnimationDisabled) snap() else tween<Float>(durationMillis = 200)

        AnimatedVisibility(
            visible = uiState.controlsVisible && !uiState.isControlsLocked,
            enter = fadeIn(animSpec),
            exit = fadeOut(animSpec)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Scrim: Subtle black ~35% to transparent
                val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(96.dp + statusBarInset)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(ScrimColor, Color.Transparent)
                            )
                        )
                )

                // Bottom Scrim: Subtle transparent to black ~35%
                val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp + navBarInset)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, ScrimColor)
                            )
                        )
                )

                // Layout Structure: Top Bar, Center Play/Pause, Bottom Stack
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // --- TOP BAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = sidePadding, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chevron-down Back Button
                        MinimalIconButton(
                            onClick = onBack,
                            icon = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.back),
                            testTag = "video_back_button"
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Video Title with basicMarquee
                        Text(
                            text = video.title,
                            color = TextWhitePrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
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

                        // "CC" Subtitle Icon Button
                        MinimalIconButton(
                            onClick = { showSubtitleDialog = true },
                            icon = Icons.Outlined.ClosedCaption,
                            contentDescription = stringResource(R.string.subtitle_title),
                            isActive = uiState.selectedSubtitleId != null,
                            testTag = "subtitle_track_button"
                        )

                        // Three-Dot Overflow Menu
                        Box {
                            MinimalIconButton(
                                onClick = { showOverflowMenu = true },
                                icon = Icons.Outlined.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                                testTag = "video_overflow_menu_button"
                            )

                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier.background(Color(0xFF1E2026))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.loop),
                                                color = TextWhitePrimary,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (uiState.isLooping) stringResource(R.string.loop_on) else stringResource(R.string.loop_off),
                                                color = if (uiState.isLooping) colors.accent else TextWhiteSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    },
                                    onClick = {
                                        viewModel.toggleLooping()
                                        showOverflowMenu = false
                                    }
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.audio_tracks),
                                            color = TextWhitePrimary,
                                            fontSize = 14.sp
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showAudioDialog = true
                                    }
                                )
                                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.subtitle_settings),
                                            color = TextWhitePrimary,
                                            fontSize = 14.sp
                                        )
                                    },
                                    onClick = {
                                        showOverflowMenu = false
                                        showSubtitleSettingsDialog = true
                                    }
                                )
                            }
                        }
                    }

                    val isSeekingEnabled = uiState.durationMs > 0L &&
                            !isDraggingSeekBar &&
                            !isSwipingVolume &&
                            !isSwipingBrightness &&
                            !showAudioDialog &&
                            !showSubtitleDialog &&
                            !showSubtitleSettingsDialog &&
                            !showSpeedDialog &&
                            !showOverflowMenu

                    // --- CENTER CONTROLS: [Rewind 10] [Play/Pause] [Forward 10] ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rewind 10s button
                        SeekStepButton(
                            iconRes = R.drawable.ic_replay_10,
                            contentDescription = stringResource(R.string.rewind_10_seconds),
                            enabled = isSeekingEnabled,
                            rotationDirection = -1f,
                            isAnimationDisabled = isAnimationDisabled,
                            onClick = {
                                viewModel.seekRelative(-VideoPlayerViewModel.SEEK_DELTA_MS)
                                viewModel.scheduleControlsAutoHide(3000L)
                            },
                            testTag = "video_rewind_10_button"
                        )

                        Spacer(modifier = Modifier.width(32.dp))

                        // Center Play / Pause Button (No circle, plain white glyph, stays at exact center)
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        viewModel.togglePlayPause()
                                    }
                                )
                                .testTag("video_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isAnimationDisabled) {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (uiState.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                    tint = Color.White,
                                    modifier = Modifier.size(56.dp)
                                )
                            } else {
                                Crossfade(
                                    targetState = uiState.isPlaying,
                                    animationSpec = tween(durationMillis = 150),
                                    label = "center_play_pause_crossfade"
                                ) { playing ->
                                    Icon(
                                        imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (playing) stringResource(R.string.pause) else stringResource(R.string.play),
                                        tint = Color.White,
                                        modifier = Modifier.size(56.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        // Forward 10s button
                        SeekStepButton(
                            iconRes = R.drawable.ic_forward_10,
                            contentDescription = stringResource(R.string.forward_10_seconds),
                            enabled = isSeekingEnabled,
                            rotationDirection = 1f,
                            isAnimationDisabled = isAnimationDisabled,
                            onClick = {
                                viewModel.seekRelative(VideoPlayerViewModel.SEEK_DELTA_MS)
                                viewModel.scheduleControlsAutoHide(3000L)
                            },
                            testTag = "video_forward_10_button"
                        )
                    }

                    // --- BOTTOM STACK (Action Row, Seek Bar, Time Row) ---
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(start = sidePadding, end = sidePadding, top = 6.dp, bottom = 18.dp)
                    ) {
                        // 1. Action Icon Row (Exactly 7 outlined icons evenly spaced)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Lock (Touch Lock)
                            MinimalIconButton(
                                onClick = {
                                    viewModel.toggleTouchLock()
                                    Toast.makeText(context, context.getString(R.string.screen_locked), Toast.LENGTH_SHORT).show()
                                },
                                icon = Icons.Outlined.Lock,
                                contentDescription = stringResource(R.string.touch_lock),
                                testTag = "touch_lock_button"
                            )

                            // 2. Volume (Mute Toggle)
                            MinimalIconButton(
                                onClick = { viewModel.toggleMute() },
                                icon = if (uiState.isMuted) Icons.Default.VolumeOff else Icons.Outlined.VolumeUp,
                                contentDescription = if (uiState.isMuted) stringResource(R.string.unmute) else stringResource(R.string.mute),
                                isActive = uiState.isMuted,
                                testTag = "mute_toggle_button"
                            )

                            // 3. PiP
                            MinimalIconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        activity.enterPictureInPictureMode(params)
                                    }
                                },
                                icon = Icons.Outlined.PictureInPictureAlt,
                                contentDescription = stringResource(R.string.pip_mode),
                                testTag = "pip_mode_button"
                            )

                            // 4. Aspect Ratio (< >)
                            MinimalIconButton(
                                onClick = {
                                    viewModel.cycleAspectRatio()
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
                                icon = Icons.Outlined.AspectRatio,
                                contentDescription = stringResource(R.string.aspect_ratio_fit),
                                testTag = "aspect_ratio_button"
                            )

                            // 5. Speed (Small outlined box with text "1X", "1.5X")
                            SpeedBadgeButton(
                                speed = uiState.playbackSpeed,
                                onClick = { showSpeedDialog = true },
                                testTag = "video_speed_button"
                            )

                            // 6. Headphones (Audio-Only Mode)
                            MinimalIconButton(
                                onClick = { viewModel.toggleAudioOnly() },
                                icon = Icons.Outlined.Headphones,
                                contentDescription = stringResource(R.string.audio_only),
                                isActive = uiState.isAudioOnly,
                                testTag = "audio_only_button"
                            )

                            // 7. Manual Screen Rotation Switch (Portrait <-> Landscape)
                            val rotationContentDesc = if (isLandscape) {
                                stringResource(R.string.rotate_to_portrait)
                            } else {
                                stringResource(R.string.rotate_to_landscape)
                            }

                            val iconRotationAngle by animateFloatAsState(
                                targetValue = if (isLandscape) 90f else 0f,
                                animationSpec = if (isAnimationDisabled) snap() else tween(durationMillis = 200),
                                label = "rotation_icon_angle"
                            )

                            MinimalIconButton(
                                onClick = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastRotationSwitchTimeMs < 400L) {
                                        return@MinimalIconButton
                                    }
                                    lastRotationSwitchTimeMs = now

                                    val currentOrientation = context.resources.configuration.orientation
                                    val targetOrientation = if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    } else {
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    }
                                    userForcedOrientation = targetOrientation
                                    activity?.requestedOrientation = targetOrientation
                                    viewModel.scheduleControlsAutoHide(3000L)

                                    coroutineScope.launch {
                                        delay(550L)
                                        val newOrientation = context.resources.configuration.orientation
                                        val isTargetLandscape = (targetOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                        val isActualLandscape = (newOrientation == Configuration.ORIENTATION_LANDSCAPE)
                                        if (isTargetLandscape != isActualLandscape) {
                                            Toast.makeText(context, context.getString(R.string.rotation_not_supported), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                icon = Icons.Outlined.ScreenRotation,
                                iconModifier = Modifier.rotate(iconRotationAngle),
                                contentDescription = rotationContentDesc,
                                isActive = false,
                                testTag = "screen_rotation_button"
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 2. Seek Bar (Solid white fill, thumb visible ONLY while dragging)
                        VideoMinimalSeekBar(
                            viewModel = viewModel,
                            onDragStateChanged = { dragging ->
                                isDraggingSeekBar = dragging
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 3. Time Labels Row (00:01 and 00:16 tabular figures)
                        VideoTimeLabelsRow(
                            viewModel = viewModel,
                            durationMs = uiState.durationMs,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showAudioDialog) {
        AudioTrackDialog(
            audioTracks = uiState.audioTracks,
            onSelectTrack = { viewModel.selectAudioTrack(it) },
            onDismiss = { showAudioDialog = false }
        )
    }

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

    if (showSubtitleSettingsDialog) {
        SubtitleSettingsDialog(
            settings = uiState.subtitleSettings,
            onSave = { viewModel.updateSubtitleSettings(it) },
            onDismiss = { showSubtitleSettingsDialog = false }
        )
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

// =========================================================================
// MINIMAL ICON BUTTON (NO CONTAINER, NO CIRCLE, NO GLOW)
// =========================================================================

@Composable
private fun MinimalIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    isActive: Boolean = false,
    iconSize: Dp = 24.dp,
    testTag: String? = null
) {
    val accentColor = AppThemeTokens.colors.accent
    Box(
        modifier = modifier
            .size(48.dp) // Minimum 48dp touch target
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) accentColor else Color.White,
                modifier = Modifier
                    .size(iconSize)
                    .then(iconModifier)
            )
            if (isActive) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }
        }
    }
}

// =========================================================================
// SPEED BADGE BUTTON (OUTLINED BOX DISPLAYING E.G. "1X", "1.5X")
// =========================================================================

@Composable
private fun SpeedBadgeButton(
    speed: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val accentColor = AppThemeTokens.colors.accent
    val speedText = if (speed % 1.0f == 0f) "${speed.toInt()}X" else "${speed}X"
    val isCustom = speed != 1.0f

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = if (isCustom) accentColor else Color.White.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = speedText,
                color = if (isCustom) accentColor else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================================
// MINIMAL SEEK BAR (SOLID WHITE FILL, THUMB VISIBLE ONLY WHILE DRAGGING)
// =========================================================================

@Composable
private fun VideoMinimalSeekBar(
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

    val thumbSize by animateDpAsState(
        targetValue = if (isDragging) 12.dp else 0.dp,
        animationSpec = tween(durationMillis = 120),
        label = "seek_thumb_size"
    )

    BoxWithConstraints(
        modifier = modifier
            .height(48.dp) // 48dp invisible touch box
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

        // 4dp Background Track: White 30%
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.30f))
        )

        val accentColor = AppThemeTokens.colors.accent

        // 4dp Progress Track: Accent Color
        Box(
            modifier = Modifier
                .fillMaxWidth(activeFraction)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accentColor)
        )

        // 12dp Accent Thumb (Visible ONLY while dragging)
        if (thumbSize > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = (thumbOffset - (thumbSize / 2)).coerceIn(0.dp, totalWidth - thumbSize))
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }

        // Floating Time Bubble above thumb while dragging (white text on dark ~60% rounded pill, 12sp tabular figures)
        if (isDragging) {
            val estimatedBubbleWidth = 48.dp
            val bubbleOffset = (thumbOffset - (estimatedBubbleWidth / 2)).coerceIn(0.dp, (totalWidth - estimatedBubbleWidth).coerceAtLeast(0.dp))
            Box(
                modifier = Modifier
                    .offset(x = bubbleOffset, y = (-28).dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = TimeUtils.formatVideoDuration((dragFraction * durationMs).toLong()),
                    color = Color.White,
                    style = AppTypography.timeLabel.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

// =========================================================================
// TIME LABELS ROW (00:01 and 00:16 TABULAR FIGURES)
// =========================================================================

@Composable
private fun VideoTimeLabelsRow(
    viewModel: VideoPlayerViewModel,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val livePositionMs by viewModel.currentPositionMs.collectAsState()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = TimeUtils.formatVideoDuration(livePositionMs),
            color = TextWhitePrimary,
            style = AppTypography.timeLabel
        )
        Text(
            text = TimeUtils.formatVideoDuration(durationMs),
            color = TextWhiteSecondary,
            style = AppTypography.timeLabel
        )
    }
}

// =========================================================================
// SEEK STEP BUTTON (REWIND 10 / FORWARD 10)
// =========================================================================

@Composable
private fun SeekStepButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    rotationDirection: Float,
    isAnimationDisabled: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val rotationAnim = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (!isAnimationDisabled) {
                        coroutineScope.launch {
                            rotationAnim.snapTo(0f)
                            rotationAnim.animateTo(
                                targetValue = rotationDirection * 30f,
                                animationSpec = tween(durationMillis = 75)
                            )
                            rotationAnim.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 75)
                            )
                        }
                    }
                    onClick()
                }
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
            modifier = Modifier
                .size(36.dp)
                .rotate(rotationAnim.value)
        )
    }
}

// =========================================================================
// VERTICAL GESTURE INDICATOR (6dp x 160dp SLIM BAR)
// =========================================================================

@Composable
private fun VerticalGestureIndicator(
    icon: ImageVector,
    contentDescription: String,
    fraction: Float,
    percentageText: String,
    modifier: Modifier = Modifier
) {
    val accentColor = AppThemeTokens.colors.accent
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.70f))
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = accentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        // 6dp x 160dp track
        Box(
            modifier = Modifier
                .width(6.dp)
                .height(160.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = percentageText,
            color = Color.White,
            style = AppTypography.timeLabel.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(name = "Video Overlay - Dark Portrait", widthDp = 360, heightDp = 640)
@Composable
fun VideoOverlayDarkPortraitPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14161B))
    ) {
        VideoOverlayPreviewMock(isLandscape = false)
    }
}

@Preview(name = "Video Overlay - Bright Landscape", widthDp = 640, heightDp = 360)
@Composable
fun VideoOverlayBrightLandscapePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8EEF5))
    ) {
        VideoOverlayPreviewMock(isLandscape = true)
    }
}

@Preview(name = "Video Overlay - Clean Mode (Dark)", widthDp = 360, heightDp = 640)
@Composable
fun VideoOverlayCleanModePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14161B))
    )
}

@Composable
private fun VideoOverlayPreviewMock(isLandscape: Boolean) {
    val sidePadding = if (isLandscape) 24.dp else 16.dp
    Box(modifier = Modifier.fillMaxSize()) {
        // Scrims
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(ScrimColor, Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, ScrimColor)))
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
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "VID_20260824_Sample_Cinematic.mp4",
                    color = TextWhitePrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Outlined.ClosedCaption,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Center Controls: [Rewind 10] [Play] [Forward 10]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_replay_10),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
                Box(
                    modifier = Modifier.size(72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
                Spacer(modifier = Modifier.width(32.dp))
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_forward_10),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Bottom Stack
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sidePadding, vertical = 18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(Icons.Outlined.Lock, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Icon(Icons.Outlined.VolumeUp, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Icon(Icons.Outlined.PictureInPictureAlt, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Icon(Icons.Outlined.AspectRatio, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text("1X", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Outlined.Headphones, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Icon(Icons.Outlined.ScreenRotation, null, tint = Color.White, modifier = Modifier.size(24.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.30f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("00:01", color = TextWhitePrimary, style = AppTypography.timeLabel)
                    Text("00:16", color = TextWhiteSecondary, style = AppTypography.timeLabel)
                }
            }
        }
    }
}
