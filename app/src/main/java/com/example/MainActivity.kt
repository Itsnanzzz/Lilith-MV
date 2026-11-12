package com.example

import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.ForegroundMediaObserver
import com.example.data.MediaRepository
import com.example.data.ThemeMode
import com.example.data.AppAccent
import com.example.ui.common.PermissionView
import com.example.ui.music.AudioEqualizerSheet
import com.example.ui.music.EqualizerDialog
import com.example.ui.music.MiniPlayer
import com.example.ui.music.MusicScreen
import com.example.ui.music.MusicViewModel
import com.example.ui.music.NowPlayingSheet
import com.example.ui.navigation.FloatingBottomBar
import com.example.ui.navigation.LocalBottomContentPadding
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

    companion object {
        private const val KEY_INTENT_HANDLED = "key_intent_handled"
    }

    private val musicViewModel: MusicViewModel by viewModels()
    private val videoLibraryViewModel: VideoLibraryViewModel by viewModels()
    private val videoPlayerViewModel: VideoPlayerViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    private val selectedTabState = mutableStateOf(MainNavigationTab.MUSIC)
    private val isPermissionBypassedState = mutableStateOf(false)
    private var isIntentHandled = false
    private lateinit var foregroundMediaObserver: ForegroundMediaObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        foregroundMediaObserver = ForegroundMediaObserver(
            context = applicationContext,
            repository = MediaRepository(applicationContext),
            getKnownAudioPaths = {
                musicViewModel.uiState.value.allTracks.mapNotNullTo(HashSet()) {
                    it.path.takeIf { p -> p.isNotEmpty() }
                }
            },
            getKnownVideoPaths = {
                videoLibraryViewModel.uiState.value.allVideos.mapNotNullTo(HashSet()) {
                    it.path.takeIf { p -> p.isNotEmpty() }
                }
            },
            onAudioMediaChanged = { musicViewModel.syncMediaLibraryInBackground() },
            onVideoMediaChanged = { videoLibraryViewModel.syncVideosInBackground() }
        )
        lifecycle.addObserver(foregroundMediaObserver)

        if (savedInstanceState != null) {
            isIntentHandled = savedInstanceState.getBoolean(KEY_INTENT_HANDLED, false)
        }

        if (!isIntentHandled) {
            handleViewIntent(intent)
        }

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
                    settingsViewModel = settingsViewModel,
                    selectedTab = selectedTabState.value,
                    onTabSelected = { selectedTabState.value = it },
                    isPermissionBypassed = isPermissionBypassedState.value
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_INTENT_HANDLED, isIntentHandled)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent == null || intent.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        isIntentHandled = true

        val cleanIntent = Intent(intent).apply {
            action = Intent.ACTION_MAIN
            data = null
        }
        setIntent(cleanIntent)

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        var displayName: String? = null
        var size: Long = 0L

        try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
                contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            size = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file" || uri.scheme == null) {
                val file = File(uri.path ?: "")
                if (file.exists()) {
                    displayName = file.name
                    size = file.length()
                } else {
                    throw FileNotFoundException("File not found: ${uri.path}")
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.cant_open_file, Toast.LENGTH_SHORT).show()
            return
        } catch (e: FileNotFoundException) {
            Toast.makeText(this, R.string.cant_open_file, Toast.LENGTH_SHORT).show()
            return
        } catch (e: IllegalArgumentException) {
            Toast.makeText(this, R.string.cant_open_file, Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cant_open_file, Toast.LENGTH_SHORT).show()
            return
        }

        val mediaType = determineMediaType(uri, intent.type, displayName)
        if (mediaType == null) {
            Toast.makeText(this, R.string.unsupported_file, Toast.LENGTH_SHORT).show()
            return
        }

        isPermissionBypassedState.value = true

        when (mediaType) {
            MediaType.AUDIO -> handleAudioView(uri, displayName, size)
            MediaType.VIDEO -> handleVideoView(uri, displayName, size)
        }
    }

    private enum class MediaType {
        AUDIO, VIDEO
    }

    private fun determineMediaType(uri: Uri, intentType: String?, displayName: String?): MediaType? {
        val mimeFromIntent = intentType?.takeIf {
            it.isNotBlank() &&
                    !it.equals("application/octet-stream", ignoreCase = true) &&
                    !it.equals("*/*", ignoreCase = true)
        }

        val typeToTest = mimeFromIntent ?: try {
            contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        if (typeToTest != null &&
            !typeToTest.equals("application/octet-stream", ignoreCase = true) &&
            !typeToTest.equals("*/*", ignoreCase = true)
        ) {
            val lower = typeToTest.lowercase(Locale.ROOT)
            if (lower.startsWith("audio/")) return MediaType.AUDIO
            if (lower.startsWith("video/")) return MediaType.VIDEO
            if (lower == "application/ogg" || lower == "application/x-ogg") return MediaType.AUDIO
        }

        val candidate = displayName ?: uri.lastPathSegment ?: uri.path ?: ""
        val ext = candidate.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotEmpty()) {
            val audioExts = setOf(
                "mp3", "wav", "flac", "m4a", "aac", "ogg", "oga", "opus",
                "wma", "alac", "aiff", "mid", "midi"
            )
            val videoExts = setOf(
                "mp4", "mkv", "webm", "avi", "mov", "3gp", "ts", "m4v",
                "flv", "wmv", "vob", "ogv"
            )
            if (ext in audioExts) return MediaType.AUDIO
            if (ext in videoExts) return MediaType.VIDEO
        }

        return null
    }

    private fun handleAudioView(uri: Uri, displayName: String?, size: Long) {
        lifecycleScope.launch {
            videoPlayerViewModel.releasePlayer()
            videoLibraryViewModel.closePlayer()

            selectedTabState.value = MainNavigationTab.MUSIC

            val matchedTrack = musicViewModel.findMatchingTrack(uri, displayName, size)
            val trackToPlay = matchedTrack ?: musicViewModel.parseTrackFromUri(uri, displayName)

            musicViewModel.playIncomingTrack(trackToPlay)
        }
    }

    private fun handleVideoView(uri: Uri, displayName: String?, size: Long) {
        lifecycleScope.launch {
            if (musicViewModel.playerManager.isPlaying.value) {
                musicViewModel.playerManager.pause()
            }
            musicViewModel.setNowPlayingExpanded(false)

            selectedTabState.value = MainNavigationTab.VIDEO

            val matchedVideo = videoLibraryViewModel.findMatchingVideo(uri, displayName, size)
            val videoToPlay = matchedVideo ?: videoLibraryViewModel.parseVideoFromUri(uri, displayName, size)

            videoLibraryViewModel.playVideo(videoToPlay)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainAppContainer(
    musicViewModel: MusicViewModel,
    videoLibraryViewModel: VideoLibraryViewModel,
    videoPlayerViewModel: VideoPlayerViewModel,
    settingsViewModel: SettingsViewModel,
    selectedTab: MainNavigationTab = MainNavigationTab.MUSIC,
    onTabSelected: (MainNavigationTab) -> Unit = {},
    isPermissionBypassed: Boolean = false
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
            ForegroundMediaObserver.hasAudioReadPermission(context) &&
                ForegroundMediaObserver.hasVideoReadPermission(context)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val hasAudio = ForegroundMediaObserver.hasAudioReadPermission(context)
        val hasVideo = ForegroundMediaObserver.hasVideoReadPermission(context)
        val granted = results.values.all { it } || (hasAudio && hasVideo)
        permissionsGranted = granted
        if (hasAudio) {
            musicViewModel.scanMediaLibrary()
        }
        if (hasVideo) {
            videoLibraryViewModel.scanVideos()
        }
    }

    if (!permissionsGranted && !isPermissionBypassed) {
        PermissionView(
            onRequestPermission = {
                permissionLauncher.launch(requiredPermissions)
            }
        )
        return
    }

    var currentTab by rememberSaveable { mutableStateOf(selectedTab) }
    LaunchedEffect(selectedTab) {
        currentTab = selectedTab
    }

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

    val density = LocalDensity.current
    var floatingBarHeightDp by remember { mutableStateOf(0.dp) }
    val isImeVisible = WindowInsets.isImeVisible
    val effectiveBottomPadding = if (isImeVisible) 0.dp else floatingBarHeightDp
    val statusBarTopInset = max(
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(nc.background)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = nc.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                val showMiniPlayerTrack = if (!musicUiState.isNowPlayingExpanded) currentPlayingTrack else null
                FloatingBottomBar(
                    selectedTab = currentTab,
                    onTabSelected = {
                        currentTab = it
                        onTabSelected(it)
                    },
                    currentTrack = showMiniPlayerTrack,
                    isPlaying = isMusicPlaying,
                    currentPositionMs = musicPositionMs,
                    durationMs = musicDurationMs,
                    formatInfo = formatInfo,
                    onTogglePlayPause = { musicViewModel.playerManager.togglePlayPause() },
                    onSkipNext = { musicViewModel.playerManager.skipToNext() },
                    onClickMiniPlayer = { musicViewModel.setNowPlayingExpanded(true) },
                    onHeightChanged = { px ->
                        floatingBarHeightDp = with(density) { px.toDp() }
                    }
                )
            }
        ) { paddingValues ->
            CompositionLocalProvider(LocalBottomContentPadding provides effectiveBottomPadding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = statusBarTopInset)
                ) {
                    when (currentTab) {
                        MainNavigationTab.MUSIC -> {
                            MusicScreen(
                                viewModel = musicViewModel,
                                bottomPadding = effectiveBottomPadding
                            )
                        }

                        MainNavigationTab.VIDEO -> {
                            VideoLibraryScreen(
                                viewModel = videoLibraryViewModel,
                                onVideoSelected = { video ->
                                    videoLibraryViewModel.playVideo(video)
                                },
                                bottomPadding = effectiveBottomPadding
                            )
                        }

                        MainNavigationTab.SETTINGS -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                bottomPadding = effectiveBottomPadding
                            )
                        }
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

        // Audio Equalizer Sheet
        if (musicUiState.isEqualizerDialogVisible) {
            AudioEqualizerSheet(
                state = equalizerState,
                onToggleEnabled = { musicViewModel.setEqualizerEnabled(it) },
                onBandGainChange = { bandIndex, gainDb -> musicViewModel.setEqualizerBandGain(bandIndex, gainDb) },
                onSelectPreset = { presetName -> musicViewModel.setEqualizerPreset(presetName) },
                onResetBands = { musicViewModel.resetEqualizerBands() },
                onToggleBassBoost = { musicViewModel.setBassBoostEnabled(it) },
                onBassBoostChange = { musicViewModel.setBassBoostStrength(it) },
                onToggleVirtualizer = { musicViewModel.setVirtualizerEnabled(it) },
                onVirtualizerChange = { musicViewModel.setVirtualizerStrength(it) },
                onPreampChange = { musicViewModel.setPreampGain(it) },
                onResetPreamp = { musicViewModel.resetPreamp() },
                onSaveCustomPreset = { musicViewModel.saveCustomPreset(it) },
                onDeleteCustomPreset = { musicViewModel.deleteCustomPreset(it) },
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
