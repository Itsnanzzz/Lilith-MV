package com.example.ui.music

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.Animatable as ColorAnimatable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.R
import com.example.data.LyricLine
import com.example.data.LyricsParser
import com.example.ui.theme.AppColors
import com.example.ui.theme.AppFontFamily
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import com.example.ui.theme.LyricsFontFamily
import com.example.ui.theme.rememberIsAnimationDisabled
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

// Auto-scroll and line-transition timing constants (a larger MIN is slower and smoother)
private const val AUTO_SCROLL_MIN_DURATION_MS = 380
private const val AUTO_SCROLL_MAX_DURATION_MS = 700
private const val AUTO_SCROLL_DURATION_RATIO = 0.6f
private const val AUTO_SCROLL_REST_FRACTION = 0.38f
private const val AUTO_SCROLL_FALLBACK_GAP_MS = 600L
private const val AUTO_SCROLL_SEEK_DURATION_MS = 350
private const val AUTO_SCROLL_DEFAULT_DURATION_MS = 420
private const val AUTO_SCROLL_MANUAL_PAUSE_MS = 3000L
private const val AUTO_SCROLL_SEEK_JUMP_THRESHOLD_MS = 1500L
private const val LINE_TRANSITION_STAGGER_STEP_MS = 40
private const val LINE_TRANSITION_MAX_STAGGER_STEPS = 3
private const val LINE_SCALE_ACTIVE = 1.0f
private const val LINE_SCALE_INACTIVE = 0.94f
// Positive = lyrics appear earlier
internal const val LYRICS_DEFAULT_OFFSET_MS = 500L

private val ActiveLyricTextStyle = TextStyle(
    fontFamily = LyricsFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 34.sp,
    letterSpacing = 0.1.sp,
    textAlign = TextAlign.Center
)

private val InactiveLyricTextStyle = TextStyle(
    fontFamily = LyricsFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 19.sp,
    lineHeight = 27.sp,
    letterSpacing = 0.1.sp,
    textAlign = TextAlign.Center
)

