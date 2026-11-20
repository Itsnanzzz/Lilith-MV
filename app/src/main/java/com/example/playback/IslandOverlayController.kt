package com.example.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.LilithApplication
import com.example.R
import com.example.data.AppAccent
import com.example.data.DynamicIslandBridge
import com.example.data.IslandPosition
import com.example.data.IslandVisibility
import com.example.data.SettingsDataStore
import com.example.data.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private class OverlayViewTreeOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var isCreated = false
    private var isDestroyed = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun onAttach() {
        if (isDestroyed) return
        if (!isCreated) {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            isCreated = true
        }
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDetach() {
        if (isDestroyed) return
        isDestroyed = true
        if (isCreated) {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            }
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        store.clear()
    }
}

private data class OverlayGeometry(
    val yPx: Int,
    val compactHeightDp: Dp,
    val maxExpandedWidthDp: Dp,
    val isAroundCamera: Boolean = false,
    val compactWidthDp: Dp = IslandDimensions.CompactWidth,
    val middleGapDp: Dp = 0.dp,
    val expandedHeightDp: Dp = IslandDimensions.ExpandedHeight,
    val expandedTopPaddingDp: Dp = 14.dp
)

@OptIn(UnstableApi::class)
class IslandOverlayController(
    private val service: MediaSessionService,
    private val playerProvider: () -> Player?,
    private val mediaSessionProvider: () -> MediaSession?,
    private val settingsDataStore: SettingsDataStore
) {

    private companion object {
        const val TRACK_DEBOUNCE_MS = 150L
        const val BRIEFLY_VISIBLE_MS = 4000L
        const val PAUSE_HIDE_DELAY_MS = 10000L
        const val AUTO_COLLAPSE_MS = 5000L
        const val PREVIEW_DURATION_MS = 4000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayManager = service.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var enabledCollectorJob: Job? = null
    private var activeFeatureJob: Job? = null
    private var trackDebounceJob: Job? = null
    private var brieflyHideJob: Job? = null
    private var pauseHideJob: Job? = null
    private var autoCollapseJob: Job? = null
    private var previewJob: Job? = null

    private var isFeatureEnabled = false
    private var currentVisibility = IslandVisibility.BRIEFLY
    private var currentPosition = IslandPosition.BELOW_STATUS_BAR
    private var currentAppAccent = AppAccent.WHITE
    private var currentThemeMode = ThemeMode.SYSTEM
    private var systemDarkMode = isSystemDarkMode()
    private var currentIsDarkTheme = resolveEffectiveDarkTheme(currentThemeMode, systemDarkMode)
    private var isAppInForeground = LilithApplication.isAppInForegroundFlow.value
    private var isScreenOn = true
    private var isPreviewActive = false
    private var dismissedForMediaId: String? = null
    private var lastTouchDown = false
    private var lastInteractionMs = 0L

    private var composeView: ComposeView? = null
    private var viewTreeOwner: OverlayViewTreeOwner? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var uiState by mutableStateOf(IslandUiState())
    private var isWindowExpanded = false
    private var isPendingHideAfterCollapse = false

    private var playerListener: Player.Listener? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var isComponentCallbacksRegistered = false
    private var isDisplayListenerRegistered = false
    private var isDestroyed = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (!isFeatureEnabled || isDestroyed || composeView == null) return
            runOnMain {
                if (isFeatureEnabled && !isDestroyed && composeView != null) {
                    updateOverlayPositionAndSize()
                }
            }
        }
    }

    private val appComponentCallbacks = object : ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) {
            if (!isFeatureEnabled || isDestroyed) return
            val newSystemDark = isSystemDarkMode(newConfig)
            if (newSystemDark != systemDarkMode) {
                systemDarkMode = newSystemDark
                val nextEffectiveDark = resolveEffectiveDarkTheme(currentThemeMode, systemDarkMode)
                if (nextEffectiveDark != currentIsDarkTheme) {
                    currentIsDarkTheme = nextEffectiveDark
                    runOnMain {
                        uiState = uiState.copy(isDarkTheme = currentIsDarkTheme)
                    }
                }
            }
            if (composeView != null) {
                runOnMain {
                    if (isFeatureEnabled && !isDestroyed && composeView != null) {
                        updateOverlayPositionAndSize()
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {}
    }

    fun start() {
        if (isDestroyed || enabledCollectorJob != null) return
        enabledCollectorJob = serviceScope.launch {
            settingsDataStore.islandEnabledFlow
                .distinctUntilChanged()
                .collect { enabled ->
                    if (enabled && !Settings.canDrawOverlays(service)) {
                        settingsDataStore.setIslandEnabled(false)
                        deactivateOverlayFeature()
                    } else if (enabled) {
                        activateOverlayFeature()
                    } else {
                        deactivateOverlayFeature()
                    }
                }
        }
    }

    fun onConfigurationChanged(newConfig: Configuration) {
        if (!isFeatureEnabled || composeView == null) return
        runOnMain {
            updateOverlayPositionAndSize()
        }
    }

    fun onTaskRemoved() {
        runOnMain {
            isPreviewActive = false
            cancelTransientTimers()
            hideOverlayView()
        }
    }

    fun release() {
        isDestroyed = true
        enabledCollectorJob?.cancel()
        enabledCollectorJob = null
        deactivateOverlayFeature()
        serviceScope.cancel()
    }

    private fun activateOverlayFeature() {
        if (isFeatureEnabled || isDestroyed) return
        isFeatureEnabled = true

        val powerManager = service.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isScreenOn = powerManager?.isInteractive != false
        systemDarkMode = isSystemDarkMode()
        currentIsDarkTheme = resolveEffectiveDarkTheme(currentThemeMode, systemDarkMode)

        registerAppComponentCallbacks()
        registerDisplayListener()
        registerScreenReceiver()
        attachPlayerListener()

        activeFeatureJob = serviceScope.launch {
            launch {
                combine(
                    settingsDataStore.islandVisibilityFlow,
                    settingsDataStore.islandPositionFlow,
                    settingsDataStore.accentColorFlow,
                    settingsDataStore.themeModeFlow,
                    LilithApplication.isAppInForegroundFlow
                ) { visibility, position, accent, themeMode, foreground ->
                    ControllerPrefsState(visibility, position, accent, themeMode, foreground)
                }.collect { state ->
                    val prevVisibility = currentVisibility
                    val prevPosition = currentPosition
                    val prevForeground = isAppInForeground

                    currentVisibility = state.visibility
                    currentPosition = state.position
                    currentAppAccent = state.accent
                    currentThemeMode = state.themeMode
                    systemDarkMode = isSystemDarkMode()
                    currentIsDarkTheme = resolveEffectiveDarkTheme(currentThemeMode, systemDarkMode)
                    isAppInForeground = state.foreground

                    uiState = uiState.copy(
                        appAccent = currentAppAccent,
                        isDarkTheme = currentIsDarkTheme
                    )

                    if (prevPosition != currentPosition && composeView != null) {
                        updateOverlayPositionAndSize()
                    }

                    if (isAppInForeground && !prevForeground && !isPreviewActive) {
                        cancelTransientTimers()
                        hideOverlayView()
                    } else if (!isAppInForeground && prevForeground) {
                        if (currentVisibility == IslandVisibility.ALWAYS &&
                            playerProvider()?.isPlaying == true &&
                            isEligibleToShow()
                        ) {
                            showOrUpdateOverlay(expanded = false)
                        }
                    }

                    if (prevVisibility != currentVisibility) {
                        handleVisibilityModeChanged()
                    }
                }
            }

            launch {
                if (DynamicIslandBridge.consumePendingPreview()) {
                    triggerPreviewInternal()
                }
                DynamicIslandBridge.previewRequests.collect {
                    DynamicIslandBridge.consumePendingPreview()
                    triggerPreviewInternal()
                }
            }
        }
    }

    private fun deactivateOverlayFeature() {
        isFeatureEnabled = false
        isPreviewActive = false
        isWindowExpanded = false
        isPendingHideAfterCollapse = false
        dismissedForMediaId = null
        activeFeatureJob?.cancel()
        activeFeatureJob = null
        cancelTransientTimers()
        detachPlayerListener()
        unregisterScreenReceiver()
        unregisterDisplayListener()
        unregisterAppComponentCallbacks()
        runOnMain {
            hideOverlayView()
        }
    }

    private fun cancelTransientTimers() {
        trackDebounceJob?.cancel()
        trackDebounceJob = null
        brieflyHideJob?.cancel()
        brieflyHideJob = null
        pauseHideJob?.cancel()
        pauseHideJob = null
        autoCollapseJob?.cancel()
        autoCollapseJob = null
        previewJob?.cancel()
        previewJob = null
    }

    private fun handleVisibilityModeChanged() {
        val player = playerProvider()
        if (currentVisibility == IslandVisibility.ALWAYS) {
            brieflyHideJob?.cancel()
            brieflyHideJob = null
            if (player != null && player.isPlaying && isEligibleToShow()) {
                showOrUpdateOverlay(expanded = uiState.isExpanded)
            }
        } else {
            pauseHideJob?.cancel()
            pauseHideJob = null
            if (composeView != null && !uiState.isExpanded && !isPreviewActive) {
                scheduleBrieflyHide()
            }
        }
    }

    private fun attachPlayerListener() {
        val player = playerProvider() ?: return
        if (playerListener != null) return

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isFeatureEnabled || isDestroyed) return
                uiState = uiState.copy(isPlaying = isPlaying)
                if (isPreviewActive) return

                if (isPlaying) {
                    pauseHideJob?.cancel()
                    pauseHideJob = null
                    if (isEligibleToShow()) {
                        showOrUpdateOverlay(expanded = uiState.isExpanded && composeView != null)
                        if (currentVisibility == IslandVisibility.BRIEFLY && !uiState.isExpanded) {
                            scheduleBrieflyHide()
                        }
                    }
                } else {
                    if (currentVisibility == IslandVisibility.ALWAYS && composeView != null) {
                        schedulePauseHide()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!isFeatureEnabled || isDestroyed) return
                trackDebounceJob?.cancel()
                trackDebounceJob = serviceScope.launch {
                    delay(TRACK_DEBOUNCE_MS)
                    val playerNow = playerProvider() ?: return@launch
                    val item = playerNow.currentMediaItem
                    if (item == null ||
                        playerNow.playbackState == Player.STATE_IDLE ||
                        playerNow.playbackState == Player.STATE_ENDED
                    ) {
                        if (!isPreviewActive) {
                            hideOverlayView()
                        }
                        return@launch
                    }

                    val newMediaId = resolveMediaId(item)
                    if (newMediaId != dismissedForMediaId) {
                        dismissedForMediaId = null
                    }

                    if (isPreviewActive) {
                        isPreviewActive = false
                        previewJob?.cancel()
                        previewJob = null
                    }

                    val stayExpanded = composeView != null && uiState.isExpanded
                    if (isEligibleToShow() && (playerNow.isPlaying || stayExpanded)) {
                        showOrUpdateOverlay(expanded = stayExpanded)
                        if (currentVisibility == IslandVisibility.BRIEFLY && !stayExpanded) {
                            scheduleBrieflyHide()
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isFeatureEnabled || isDestroyed) return
                if ((playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) &&
                    !isPreviewActive
                ) {
                    cancelTransientTimers()
                    hideOverlayView()
                }
            }
        }
        playerListener = listener
        player.addListener(listener)
    }

    private fun detachPlayerListener() {
        val listener = playerListener ?: return
        playerProvider()?.removeListener(listener)
        playerListener = null
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        isPreviewActive = false
                        cancelTransientTimers()
                        runOnMain { hideOverlayView() }
                    }

                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        if (isFeatureEnabled &&
                            currentVisibility == IslandVisibility.ALWAYS &&
                            playerProvider()?.isPlaying == true &&
                            isEligibleToShow()
                        ) {
                            runOnMain { showOrUpdateOverlay(expanded = false) }
                        }
                    }
                }
            }
        }
        screenReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        ContextCompat.registerReceiver(service, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun unregisterScreenReceiver() {
        val receiver = screenReceiver ?: return
        try {
            service.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
        }
        screenReceiver = null
    }

    private fun isEligibleToShow(): Boolean {
        if (!isFeatureEnabled || isDestroyed) return false
        if (!Settings.canDrawOverlays(service)) {
            serviceScope.launch {
                settingsDataStore.setIslandEnabled(false)
            }
            return false
        }
        if (!isScreenOn) return false
        if (isAppInForeground && !isPreviewActive) return false

        val player = playerProvider()
        if (!isPreviewActive) {
            if (player == null) return false
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                return false
            }
            val item = player.currentMediaItem ?: return false
            val mediaId = resolveMediaId(item)
            if (mediaId == dismissedForMediaId) return false
        }
        return true
    }

    private fun triggerPreviewInternal() {
        if (!isFeatureEnabled || isDestroyed) return
        if (!Settings.canDrawOverlays(service)) {
            serviceScope.launch { settingsDataStore.setIslandEnabled(false) }
            return
        }
        isPreviewActive = true
        dismissedForMediaId = null
        previewJob?.cancel()
        brieflyHideJob?.cancel()
        pauseHideJob?.cancel()

        showOrUpdateOverlay(expanded = false, forcePreviewSampleIfEmpty = true)

        previewJob = serviceScope.launch {
            delay(PREVIEW_DURATION_MS)
            isPreviewActive = false
            if (composeView != null && !uiState.isExpanded) {
                if (isAppInForeground || currentVisibility == IslandVisibility.BRIEFLY || playerProvider()?.isPlaying != true) {
                    hideOverlayView()
                }
            }
        }
    }

    private fun scheduleBrieflyHide() {
        brieflyHideJob?.cancel()
        brieflyHideJob = serviceScope.launch {
            delay(BRIEFLY_VISIBLE_MS)
            if (!uiState.isExpanded && !isPreviewActive) {
                hideOverlayView()
            }
        }
    }

    private fun schedulePauseHide() {
        pauseHideJob?.cancel()
        pauseHideJob = serviceScope.launch {
            delay(PAUSE_HIDE_DELAY_MS)
            if (playerProvider()?.isPlaying != true && !isPreviewActive) {
                hideOverlayView()
            }
        }
    }

    private fun scheduleAutoCollapse() {
        autoCollapseJob?.cancel()
        lastInteractionMs = SystemClock.elapsedRealtime()
        autoCollapseJob = serviceScope.launch {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - lastInteractionMs
                val remaining = AUTO_COLLAPSE_MS - elapsed
                if (remaining <= 0L && !lastTouchDown) {
                    val shouldHideAfterCollapse =
                        currentVisibility == IslandVisibility.BRIEFLY ||
                            isPreviewActive ||
                            isAppInForeground ||
                            playerProvider()?.isPlaying != true
                    isPendingHideAfterCollapse = shouldHideAfterCollapse
                    uiState = uiState.copy(isExpanded = false)
                    if (shouldHideAfterCollapse) {
                        delay(260L)
                        if (!uiState.isExpanded) {
                            isPreviewActive = false
                            hideOverlayView()
                        }
                    }
                    break
                }
                delay(remaining.coerceAtLeast(200L))
            }
        }
    }

    private fun showOrUpdateOverlay(
        expanded: Boolean,
        forcePreviewSampleIfEmpty: Boolean = false
    ) {
        if (!Settings.canDrawOverlays(service)) {
            serviceScope.launch { settingsDataStore.setIslandEnabled(false) }
            return
        }

        val geometry = computeOverlayGeometry()
        val player = playerProvider()
        val currentItem = player?.currentMediaItem

        val nextState = if (currentItem != null) {
            val meta = currentItem.mediaMetadata
            val title = meta.title?.toString()?.takeIf { it.isNotBlank() }
                ?: service.getString(R.string.default_song_title)
            val artist = meta.artist?.toString()?.takeIf { it.isNotBlank() }
                ?: service.getString(R.string.unknown_artist)
            val artTarget = meta.artworkUri?.toString()?.takeIf { it.isNotBlank() }
                ?: currentItem.mediaId.takeIf { it.isNotBlank() }
                ?: currentItem.localConfiguration?.uri?.toString()
            val snapshot = readCurrentProgressSnapshot()
            IslandUiState(
                mediaId = resolveMediaId(currentItem),
                title = title,
                artist = artist,
                artTarget = artTarget,
                isPlaying = player.isPlaying,
                isExpanded = expanded,
                appAccent = currentAppAccent,
                isDarkTheme = currentIsDarkTheme,
                isAroundCamera = geometry.isAroundCamera,
                compactWidthDp = geometry.compactWidthDp,
                compactHeightDp = geometry.compactHeightDp,
                middleGapDp = geometry.middleGapDp,
                maxExpandedWidthDp = geometry.maxExpandedWidthDp,
                expandedHeightDp = geometry.expandedHeightDp,
                expandedTopPaddingDp = geometry.expandedTopPaddingDp,
                initialProgress = snapshot.progress,
                initialPositionMs = snapshot.positionMs,
                initialDurationMs = snapshot.durationMs,
                isPreviewSample = false
            )
        } else if (forcePreviewSampleIfEmpty) {
            IslandUiState(
                mediaId = "__preview_sample__",
                title = service.getString(R.string.island_preview_sample_title),
                artist = service.getString(R.string.island_preview_sample_artist),
                artTarget = null,
                isPlaying = true,
                isExpanded = expanded,
                appAccent = currentAppAccent,
                isDarkTheme = currentIsDarkTheme,
                isAroundCamera = geometry.isAroundCamera,
                compactWidthDp = geometry.compactWidthDp,
                compactHeightDp = geometry.compactHeightDp,
                middleGapDp = geometry.middleGapDp,
                maxExpandedWidthDp = geometry.maxExpandedWidthDp,
                expandedHeightDp = geometry.expandedHeightDp,
                expandedTopPaddingDp = geometry.expandedTopPaddingDp,
                initialProgress = 0.40f,
                initialPositionMs = 88_000L,
                initialDurationMs = 220_000L,
                isPreviewSample = true
            )
        } else {
            return
        }

        if (expanded) {
            isWindowExpanded = true
            isPendingHideAfterCollapse = false
        }
        uiState = nextState

        if (composeView == null) {
            isWindowExpanded = expanded
            attachOverlayView(geometry)
        } else {
            updateOverlayLayoutParams(geometry)
        }
    }

    private fun attachOverlayView(geometry: OverlayGeometry) {
        if (composeView != null) return

        val params = createLayoutParams(geometry, isWindowExpanded)
        val owner = OverlayViewTreeOwner()
        val view = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    DynamicIslandPill(
                        state = uiState,
                        onReadProgress = { readCurrentProgressSnapshot() },
                        onExpand = {
                            val snapshot = readCurrentProgressSnapshot()
                            isWindowExpanded = true
                            isPendingHideAfterCollapse = false
                            uiState = uiState.copy(
                                isExpanded = true,
                                initialProgress = snapshot.progress,
                                initialPositionMs = snapshot.positionMs,
                                initialDurationMs = snapshot.durationMs
                            )
                            updateOverlayLayoutParams(computeOverlayGeometry())
                            brieflyHideJob?.cancel()
                            scheduleAutoCollapse()
                        },
                        onOpenApp = { openPlayerActivity() },
                        onPrevious = {
                            if (!uiState.isPreviewSample) {
                                playerProvider()?.seekToPrevious()
                            }
                        },
                        onTogglePlayPause = {
                            if (uiState.isPreviewSample) {
                                uiState = uiState.copy(isPlaying = !uiState.isPlaying)
                            } else {
                                playerProvider()?.let { player ->
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        },
                        onNext = {
                            if (!uiState.isPreviewSample) {
                                playerProvider()?.seekToNext()
                            }
                        },
                        onSwipeUpDismiss = {
                            isPreviewActive = false
                            dismissedForMediaId = uiState.mediaId.takeIf { it.isNotBlank() && !uiState.isPreviewSample }
                            cancelTransientTimers()
                            hideOverlayView()
                        },
                        onTouchDownChanged = { down ->
                            lastTouchDown = down
                            if (down) {
                                lastInteractionMs = SystemClock.elapsedRealtime()
                            }
                        },
                        onInteraction = {
                            lastInteractionMs = SystemClock.elapsedRealtime()
                        },
                        onCollapseSettled = {
                            if (!uiState.isExpanded && isWindowExpanded && !isPendingHideAfterCollapse && composeView != null) {
                                isWindowExpanded = false
                                updateOverlayLayoutParams(computeOverlayGeometry())
                            }
                        }
                    )
                }
            }
        }

        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.Q) {
            view.setOnApplyWindowInsetsListener { v, insets ->
                if (currentPosition == IslandPosition.AROUND_CAMERA) {
                    mainHandler.post {
                        if (composeView === v) {
                            updateOverlayPositionAndSize()
                        }
                    }
                }
                v.onApplyWindowInsets(insets)
            }
        }

        try {
            owner.onAttach()
            windowManager.addView(view, params)
            composeView = view
            viewTreeOwner = owner
            windowParams = params
        } catch (t: Throwable) {
            owner.onDetach()
            handleOverlayFailure(t)
        }
    }

    private fun updateOverlayPositionAndSize() {
        val geometry = computeOverlayGeometry()
        if (uiState.isAroundCamera != geometry.isAroundCamera ||
            uiState.compactWidthDp != geometry.compactWidthDp ||
            uiState.compactHeightDp != geometry.compactHeightDp ||
            uiState.middleGapDp != geometry.middleGapDp ||
            uiState.maxExpandedWidthDp != geometry.maxExpandedWidthDp ||
            uiState.expandedHeightDp != geometry.expandedHeightDp ||
            uiState.expandedTopPaddingDp != geometry.expandedTopPaddingDp
        ) {
            uiState = uiState.copy(
                isAroundCamera = geometry.isAroundCamera,
                compactWidthDp = geometry.compactWidthDp,
                compactHeightDp = geometry.compactHeightDp,
                middleGapDp = geometry.middleGapDp,
                maxExpandedWidthDp = geometry.maxExpandedWidthDp,
                expandedHeightDp = geometry.expandedHeightDp,
                expandedTopPaddingDp = geometry.expandedTopPaddingDp
            )
        }
        updateOverlayLayoutParams(geometry)
    }

    private fun updateOverlayLayoutParams(geometry: OverlayGeometry) {
        val view = composeView ?: return
        val params = windowParams ?: return
        val (targetWidthPx, targetHeightPx) = computeWindowSizePx(geometry, isWindowExpanded)
        if (params.y == geometry.yPx &&
            params.width == targetWidthPx &&
            params.height == targetHeightPx
        ) {
            return
        }

        params.y = geometry.yPx
        params.width = targetWidthPx
        params.height = targetHeightPx
        try {
            windowManager.updateViewLayout(view, params)
        } catch (t: Throwable) {
            handleOverlayFailure(t)
        }
    }

    private fun hideOverlayView() {
        autoCollapseJob?.cancel()
        autoCollapseJob = null
        brieflyHideJob?.cancel()
        brieflyHideJob = null
        pauseHideJob?.cancel()
        pauseHideJob = null
        lastTouchDown = false
        isWindowExpanded = false
        isPendingHideAfterCollapse = false

        val view = composeView
        val owner = viewTreeOwner
        composeView = null
        viewTreeOwner = null
        windowParams = null
        uiState = uiState.copy(isExpanded = false)

        if (view != null) {
            try {
                view.disposeComposition()
            } catch (_: Throwable) {
            }
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Throwable) {
            }
        }
        owner?.onDetach()
    }

    private fun handleOverlayFailure(@Suppress("UNUSED_PARAMETER") error: Throwable) {
        cancelTransientTimers()
        hideOverlayView()
        serviceScope.launch {
            settingsDataStore.setIslandEnabled(false)
        }
        val errorMessage = service.getString(R.string.island_error_turned_off)
        DynamicIslandBridge.emitError(errorMessage)
        if (!LilithApplication.isAppInForegroundFlow.value) {
            mainHandler.post {
                Toast.makeText(service.applicationContext, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openPlayerActivity() {
        try {
            val sessionActivity = mediaSessionProvider()?.sessionActivity
            if (sessionActivity != null) {
                sessionActivity.send()
            } else {
                val launchIntent = service.packageManager.getLaunchIntentForPackage(service.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (launchIntent != null) {
                    service.startActivity(launchIntent)
                }
            }
        } catch (_: PendingIntent.CanceledException) {
        } catch (_: Throwable) {
        } finally {
            isPreviewActive = false
            cancelTransientTimers()
            hideOverlayView()
        }
    }

    private fun createLayoutParams(
        geometry: OverlayGeometry,
        expandedWindow: Boolean
    ): WindowManager.LayoutParams {
        val (widthPx, heightPx) = computeWindowSizePx(geometry, expandedWindow)
        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            title = "LilithDynamicIsland"
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = geometry.yPx
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun computeWindowSizePx(
        geometry: OverlayGeometry,
        expandedWindow: Boolean
    ): Pair<Int, Int> {
        val density = service.resources.displayMetrics.density.coerceAtLeast(1f)
        val widthDp = if (expandedWindow) {
            geometry.maxExpandedWidthDp
        } else {
            geometry.compactWidthDp
        }
        val heightDp = if (expandedWindow) {
            geometry.expandedHeightDp
        } else {
            geometry.compactHeightDp
        }
        return dpToPx(widthDp.value, density) to dpToPx(heightDp.value, density)
    }

    private fun computeOverlayGeometry(): OverlayGeometry {
        val density = service.resources.displayMetrics.density.coerceAtLeast(1f)
        val screenWidthPx = getScreenWidthPx()
        val maxExpandedWidthDp = ((screenWidthPx / density).dp - 24.dp)
            .coerceAtLeast(220.dp)
            .coerceAtMost(360.dp)

        val statusBarHeightPx = getStatusBarHeightPx()
        val defaultYPx = statusBarHeightPx + dpToPx(4f, density)
        val defaultCompactHeightDp = 36.dp

        if (currentPosition == IslandPosition.BELOW_STATUS_BAR ||
            service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        ) {
            return OverlayGeometry(
                yPx = defaultYPx,
                compactHeightDp = defaultCompactHeightDp,
                maxExpandedWidthDp = maxExpandedWidthDp
            )
        }

        val cutoutRect = findTopCutoutRect(screenWidthPx, density)
            ?: return OverlayGeometry(
                yPx = defaultYPx,
                compactHeightDp = defaultCompactHeightDp,
                maxExpandedWidthDp = maxExpandedWidthDp
            )

        val cameraTopDp = cutoutRect.top / density
        val cameraBottomDp = cutoutRect.bottom / density
        val cameraWidthDp = cutoutRect.width() / density
        val cameraHeightDp = cutoutRect.height() / density

        val pillTopDp = (cameraTopDp - 2f).coerceAtLeast(0f)
        val yPx = dpToPx(pillTopDp, density)
        val compactHeightDp = (cameraHeightDp + 4f).coerceIn(24f, 34f).dp
        val compactWidthDp = (cameraWidthDp + 76f).coerceIn(96f, 140f).dp
        val middleGapDp = (cameraWidthDp + 8f).dp
        val expandedTopPaddingDp = ((cameraBottomDp - pillTopDp).coerceAtLeast(0f) + 6f).dp
        val expandedHeightDp = expandedTopPaddingDp + 90.dp

        return OverlayGeometry(
            yPx = yPx,
            compactHeightDp = compactHeightDp,
            maxExpandedWidthDp = maxExpandedWidthDp,
            isAroundCamera = true,
            compactWidthDp = compactWidthDp,
            middleGapDp = middleGapDp,
            expandedHeightDp = expandedHeightDp,
            expandedTopPaddingDp = expandedTopPaddingDp
        )
    }

    private fun findTopCutoutRect(
        screenWidthPx: Int,
        density: Float
    ): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        val boundingRects: List<Rect> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.windowInsets.displayCutout?.boundingRects.orEmpty()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val displayCutout = windowManager.defaultDisplay?.cutout
            displayCutout?.boundingRects
                ?: composeView?.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        } else {
            composeView?.rootWindowInsets?.displayCutout?.boundingRects.orEmpty()
        }

        if (boundingRects.isEmpty()) return null

        val topCandidates = boundingRects.filter { rect ->
            rect.width() > 0 &&
                rect.height() > 0 &&
                rect.top == 0
        }
        if (topCandidates.isEmpty()) return null

        val screenCenterXDp = (screenWidthPx / 2f) / density
        val closest = topCandidates.minByOrNull { abs((it.exactCenterX() / density) - screenCenterXDp) }
            ?: return null
        val cameraCenterXDp = closest.exactCenterX() / density
        if (abs(cameraCenterXDp - screenCenterXDp) > 8f) return null

        return closest
    }

    private fun getScreenWidthPx(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width().coerceAtLeast(1)
        } else {
            service.resources.displayMetrics.widthPixels.coerceAtLeast(1)
        }
    }

    private fun getStatusBarHeightPx(): Int {
        val density = service.resources.displayMetrics.density.coerceAtLeast(1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val top = windowManager.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
                .top
            if (top > 0) return top
        }
        val resId = service.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) {
            service.resources.getDimensionPixelSize(resId)
        } else {
            dpToPx(24f, density)
        }
    }

    private fun readCurrentProgressSnapshot(): IslandProgressSnapshot {
        if (uiState.isPreviewSample) {
            return IslandProgressSnapshot(
                progress = uiState.initialProgress,
                positionMs = uiState.initialPositionMs,
                durationMs = uiState.initialDurationMs
            )
        }
        val player = playerProvider() ?: return IslandProgressSnapshot()
        val duration = player.duration.coerceAtLeast(0L)
        val position = player.currentPosition.coerceAtLeast(0L)
        if (duration <= 0L) return IslandProgressSnapshot(progress = 0f, positionMs = position, durationMs = 0L)
        val fraction = (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        return IslandProgressSnapshot(
            progress = fraction,
            positionMs = position,
            durationMs = duration
        )
    }

    private fun registerDisplayListener() {
        if (isDisplayListenerRegistered) return
        val dm = displayManager ?: return
        dm.registerDisplayListener(displayListener, mainHandler)
        isDisplayListenerRegistered = true
    }

    private fun unregisterDisplayListener() {
        if (!isDisplayListenerRegistered) return
        try {
            displayManager?.unregisterDisplayListener(displayListener)
        } catch (_: Throwable) {
        }
        isDisplayListenerRegistered = false
    }

    private fun registerAppComponentCallbacks() {
        if (isComponentCallbacksRegistered) return
        service.applicationContext.registerComponentCallbacks(appComponentCallbacks)
        isComponentCallbacksRegistered = true
    }

    private fun unregisterAppComponentCallbacks() {
        if (!isComponentCallbacksRegistered) return
        try {
            service.applicationContext.unregisterComponentCallbacks(appComponentCallbacks)
        } catch (_: Throwable) {
        }
        isComponentCallbacksRegistered = false
    }

    private fun isSystemDarkMode(
        config: Configuration = service.applicationContext.resources.configuration
    ): Boolean {
        return (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun resolveEffectiveDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean {
        return when (mode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
        }
    }

    private fun resolveMediaId(item: MediaItem): String {
        return item.mediaId.takeIf { it.isNotBlank() }
            ?: item.localConfiguration?.uri?.toString()
            ?: item.mediaMetadata.title?.toString()
            ?: ""
    }

    private fun dpToPx(dp: Float, density: Float): Int = (dp * density + 0.5f).toInt()

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private data class ControllerPrefsState(
        val visibility: IslandVisibility,
        val position: IslandPosition,
        val accent: AppAccent,
        val themeMode: ThemeMode,
        val foreground: Boolean
    )
}
