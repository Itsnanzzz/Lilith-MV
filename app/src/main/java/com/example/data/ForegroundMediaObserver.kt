package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lifecycle-aware background media detector that observes MediaStore changes and
 * checks for newly added audio or video files ONLY while the application is open
 * (between Lifecycle ON_START and ON_STOP).
 */
class ForegroundMediaObserver(
    private val context: Context,
    private val repository: MediaRepository,
    private val getKnownAudioPaths: () -> Set<String>,
    private val getKnownVideoPaths: () -> Set<String>,
    private val onAudioMediaChanged: suspend () -> Unit,
    private val onVideoMediaChanged: suspend () -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val debounceMs: Long = DEFAULT_DEBOUNCE_MS
) : DefaultLifecycleObserver {

    private val _isObserving = MutableStateFlow(false)
    val isObserving: StateFlow<Boolean> = _isObserving.asStateFlow()

    private var observationJob: Job? = null
    private var hasStartedOnce = false

    @OptIn(FlowPreview::class)
    override fun onStart(owner: LifecycleOwner) {
        stopObserving()

        val job = SupervisorJob()
        observationJob = job
        val scope = CoroutineScope(ioDispatcher + job)
        _isObserving.value = true

        scope.launch {
            repository.observeAudioStoreChanges()
                .debounce(debounceMs)
                .collect {
                    if (hasAudioReadPermission(context)) {
                        onAudioMediaChanged()
                    }
                }
        }

        scope.launch {
            repository.observeVideoStoreChanges()
                .debounce(debounceMs)
                .collect {
                    if (hasVideoReadPermission(context)) {
                        onVideoMediaChanged()
                    }
                }
        }

        val returningToForeground = hasStartedOnce
        hasStartedOnce = true

        scope.launch {
            if (returningToForeground) {
                runForegroundCheck()
            }
            while (isActive) {
                delay(pollIntervalMs)
                runForegroundCheck()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        stopObserving()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        stopObserving()
    }

    fun triggerImmediateCheckIfOpen() {
        val job = observationJob ?: return
        if (!job.isActive || !_isObserving.value) return
        CoroutineScope(ioDispatcher + job).launch {
            runForegroundCheck()
        }
    }

    private suspend fun runForegroundCheck() {
        val canReadAudio = hasAudioReadPermission(context)
        val canReadVideo = hasVideoReadPermission(context)
        if (!canReadAudio && !canReadVideo) return

        repository.scanUnindexedPublicMedia(
            scanAudio = canReadAudio,
            scanVideo = canReadVideo,
            knownAudioPaths = getKnownAudioPaths(),
            knownVideoPaths = getKnownVideoPaths()
        )
        if (canReadAudio) {
            onAudioMediaChanged()
        }
        if (canReadVideo) {
            onVideoMediaChanged()
        }
    }

    private fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
        _isObserving.value = false
    }

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 5_000L
        const val DEFAULT_DEBOUNCE_MS = 400L

        fun hasAudioReadPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }

        fun hasVideoReadPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