@Composable
fun LyricsContainer(
    lyricsState: LyricsUiState,
    trackId: Long,
    isPlaying: Boolean,
    isVisible: Boolean,
    positionFlow: StateFlow<Long>,
    syncOffsetMs: Long,
    onSeek: (Long) -> Unit,
    onImportLyrics: () -> Unit,
    onRemoveImportedLyrics: () -> Unit,
    onOpenSyncOffsetDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }

    val loadedState = lyricsState as? LyricsUiState.Loaded
    val canImport = when (lyricsState) {
        LyricsUiState.Idle -> false
        is LyricsUiState.Loading -> lyricsState.canImport
        is LyricsUiState.Loaded -> lyricsState.canImport
        is LyricsUiState.Empty -> lyricsState.canImport
        is LyricsUiState.Error -> lyricsState.canImport
    }
    val hasImported = loadedState?.hasImportedLyrics == true
    val isSyncedLoaded = loadedState?.lyrics?.synced == true
    val hasMenuActions = canImport || hasImported || isSyncedLoaded

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_container")
    ) {
        // Compact flat header row (~36-40dp visual height, >=48dp touch target on overflow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp)
                .padding(start = 16.dp, end = 4.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.lyrics_title),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                if (loadedState != null) {
                    val badgeText = if (loadedState.lyrics.synced) {
                        stringResource(R.string.lyrics_badge_synced)
                    } else {
                        stringResource(R.string.lyrics_badge_plain)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(AppTokens.RadiusPill))
                            .background(colors.background.copy(alpha = 0.72f))
                            .border(1.dp, colors.hairline, RoundedCornerShape(AppTokens.RadiusPill))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            style = AppTypography.badge,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            if (hasMenuActions) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clip(CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClick = { menuExpanded = true }
                        )
                        .testTag("lyrics_overflow_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.lyrics_more_options),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(colors.surface)
                    ) {
                        if (canImport) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.lyrics_import_menu),
                                        style = AppTypography.body,
                                        color = colors.textPrimary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onImportLyrics()
                                }
                            )
                        }
                        if (hasImported) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.lyrics_remove_imported),
                                        style = AppTypography.body,
                                        color = colors.destructive
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveImportedLyrics()
                                }
                            )
                        }
                        if (isSyncedLoaded) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.lyrics_sync_offset),
                                        style = AppTypography.body,
                                        color = colors.textPrimary
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSyncOffsetDialog()
                                }
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }

        // Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (lyricsState) {
                LyricsUiState.Idle -> Unit

                is LyricsUiState.Loading -> {
                    var showSpinner by remember(lyricsState.trackId) { mutableStateOf(false) }
                    LaunchedEffect(lyricsState.trackId) {
                        showSpinner = false
                        delay(300L)
                        showSpinner = true
                    }
                    if (showSpinner) {
                        CircularProgressIndicator(
                            color = colors.accentText,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                is LyricsUiState.Empty -> {
                    LyricsEmptyOrErrorState(
                        title = stringResource(R.string.lyrics_empty_title),
                        helper = if (lyricsState.canImport) {
                            stringResource(R.string.lyrics_empty_helper)
                        } else {
                            stringResource(R.string.lyrics_empty_no_import_helper)
                        },
                        showImportButton = lyricsState.canImport,
                        onImportClick = onImportLyrics,
                        colors = colors,
                        testTag = "lyrics_empty_state"
                    )
                }

                is LyricsUiState.Error -> {
                    LyricsEmptyOrErrorState(
                        title = stringResource(R.string.lyrics_error_title),
                        helper = null,
                        showImportButton = lyricsState.canImport,
                        onImportClick = onImportLyrics,
                        colors = colors,
                        testTag = "lyrics_error_state"
                    )
                }

                is LyricsUiState.Loaded -> {
                    key(lyricsState.trackId, lyricsState.lyrics) {
                        if (lyricsState.lyrics.synced) {
                            SyncedLyricsList(
                                trackId = lyricsState.trackId,
                                lines = lyricsState.lyrics.lines,
                                isPlaying = isPlaying,
                                isVisible = isVisible,
                                positionFlow = positionFlow,
                                syncOffsetMs = syncOffsetMs,
                                onSeek = onSeek,
                                colors = colors
                            )
                        } else {
                            PlainLyricsList(
                                lines = lyricsState.lyrics.lines,
                                colors = colors
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsEmptyOrErrorState(
    title: String,
    helper: String?,
    showImportButton: Boolean,
    onImportClick: () -> Unit,
    colors: AppColors,
    testTag: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lyrics),
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = AppTypography.listTitle.copy(fontFamily = LyricsFontFamily),
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        if (!helper.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = helper,
                style = AppTypography.listSubtitle.copy(fontFamily = LyricsFontFamily),
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
        if (showImportButton) {
            Spacer(modifier = Modifier.height(16.dp))
            AppPrimaryButton(
                text = stringResource(R.string.lyrics_import),
                onClick = onImportClick,
                modifier = Modifier.testTag("lyrics_import_button")
            )
        }
    }
}

@Composable
private fun SyncedLyricsList(
    trackId: Long,
    lines: List<LyricLine>,
    isPlaying: Boolean,
    isVisible: Boolean,
    positionFlow: StateFlow<Long>,
    syncOffsetMs: Long,
    onSeek: (Long) -> Unit,
    colors: AppColors
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val listState = rememberLazyListState()

    val activeIndexState = remember(trackId, lines) {
        val initialEffectivePos = applyLyricsOffset(positionFlow.value, syncOffsetMs)
        mutableIntStateOf(LyricsParser.findActiveLineIndex(lines, initialEffectivePos))
    }
    val transitionDurationState = remember(trackId, lines) {
        mutableIntStateOf(
            if (activeIndexState.intValue >= 0) {
                computeAdaptiveScrollDurationMs(lines, activeIndexState.intValue)
            } else {
                AUTO_SCROLL_DEFAULT_DURATION_MS
            }
        )
    }

    var lastEffectivePositionMs by remember(trackId) {
        mutableLongStateOf(applyLyricsOffset(positionFlow.value, syncOffsetMs))
    }
    var pendingTapSeek by remember(trackId) { mutableStateOf(false) }
    val pendingIsSeekState = remember(trackId) { mutableStateOf(false) }

    // Collect playback position ONLY while lyrics are visible and screen is RESUMED
    LaunchedEffect(positionFlow, trackId, lines, syncOffsetMs, isVisible, lifecycleOwner) {
        if (!isVisible) return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            positionFlow.collect { pos ->
                // Positive syncOffsetMs brings lyrics earlier, matching LRC [offset:+ms].
                val effectivePos = applyLyricsOffset(pos, syncOffsetMs)
                val newIndex = LyricsParser.findActiveLineIndex(lines, effectivePos)
                val prevIndex = activeIndexState.intValue
                val isTimeJump = effectivePos < lastEffectivePositionMs - 100L ||
                    (effectivePos - lastEffectivePositionMs) > AUTO_SCROLL_SEEK_JUMP_THRESHOLD_MS
                val isIndexJump = prevIndex >= 0 && newIndex >= 0 && abs(newIndex - prevIndex) > 1
                val isSeek = pendingTapSeek || isTimeJump || isIndexJump

                pendingTapSeek = false
                lastEffectivePositionMs = effectivePos

                if (newIndex != prevIndex) {
                    pendingIsSeekState.value = isSeek
                    if (newIndex >= 0) {
                        transitionDurationState.intValue = if (isSeek) {
                            AUTO_SCROLL_SEEK_DURATION_MS
                        } else {
                            computeAdaptiveScrollDurationMs(lines, newIndex)
                        }
                    }
                    activeIndexState.intValue = newIndex
                }
            }
        }
    }

    // Monitor TalkBack touch exploration so liveRegion is only attached when TalkBack is active
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var isTouchExplorationEnabled by remember(accessibilityManager) {
        mutableStateOf(accessibilityManager?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            isTouchExplorationEnabled = enabled
        }
        accessibilityManager?.addTouchExplorationStateChangeListener(listener)
        onDispose {
            accessibilityManager?.removeTouchExplorationStateChangeListener(listener)
        }
    }

    var isAutoScrollPaused by remember(trackId) { mutableStateOf(false) }
    var scrollTouchCounter by remember(trackId) { mutableIntStateOf(0) }
    var isDragging by remember(trackId) { mutableStateOf(false) }
    val autoScrollJobHolder = remember(trackId) { mutableStateOf<Job?>(null) }

    LaunchedEffect(listState, trackId) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isDragging = true
                    isAutoScrollPaused = true
                    pendingIsSeekState.value = false
                    autoScrollJobHolder.value?.cancel()
                }
                is DragInteraction.Stop,
                is DragInteraction.Cancel -> {
                    isDragging = false
                    scrollTouchCounter++
                }
            }
        }
    }

    LaunchedEffect(scrollTouchCounter, isDragging, isVisible, isPlaying, trackId) {
        if (isVisible && isPlaying && !isDragging && scrollTouchCounter > 0 && isAutoScrollPaused) {
            delay(AUTO_SCROLL_MANUAL_PAUSE_MS)
            isAutoScrollPaused = false
        }
    }

    SyncedLyricsAutoScroller(
        trackId = trackId,
        lines = lines,
        listState = listState,
        activeIndexState = activeIndexState,
        transitionDurationState = transitionDurationState,
        pendingIsSeekState = pendingIsSeekState,
        autoScrollJobHolder = autoScrollJobHolder,
        isAutoScrollPaused = isAutoScrollPaused,
        isDragging = isDragging,
        isVisible = isVisible,
        isAnimationDisabled = isAnimationDisabled
    )

    val onSeekLineClick = remember(onSeek, syncOffsetMs) {
        { timeMs: Long ->
            pendingTapSeek = true
            onSeek(calculateLyricTapSeekPositionMs(timeMs, syncOffsetMs))
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .fadingVerticalEdges(edgeHeight = 24.dp)
            .testTag("synced_lyrics_list"),
        contentPadding = PaddingValues(top = 28.dp, bottom = 72.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(
            items = lines,
            key = { index, line -> "${trackId}_${index}_${line.timeMs ?: -1L}" }
        ) { index, line ->
            if (line.text.isEmpty()) {
                // Extra 8dp top + 8dp bottom around empty timestamp pause lines
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                val isActive by remember(index, activeIndexState) {
                    derivedStateOf { activeIndexState.value == index }
                }
                val onLineClick = remember(line.timeMs, onSeekLineClick) {
                    {
                        line.timeMs?.let(onSeekLineClick)
                        Unit
                    }
                }
                SyncedLyricLineItem(
                    text = line.text,
                    index = index,
                    isActive = isActive,
                    activeIndexState = activeIndexState,
                    transitionDurationState = transitionDurationState,
                    isVisible = isVisible,
                    isAnimationDisabled = isAnimationDisabled,
                    isTouchExplorationEnabled = isTouchExplorationEnabled,
                    colors = colors,
                    onClick = onLineClick
                )
            }
        }
    }
}

@Composable
private fun SyncedLyricsAutoScroller(
    trackId: Long,
    lines: List<LyricLine>,
    listState: LazyListState,
    activeIndexState: State<Int>,
    transitionDurationState: androidx.compose.runtime.MutableIntState,
    pendingIsSeekState: androidx.compose.runtime.MutableState<Boolean>,
    autoScrollJobHolder: androidx.compose.runtime.MutableState<Job?>,
    isAutoScrollPaused: Boolean,
    isDragging: Boolean,
    isVisible: Boolean,
    isAnimationDisabled: Boolean
) {
    var hasPlacedInitialPosition by remember(trackId) { mutableStateOf(false) }
    var isProgrammaticScroll by remember(trackId) { mutableStateOf(false) }
    val activeIndex = activeIndexState.value

    LaunchedEffect(activeIndex, isAutoScrollPaused, isVisible, trackId) {
        if (!isVisible) {
            autoScrollJobHolder.value?.cancel()
            return@LaunchedEffect
        }
        if (activeIndex < 0 || activeIndex >= lines.size) {
            if (!hasPlacedInitialPosition) {
                hasPlacedInitialPosition = true
            }
            return@LaunchedEffect
        }
        if (isDragging || isAutoScrollPaused) return@LaunchedEffect

        autoScrollJobHolder.value?.cancel()
        autoScrollJobHolder.value = coroutineContext[Job]

        if (!isProgrammaticScroll && listState.isScrollInProgress) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
        }
        if (isDragging || isAutoScrollPaused) return@LaunchedEffect

        val isInitialPlacement = !hasPlacedInitialPosition
        val shouldAnimate = !isInitialPlacement && !isAnimationDisabled
        val durationMs = when {
            !shouldAnimate -> 0
            pendingIsSeekState.value -> {
                pendingIsSeekState.value = false
                AUTO_SCROLL_SEEK_DURATION_MS
            }
            else -> computeAdaptiveScrollDurationMs(lines, activeIndex)
        }
        if (shouldAnimate) {
            transitionDurationState.intValue = durationMs
        }

        // Wait one frame after activeIndex recomposition so visibleItemsInfo has updated heights
        withFrameNanos { }

        try {
            isProgrammaticScroll = true
            scrollToActiveLine(
                listState = listState,
                targetIndex = activeIndex,
                animate = shouldAnimate,
                durationMs = durationMs
            )
            hasPlacedInitialPosition = true
        } finally {
            if (autoScrollJobHolder.value === coroutineContext[Job]) {
                isProgrammaticScroll = false
                autoScrollJobHolder.value = null
            }
        }
    }
}

@Composable
private fun SyncedLyricLineItem(
    text: String,
    index: Int,
    isActive: Boolean,
    activeIndexState: State<Int>,
    transitionDurationState: State<Int>,
    isVisible: Boolean,
    isAnimationDisabled: Boolean,
    isTouchExplorationEnabled: Boolean,
    colors: AppColors,
    onClick: () -> Unit
) {
    val initialColor = remember(index, colors) {
        Snapshot.withoutReadObservation {
            resolveLyricLineColor(
                index = index,
                activeIndex = activeIndexState.value,
                colors = colors
            )
        }
    }
    val colorAnim = remember(index, colors) { ColorAnimatable(initialColor) }
    val scaleAnim = remember(index) {
        val initialScale = Snapshot.withoutReadObservation {
            if (activeIndexState.value == index) LINE_SCALE_ACTIVE else LINE_SCALE_INACTIVE
        }
        Animatable(initialScale)
    }

    LaunchedEffect(index, isVisible, isAnimationDisabled, colors) {
        if (!isVisible) return@LaunchedEffect
        var isFirstEmission = true
        snapshotFlow { activeIndexState.value }.collectLatest { currentActive ->
            val distance = if (currentActive >= 0) abs(index - currentActive) else Int.MAX_VALUE
            val targetColor = resolveLyricLineColor(
                index = index,
                activeIndex = currentActive,
                colors = colors
            )
            val targetScale = if (currentActive == index) LINE_SCALE_ACTIVE else LINE_SCALE_INACTIVE

            if (isFirstEmission || isAnimationDisabled) {
                isFirstEmission = false
                colorAnim.snapTo(targetColor)
                scaleAnim.snapTo(targetScale)
            } else {
                val durationMs = transitionDurationState.value.coerceAtLeast(1)
                val staggerMs = if (currentActive < 0 || distance == 0) {
                    0
                } else {
                    distance.coerceAtMost(LINE_TRANSITION_MAX_STAGGER_STEPS) * LINE_TRANSITION_STAGGER_STEP_MS
                }
                coroutineScope {
                    launch {
                        colorAnim.animateTo(
                            targetValue = targetColor,
                            animationSpec = tween(
                                durationMillis = durationMs,
                                delayMillis = staggerMs,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                    launch {
                        scaleAnim.animateTo(
                            targetValue = targetScale,
                            animationSpec = tween(
                                durationMillis = durationMs,
                                easing = FastOutSlowInEasing
                            )
                        )
                    }
                }
            }
        }
    }

    val lineSemantics = if (isActive && isTouchExplorationEnabled) {
        Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(AppTokens.Radius12))
            .clickable(
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 4.dp)
            .then(lineSemantics),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = text,
            style = if (isActive) ActiveLyricTextStyle else InactiveLyricTextStyle,
            color = ColorProducer { colorAnim.value },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = scaleAnim.value
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
        )
    }
}

private fun resolveLyricLineColor(
    index: Int,
    activeIndex: Int,
    colors: AppColors
): Color {
    return when {
        activeIndex == index -> colors.textPrimary
        activeIndex >= 0 && abs(index - activeIndex) == 1 -> colors.textSecondary.copy(alpha = 0.62f)
        else -> colors.textSecondary.copy(alpha = 0.38f)
    }
}

internal fun applyLyricsOffset(positionMs: Long, syncOffsetMs: Long): Long {
    return (positionMs + syncOffsetMs).coerceAtLeast(0L)
}

internal fun calculateLyricTapSeekPositionMs(lineTimeMs: Long, syncOffsetMs: Long): Long {
    return (lineTimeMs - syncOffsetMs).coerceAtLeast(0L)
}

private fun computeAdaptiveScrollDurationMs(
    lines: List<LyricLine>,
    activeIndex: Int
): Int {
    val currentMs = lines.getOrNull(activeIndex)?.timeMs
    var nextMs: Long? = null
    if (currentMs != null) {
        for (i in (activeIndex + 1) until lines.size) {
            val candidate = lines[i].timeMs
            if (candidate != null && candidate > currentMs) {
                nextMs = candidate
                break
            }
        }
    }
    val timeToNextLineMs = if (currentMs != null && nextMs != null) {
        nextMs - currentMs
    } else {
        AUTO_SCROLL_FALLBACK_GAP_MS
    }
    return (timeToNextLineMs * AUTO_SCROLL_DURATION_RATIO)
        .toInt()
        .coerceIn(AUTO_SCROLL_MIN_DURATION_MS, AUTO_SCROLL_MAX_DURATION_MS)
}

private suspend fun scrollToActiveLine(
    listState: LazyListState,
    targetIndex: Int,
    animate: Boolean,
    durationMs: Int
) {
    if (targetIndex < 0) return

    var layoutInfo = listState.layoutInfo
    var viewportHeight = layoutInfo.viewportSize.height
    if (viewportHeight <= 0) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
        layoutInfo = listState.layoutInfo
        viewportHeight = layoutInfo.viewportSize.height
    }

    var visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    if (visibleItem == null) {
        val firstVisibleIndex = layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
        if (!animate) {
            val directOffset = -(layoutInfo.viewportStartOffset + viewportHeight * AUTO_SCROLL_REST_FRACTION).toInt()
            listState.scrollToItem(targetIndex, directOffset)
        } else {
            val nearFraction = if (targetIndex >= firstVisibleIndex) 0.52f else 0.24f
            val nearOffset = -(layoutInfo.viewportStartOffset + viewportHeight * nearFraction).toInt()
            listState.scrollToItem(targetIndex, nearOffset)
        }
        withFrameNanos { }
        layoutInfo = listState.layoutInfo
        viewportHeight = layoutInfo.viewportSize.height
        visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    }

    if (visibleItem != null && viewportHeight > 0) {
        val targetCenter = layoutInfo.viewportStartOffset + viewportHeight * AUTO_SCROLL_REST_FRACTION
        val itemCenter = visibleItem.offset + visibleItem.size / 2f
        val delta = itemCenter - targetCenter
        if (abs(delta) > 1.5f) {
            val canMove = if (delta > 0f) listState.canScrollForward else listState.canScrollBackward
            if (canMove) {
                if (!animate) {
                    listState.scrollBy(delta)
                } else {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(
                            durationMillis = durationMs,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PlainLyricsList(
    lines: List<LyricLine>,
    colors: AppColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadingVerticalEdges(edgeHeight = 24.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp)
            .testTag("plain_lyrics_list"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val textColor = colors.textPrimary.copy(alpha = 0.90f)
        lines.forEach { line ->
            if (line.text.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                Text(
                    text = line.text,
                    fontFamily = LyricsFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    letterSpacing = 0.1.sp,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SyncOffsetDialog(
    syncOffsetMs: Long,
    onOffsetChange: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors
    val formattedOffset = remember(syncOffsetMs) {
        if (syncOffsetMs == 0L) {
            "0.0 s"
        } else {
            String.format(Locale.US, "%+.1f s", syncOffsetMs / 1000f)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.lyrics_sync_offset),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
        },
        text = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val canDecrease = syncOffsetMs > -10_000L
                val canIncrease = syncOffsetMs < 10_000L

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .clickable(
                            enabled = canDecrease,
                            role = Role.Button,
                            onClick = { onOffsetChange((syncOffsetMs - 500L).coerceAtLeast(-10_000L)) }
                        )
                        .testTag("lyrics_offset_decrease"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = stringResource(R.string.lyrics_sync_offset_decrease),
                        tint = if (canDecrease) colors.textPrimary else colors.disabled,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = formattedOffset,
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.testTag("lyrics_offset_value")
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceMuted)
                        .clickable(
                            enabled = canIncrease,
                            role = Role.Button,
                            onClick = { onOffsetChange((syncOffsetMs + 500L).coerceAtMost(10_000L)) }
                        )
                        .testTag("lyrics_offset_increase"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.lyrics_sync_offset_increase),
                        tint = if (canIncrease) colors.textPrimary else colors.disabled,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        confirmButton = {
            AppPrimaryButton(
                text = stringResource(R.string.close),
                onClick = onDismiss
            )
        },
        dismissButton = {
            val canReset = syncOffsetMs != LYRICS_DEFAULT_OFFSET_MS
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(AppTokens.RadiusPill))
                    .background(colors.surfaceMuted.copy(alpha = if (canReset) 1f else 0.5f))
                    .then(
                        if (canReset) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClick = { onOffsetChange(LYRICS_DEFAULT_OFFSET_MS) }
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("lyrics_offset_reset"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.lyrics_sync_offset_reset),
                    color = if (canReset) colors.textPrimary else colors.disabled,
                    style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    )
}

private fun Modifier.fadingVerticalEdges(edgeHeight: Dp = 24.dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edgePx = edgeHeight.toPx()
        if (edgePx > 0f && size.height > edgePx * 2f) {
            val topFraction = edgePx / size.height
            val bottomFraction = 1f - topFraction
            val fadeBrush = Brush.verticalGradient(
                0f to Color.Transparent,
                topFraction to Color.Black,
                bottomFraction to Color.Black,
                1f to Color.Transparent
            )
            drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
        }
    }
