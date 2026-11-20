package com.example.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.AppAccent
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.LilithTheme
import com.example.ui.theme.rememberIsAnimationDisabled
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Tuning constants for all wavy music progress tracks (Now Playing, Mini Player, Dynamic Island).
 */
object WavyTrackDefaults {
    /** Full wavelength scroll period in ms (2000ms is faster, 3200ms is slower). */
    const val WAVE_PERIOD_MS: Int = 2600

    /** Play/pause amplitude ease duration in ms. */
    const val AMPLITUDE_TRANSITION_MS: Int = 450

    /** Initial wave appear ramp duration in ms. */
    const val APPEAR_RAMP_MS: Int = 600

    /** Linear tween duration matching the 500ms player position tick interval. */
    const val PROGRESS_TICK_MS: Int = 500

    /** Short tween duration on discrete tap-to-seek jumps. */
    const val SEEK_TWEEN_MS: Int = 150

    val TAPER_LENGTH_DP: Dp = 10.dp
    val GAP_AFTER_WAVE_DP: Dp = 4.dp
    val END_DOT_SIZE_DP: Dp = 4.dp
    val CURVE_STEP_DP: Dp = 2.dp
}

/**
 * Shared wavy music progress track drawn via Canvas/drawWithCache:
 * - Played part: tapered sine wave with round caps/joins ending cleanly at progress center.
 * - Remaining part: flat thin line with round caps after a 4dp gap, plus a 4dp end dot.
 * - Animated ONLY while playing AND visible AND lifecycle resumed (phase stops cleanly when paused/hidden).
 */
