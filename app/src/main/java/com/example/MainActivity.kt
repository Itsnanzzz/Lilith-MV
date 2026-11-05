package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ThemeMode
import com.example.data.AppAccent
import com.example.ui.common.PermissionView
import com.example.ui.music.EqualizerDialog
import com.example.ui.music.MiniPlayer
import com.example.ui.music.MusicScreen
import com.example.ui.music.MusicViewModel
import com.example.ui.music.NowPlayingSheet
import com.example.ui.navigation.UnifiedDockedBottomBar
import com.example.ui.music.PlaybackSpeedDialog
import com.example.ui.music.QueueSheet
import com.example.ui.music.SleepTimerDialog
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.LilithTheme
import com.example.ui.theme.LocalNeumorphicColors
import com.example.ui.theme.NeumorphicFloatingBottomBar
import com.example.ui.video.VideoLibraryScreen
import com.example.ui.video.VideoLibraryViewModel
import com.example.ui.video.VideoPlayerScreen
import com.example.ui.video.VideoPlayerViewModel

enum class MainNavigationTab {
    MUSIC, VIDEO, SETTINGS
}

class MainActivity : AppCompatActivity() {

    private val musicViewModel: MusicViewModel by viewModels()
    private val videoLibraryViewModel: VideoLibraryViewModel by viewModels()
    private val videoPlayerViewModel: VideoPlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val appAccent by settingsViewModel.appAccent.collectAsState(initial = AppAccent.WHITE)
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            LilithTheme(darkTheme = isDark, appAccent = appAccent) {
                MainAppContainer(
                    musicViewModel = musicViewModel,
                    videoLibraryViewModel = videoLibraryViewModel,
                    videoPlayerViewModel = videoPlayerViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
fun MainAppContainer(
    musicViewModel: MusicViewModel,
    videoLibraryViewModel: VideoLibraryViewModel,
    videoPlayerViewModel: VideoPlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    val nc = LocalNeumorphicColors.current
    val context = LocalContext.current

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
    }

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        permissionsGranted = granted
        if (granted) {
            musicViewModel.scanMediaLibrary()
            videoLibraryViewModel.scanVideos()
        }
    }

    if (!permissionsGranted) {
        PermissionView(
            onRequestPermission = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(MainNavigationTab.MUSIC) }

    val musicUiState by musicViewModel.uiState.collectAsState()
    val videoLibUiState by videoLibraryViewModel.uiState.collectAsState()

    val currentPlayingTrack by musicViewModel.playerManager.currentTrack.collectAsState()
    val isMusicPlaying by musicViewModel.playerManager.isPlaying.collectAsState()
    val musicPositionMs by musicViewModel.playerManager.currentPositionMs.collectAsState()
    val musicDurationMs by musicViewModel.playerManager.durationMs.collectAsState()
    val queue by musicViewModel.playerManager.queue.collectAsState()
    val repeatMode by musicViewModel.playerManager.repeatMode.collectAsState()
    val shuffleEnabled by musicViewModel.playerManager.shuffleEnabled.collectAsState()
    val playbackSpeed by musicViewModel.playerManager.playbackSpeed.collectAsState()
    val formatInfo by musicViewModel.playerManager.formatInfo.collectAsState()
    val sleepTimerSec by musicViewModel.playerManager.sleepTimerRemainingSeconds.collectAsState()

    val equalizerState by musicViewModel.equalizerState.collectAsState()

    // If video starts playing, pause music playback to avoid mixed audio
    LaunchedEffect(videoLibUiState.activePlayingVideo) {
        if (videoLibUiState.activePlayingVideo != null && isMusicPlaying) {
            musicViewModel.playerManager.togglePlayPause()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(nc.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = nc.background,
            bottomBar = {
                val showMiniPlayerTrack = if (!musicUiState.isNowPlayingExpanded) currentPlayingTrack else null
                UnifiedDockedBottomBar(
                    selectedTab = currentTab,
                    onTabSelected = { currentTab = it },
                    currentTrack = showMiniPlayerTrack,
                    isPlaying = isMusicPlaying,
                    currentPositionMs = musicPositionMs,
                    durationMs = musicDurationMs,
                    formatInfo = formatInfo,
                    onTogglePlayPause = { musicViewModel.playerManager.togglePlayPause() },
                    onSkipNext = { musicViewModel.playerManager.skipToNext() },
                    onClickMiniPlayer = { musicViewModel.setNowPlayingExpanded(true) }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    MainNavigationTab.MUSIC -> {
                        MusicScreen(viewModel = musicViewModel)
                    }

                    MainNavigationTab.VIDEO -> {
                        VideoLibraryScreen(
                            viewModel = videoLibraryViewModel,
                            onVideoSelected = { video ->
                                videoLibraryViewModel.playVideo(video)
                            }
                        )
                    }

                    MainNavigationTab.SETTINGS -> {
                        SettingsScreen(viewModel = settingsViewModel)
                    }
                }
            }
        }

        // Full Screen Video Player Overlay
        videoLibUiState.activePlayingVideo?.let { activeVideo ->
            VideoPlayerScreen(
                video = activeVideo,
                viewModel = videoPlayerViewModel,
                onBack = {
                    videoPlayerViewModel.releasePlayer()
                    videoLibraryViewModel.closePlayer()
                }
            )
        }

        // Expanded Now Playing Sheet
        AnimatedVisibility(
            visible = musicUiState.isNowPlayingExpanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            NowPlayingSheet(
                track = currentPlayingTrack,
                isPlaying = isMusicPlaying,
                currentPositionMs = musicPositionMs,
                durationMs = musicDurationMs,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                playbackSpeed = playbackSpeed,
                formatInfo = formatInfo,
                sleepTimerRemainingSec = sleepTimerSec,
                onCollapse = { musicViewModel.setNowPlayingExpanded(false) },
                onTogglePlayPause = { musicViewModel.playerManager.togglePlayPause() },
                onSkipNext = { musicViewModel.playerManager.skipToNext() },
                onSkipPrevious = { musicViewModel.playerManager.skipToPrevious() },
                onSeek = { musicViewModel.playerManager.seekTo(it) },
                onToggleRepeat = { musicViewModel.playerManager.toggleRepeatMode() },
                onToggleShuffle = { musicViewModel.playerManager.toggleShuffle() },
                onOpenSpeedDialog = { musicViewModel.setSpeedDialogVisible(true) },
                onOpenSleepTimerDialog = { musicViewModel.setSleepTimerDialogVisible(true) },
                onOpenEqualizerDialog = { musicViewModel.setEqualizerDialogVisible(true) },
                onOpenQueueSheet = { musicViewModel.setQueueSheetVisible(true) }
            )
        }

        // Queue Modal Bottom Sheet
        if (musicUiState.isQueueSheetVisible) {
            QueueSheet(
                queue = queue,
                currentTrack = currentPlayingTrack,
                onTrackClick = { index ->
                    musicViewModel.playerManager.playTrackList(queue, index)
                },
                onRemove = { index ->
                    musicViewModel.playerManager.removeFromQueue(index)
                },
                onMoveUp = { index ->
                    musicViewModel.playerManager.moveQueueItem(index, index - 1)
                },
                onMoveDown = { index ->
                    musicViewModel.playerManager.moveQueueItem(index, index + 1)
                },
                onClearQueue = {
                    musicViewModel.playerManager.clearQueue()
                },
                onDismiss = {
                    musicViewModel.setQueueSheetVisible(false)
                }
            )
        }

        // Equalizer Dialog
        if (musicUiState.isEqualizerDialogVisible) {
            EqualizerDialog(
                state = equalizerState,
                onToggleEnabled = { musicViewModel.setEqualizerEnabled(it) },
                onSelectPreset = { musicViewModel.setEqualizerPreset(it) },
                onSetBassBoost = { musicViewModel.setBassBoost(it) },
                onDismiss = { musicViewModel.setEqualizerDialogVisible(false) }
            )
        }

        // Music Playback Speed Dialog
        if (musicUiState.isSpeedDialogVisible) {
            PlaybackSpeedDialog(
                currentSpeed = playbackSpeed,
                onSelectSpeed = { musicViewModel.playerManager.setPlaybackSpeed(it) },
                onDismiss = { musicViewModel.setSpeedDialogVisible(false) }
            )
        }

        // Sleep Timer Dialog
        if (musicUiState.isSleepTimerDialogVisible) {
            SleepTimerDialog(
                remainingSeconds = sleepTimerSec,
                onSetTimerMinutes = { musicViewModel.playerManager.setSleepTimer(it) },
                onDismiss = { musicViewModel.setSleepTimerDialogVisible(false) }
            )
        }
    }
}