@Composable
fun WavyTrack(
    progress: Float,
    playing: Boolean,
    color: Color,
    trackColor: Color,
    strokeWidth: Dp,
    amplitude: Dp,
    wavelength: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    freezePhase: Boolean = false,
    progressDurationMs: Int = WavyTrackDefaults.PROGRESS_TICK_MS,
    isSeeking: Boolean = false,
    visible: Boolean = true,
    stateDescriptionText: String = "",
    exposeProgressSemantics: Boolean = true
) {
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val inspectionMode = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isLifecycleResumed by remember(lifecycleOwner, inspectionMode) {
        mutableStateOf(
            inspectionMode || lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        )
    }

    DisposableEffect(lifecycleOwner, inspectionMode) {
        if (inspectionMode) {
            isLifecycleResumed = true
            onDispose {}
        } else {
            isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            val observer = LifecycleEventObserver { _, _ ->
                isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    val targetProgress = progress.coerceIn(0f, 1f)
    val progressAnimatable = remember { Animatable(targetProgress) }
    LaunchedEffect(targetProgress, isSeeking, isAnimationDisabled, progressDurationMs) {
        if (isAnimationDisabled || isSeeking || inspectionMode) {
            progressAnimatable.snapTo(targetProgress)
        } else {
            val delta = abs(targetProgress - progressAnimatable.value)
            val duration = if (delta > 0.05f) {
                WavyTrackDefaults.SEEK_TWEEN_MS
            } else {
                progressDurationMs.coerceAtLeast(0)
            }
            if (duration == 0) {
                progressAnimatable.snapTo(targetProgress)
            } else {
                progressAnimatable.animateTo(
                    targetValue = targetProgress,
                    animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                )
            }
        }
    }
    val progressState: State<Float> = progressAnimatable.asState()

    val isVisible = visible && isLifecycleResumed
    val shouldShowWave = playing && isVisible
    val amplitudeAnimatable = remember {
        Animatable(
            if (isAnimationDisabled || inspectionMode) {
                if (playing) 1f else 0f
            } else {
                0f
            }
        )
    }
    var isFirstAmplitudeRun by remember { mutableStateOf(true) }

    LaunchedEffect(shouldShowWave, isAnimationDisabled, inspectionMode, playing) {
        val targetAmplitude = if (isAnimationDisabled || inspectionMode) {
            if (playing) 1f else 0f
        } else {
            if (shouldShowWave) 1f else 0f
        }
        if (isAnimationDisabled || inspectionMode) {
            amplitudeAnimatable.snapTo(targetAmplitude)
            isFirstAmplitudeRun = false
        } else {
            val duration = if (isFirstAmplitudeRun && targetAmplitude > 0f) {
                WavyTrackDefaults.APPEAR_RAMP_MS
            } else {
                WavyTrackDefaults.AMPLITUDE_TRANSITION_MS
            }
            isFirstAmplitudeRun = false
            amplitudeAnimatable.animateTo(
                targetValue = targetAmplitude,
                animationSpec = tween(durationMillis = duration, easing = FastOutSlowInEasing)
            )
        }
    }
    val amplitudeState: State<Float> = amplitudeAnimatable.asState()

    val phaseState = remember { mutableFloatStateOf(0f) }
    val shouldScrollPhase = playing && isVisible && !freezePhase && !isAnimationDisabled && !inspectionMode
    LaunchedEffect(shouldScrollPhase) {
        if (!shouldScrollPhase) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        val twoPi = (2.0 * PI).toFloat()
        val radPerNano = twoPi / (WavyTrackDefaults.WAVE_PERIOD_MS * 1_000_000f)
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val dtNanos = (nowNanos - lastNanos).coerceIn(0L, 100_000_000L)
            lastNanos = nowNanos
            phaseState.floatValue = (phaseState.floatValue + dtNanos * radPerNano) % twoPi
        }
    }

    val wavePath = remember { Path() }

    val semanticsModifier = if (exposeProgressSemantics) {
        Modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(current = targetProgress, range = 0f..1f)
            if (stateDescriptionText.isNotEmpty()) {
                stateDescription = stateDescriptionText
            }
        }
    } else {
        Modifier
    }

    Spacer(
        modifier = modifier
            .height(height)
            .then(semanticsModifier)
            .drawWithCache {
                val strokePx = strokeWidth.toPx().coerceAtLeast(1f)
                val dotRadiusPx = (WavyTrackDefaults.END_DOT_SIZE_DP.toPx() / 2f).coerceAtLeast(1f)
                val maxFitAmpPx = ((size.height - strokePx) / 2f).coerceAtLeast(0f)
                val maxAmpPx = minOf(amplitude.toPx().coerceAtLeast(0f), maxFitAmpPx)
                val wavelengthPx = wavelength.toPx().coerceAtLeast(4f)
                val taperWidthPx = WavyTrackDefaults.TAPER_LENGTH_DP.toPx().coerceAtLeast(1f)
                val gapPx = WavyTrackDefaults.GAP_AFTER_WAVE_DP.toPx().coerceAtLeast(0f)
                val stepPx = WavyTrackDefaults.CURVE_STEP_DP.toPx().coerceIn(1.5f, wavelengthPx / 4f)

                val waveStroke = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )

                val capInset = maxOf(strokePx / 2f, dotRadiusPx)
                val startX = capInset
                val endX = (size.width - capInset).coerceAtLeast(startX)
                val usableWidth = (endX - startX).coerceAtLeast(0f)
                val centerY = size.height / 2f
                val twoPiOverLambda = ((2.0 * PI) / wavelengthPx.toDouble()).toFloat()

                onDrawBehind {
                    val clampedProgress = progressState.value.coerceIn(0f, 1f)
                    val amp = maxAmpPx * amplitudeState.value.coerceIn(0f, 1f)
                    val phase = phaseState.floatValue

                    val splitX = startX + usableWidth * clampedProgress
                    val playedWidth = (splitX - startX).coerceAtLeast(0f)
                    val hasPlayedPart = clampedProgress > 0f && playedWidth > 0.5f

                    // Remaining flat track with 4dp visual gap after wave end cap
                    val trackStartX = if (hasPlayedPart) {
                        splitX + gapPx + strokePx
                    } else {
                        startX
                    }
                    if (clampedProgress < 1f && (endX - trackStartX) > 0.5f) {
                        drawLine(
                            color = trackColor,
                            start = Offset(trackStartX, centerY),
                            end = Offset(endX, centerY),
                            strokeWidth = strokePx,
                            cap = StrokeCap.Round
                        )
                    }

                    // Far-right 4dp dot marker when remaining space exists
                    if (clampedProgress < 0.995f && usableWidth > 0f && (endX - splitX) > strokePx / 2f) {
                        drawCircle(
                            color = trackColor,
                            radius = dotRadiusPx,
                            center = Offset(endX, centerY)
                        )
                    }

                    // Played tapered sine wave path
                    if (hasPlayedPart) {
                        if (amp <= 0.05f) {
                            drawLine(
                                color = color,
                                start = Offset(startX, centerY),
                                end = Offset(splitX, centerY),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                        } else {
                            wavePath.rewind()

                            fun sampleY(xPos: Float): Float {
                                val distFromEnd = (splitX - xPos).coerceAtLeast(0f)
                                val taper = if (distFromEnd >= taperWidthPx) {
                                    1f
                                } else {
                                    val t = (distFromEnd / taperWidthPx).coerceIn(0f, 1f)
                                    t * t * (3f - 2f * t)
                                }
                                return centerY + amp * taper * sin(twoPiOverLambda * (xPos - startX) - phase)
                            }

                            wavePath.moveTo(startX, sampleY(startX))
                            var x = startX + stepPx
                            while (x < splitX) {
                                wavePath.lineTo(x, sampleY(x))
                                x += stepPx
                            }
                            wavePath.lineTo(splitX, centerY)
                            drawPath(
                                path = wavePath,
                                color = color,
                                style = waveStroke
                            )
                        }
                    }
                }
            }
    )
}

/**
 * Interactive Now Playing seek bar built on WavyTrack:
 * - 48dp invisible touch target over a 24dp tall wavy track (4dp stroke, 3dp amplitude, 26dp wavelength).
 * - Draggable + tap-to-seek; shows a 4dp x 20dp rounded vertical handle while dragging and freezes phase
 *   (keeping amplitude) so the wave does not crawl under the finger.
 * - Exposes slider semantics + stateDescription without liveRegion announcements.
 */
@Composable
fun WavySeekBar(
    value: Float,
    playing: Boolean,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    visible: Boolean = true,
    stateDescriptionText: String = "",
    testTag: String? = null,
    previewDragging: Boolean = false
) {
    val colors = AppThemeTokens.colors
    val density = LocalDensity.current

    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }

    LaunchedEffect(enabled) {
        if (!enabled) isDragging = false
    }

    val showDragging = isDragging || previewDragging
    val activeFraction = if (isDragging) dragFraction else value.coerceIn(0f, 1f)

    val strokeDp = 4.dp
    val capInsetDp = maxOf(strokeDp / 2f, WavyTrackDefaults.END_DOT_SIZE_DP / 2f)
    val capInsetPx = remember(density) { with(density) { capInsetDp.toPx() } }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = ProgressBarRangeInfo(current = activeFraction, range = 0f..1f)
                if (stateDescriptionText.isNotEmpty()) {
                    stateDescription = stateDescriptionText
                }
                if (enabled) {
                    setProgress { targetValue ->
                        val clamped = targetValue.coerceIn(0f, 1f)
                        latestOnValueChange(clamped)
                        latestOnValueChangeFinished?.invoke()
                        true
                    }
                }
            }
            .pointerInput(enabled, capInsetPx) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val usableWidthPx = (size.width - capInsetPx * 2f).coerceAtLeast(1f)
                        isDragging = true
                        dragFraction = ((offset.x - capInsetPx) / usableWidthPx).coerceIn(0f, 1f)
                        latestOnValueChange(dragFraction)
                    },
                    onDragEnd = {
                        latestOnValueChange(dragFraction)
                        latestOnValueChangeFinished?.invoke()
                        isDragging = false
                    },
                    onDragCancel = {
                        latestOnValueChangeFinished?.invoke()
                        isDragging = false
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val usableWidthPx = (size.width - capInsetPx * 2f).coerceAtLeast(1f)
                        dragFraction = ((change.position.x - capInsetPx) / usableWidthPx).coerceIn(0f, 1f)
                        latestOnValueChange(dragFraction)
                    }
                )
            }
            .pointerInput(enabled, capInsetPx) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val usableWidthPx = (size.width - capInsetPx * 2f).coerceAtLeast(1f)
                        val target = ((offset.x - capInsetPx) / usableWidthPx).coerceIn(0f, 1f)
                        latestOnValueChange(target)
                        latestOnValueChangeFinished?.invoke()
                    }
                )
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        WavyTrack(
            progress = activeFraction,
            playing = playing,
            color = colors.accentText,
            trackColor = colors.accentText.copy(alpha = 0.28f),
            strokeWidth = strokeDp,
            amplitude = 3.dp,
            wavelength = 26.dp,
            height = 24.dp,
            freezePhase = showDragging,
            isSeeking = isDragging,
            visible = visible,
            exposeProgressSemantics = false,
            modifier = Modifier.fillMaxWidth()
        )

        if (showDragging) {
            val usableWidthDp = (maxWidth - capInsetDp * 2f).coerceAtLeast(0.dp)
            val handleCenterX = capInsetDp + usableWidthDp * activeFraction
            val handleX = (handleCenterX - 2.dp).coerceIn(0.dp, (maxWidth - 4.dp).coerceAtLeast(0.dp))
            Box(
                modifier = Modifier
                    .offset(x = handleX)
                    .size(width = 4.dp, height = 20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accentText)
            )
        }
    }
}

// =========================================================================
// PREVIEWS
// =========================================================================

@Preview(name = "WavyTrack - Light Playing 40%", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun WavyTrackLightPlaying40Preview() {
    LilithTheme(darkTheme = false, appAccent = AppAccent.CYAN) {
        val colors = AppThemeTokens.colors
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(20.dp)
        ) {
            WavyTrack(
                progress = 0.40f,
                playing = true,
                color = colors.accentText,
                trackColor = colors.accentText.copy(alpha = 0.28f),
                strokeWidth = 4.dp,
                amplitude = 3.dp,
                wavelength = 26.dp,
                height = 24.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "WavyTrack - Dark Paused 40%", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
private fun WavyTrackDarkPaused40Preview() {
    LilithTheme(darkTheme = true, appAccent = AppAccent.CYAN) {
        val colors = AppThemeTokens.colors
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(20.dp)
        ) {
            WavyTrack(
                progress = 0.40f,
                playing = false,
                color = colors.accentText,
                trackColor = colors.accentText.copy(alpha = 0.28f),
                strokeWidth = 4.dp,
                amplitude = 3.dp,
                wavelength = 26.dp,
                height = 24.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "WavyTrack - Light Playing 0%", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun WavyTrackLightPlaying0Preview() {
    LilithTheme(darkTheme = false, appAccent = AppAccent.BLUE) {
        val colors = AppThemeTokens.colors
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(20.dp)
        ) {
            WavyTrack(
                progress = 0f,
                playing = true,
                color = colors.accentText,
                trackColor = colors.accentText.copy(alpha = 0.28f),
                strokeWidth = 4.dp,
                amplitude = 3.dp,
                wavelength = 26.dp,
                height = 24.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "WavyTrack - Dark Playing 100%", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
private fun WavyTrackDarkPlaying100Preview() {
    LilithTheme(darkTheme = true, appAccent = AppAccent.GREEN) {
        val colors = AppThemeTokens.colors
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(20.dp)
        ) {
            WavyTrack(
                progress = 1f,
                playing = true,
                color = colors.accentText,
                trackColor = colors.accentText.copy(alpha = 0.28f),
                strokeWidth = 4.dp,
                amplitude = 3.dp,
                wavelength = 26.dp,
                height = 24.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview(name = "WavySeekBar - Light Dragging 40%", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun WavySeekBarLightDraggingPreview() {
    LilithTheme(darkTheme = false, appAccent = AppAccent.CYAN) {
        val colors = AppThemeTokens.colors
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            WavySeekBar(
                value = 0.40f,
                playing = true,
                onValueChange = {},
                stateDescriptionText = "1:28 of 4:46",
                previewDragging = true
            )
        }
    }
}

@Preview(name = "WavySeekBar - Dark Rest 40%", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
private fun WavySeekBarDarkRestPreview() {
    LilithTheme(darkTheme = true, appAccent = AppAccent.YELLOW) {
        val colors = AppThemeTokens.colors
        Box(
            modifier = Modifier
                .background(colors.background)
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            WavySeekBar(
                value = 0.40f,
                playing = true,
                onValueChange = {},
                stateDescriptionText = "1:28 of 4:46"
            )
        }
    }
}
