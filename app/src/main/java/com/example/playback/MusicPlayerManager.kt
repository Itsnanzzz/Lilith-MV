package com.example.playback

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.R
import com.example.data.MusicSessionDataStore
import com.example.data.MusicSessionSnapshot
import com.example.data.model.AudioFormatInfo
import com.example.data.model.AudioTrack
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections

@OptIn(UnstableApi::class)
class MusicPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    var mediaController: MediaController? = null
        private set

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _queue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val queue: StateFlow<List<AudioTrack>> = _queue.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _formatInfo = MutableStateFlow(AudioFormatInfo())
    val formatInfo: StateFlow<AudioFormatInfo> = _formatInfo.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow(0)
    val sleepTimerRemainingSeconds: StateFlow<Int> = _sleepTimerRemainingSeconds.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var progressPollingJob: Job? = null

    private val sessionDataStore = MusicSessionDataStore(context)
    private var lastSavedPositionMs = -1L
    private var lastSavedTime = 0L

    private data class PendingRestore(
        val queue: List<AudioTrack>,
        val startIndex: Int,
        val startPositionMs: Long,
        val repeatMode: Int,
        val shuffleEnabled: Boolean
    )
    private var pendingRestore: PendingRestore? = null

    private data class PendingPlay(
        val tracks: List<AudioTrack>,
        val startIndex: Int
    )
    private var pendingPlay: PendingPlay? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupPlayerListener()
                startProgressPolling()
                val pp = pendingPlay
                if (pp != null) {
                    pendingPlay = null
                    pendingRestore = null
                    playTrackList(pp.tracks, pp.startIndex)
                } else {
                    pendingRestore?.let { pr ->
                        mediaController?.let { ctrl ->
                            applyRestoredSessionToController(ctrl, pr.queue, pr.startIndex, pr.startPositionMs, pr.repeatMode, pr.shuffleEnabled)
                        }
                        pendingRestore = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        val controller = mediaController ?: return
        controller.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                if (!isPlaying) {
                    saveSessionNow()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                updateCurrentTrackFromMediaItem(mediaItem)
                saveSessionNow()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
                saveSessionNow()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
                saveSessionNow()
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
            }
        })

        _isPlaying.value = controller.isPlaying
        _repeatMode.value = controller.repeatMode
        _shuffleEnabled.value = controller.shuffleModeEnabled
        updateCurrentTrackFromMediaItem(controller.currentMediaItem)
    }

    private fun applyRestoredSessionToController(
        controller: MediaController,
        validQueue: List<AudioTrack>,
        startIndex: Int,
        startPositionMs: Long,
        repeatMode: Int,
        shuffleEnabled: Boolean
    ) {
        val mediaItems = validQueue.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.albumArtUri?.let { Uri.parse(it) })
                .build()

            MediaItem.Builder()
                .setMediaId(track.uri)
                .setUri(track.uri)
                .setMediaMetadata(metadata)
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex, startPositionMs)
        controller.repeatMode = repeatMode
        controller.shuffleModeEnabled = shuffleEnabled
        controller.prepare()
        controller.playWhenReady = false // NEVER auto-play
    }

    private fun canPersistTrack(track: AudioTrack): Boolean {
        val uri = try {
            Uri.parse(track.uri)
        } catch (_: Exception) {
            return false
        }
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            if (uri.authority == "media") {
                return true
            }
            return try {
                context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
            } catch (_: Exception) {
                false
            }
        }
        return true
    }

    fun saveSessionNow() {
        val track = _currentTrack.value ?: return
        val queueTracks = _queue.value
        if (queueTracks.isEmpty()) return

        // Do not save non-persisted content URIs from external apps into snapshot
        if (!canPersistTrack(track)) return

        val validQueueTracks = queueTracks.filter { canPersistTrack(it) }
        if (validQueueTracks.isEmpty() || !validQueueTracks.any { it.uri == track.uri }) return

        val queueIndex = validQueueTracks.indexOfFirst { it.uri == track.uri }.coerceAtLeast(0)
        val position = _currentPositionMs.value
        val duration = if (_durationMs.value > 0L) _durationMs.value else track.durationMs
        val repeat = _repeatMode.value
        val shuffle = _shuffleEnabled.value

        val snapshot = com.example.data.MusicSessionSnapshot(
            songUri = track.uri,
            title = track.title,
            artist = track.artist,
            album = track.album,
            albumArtUri = track.albumArtUri,
            durationMs = duration,
            positionMs = position,
            queueUris = validQueueTracks.map { it.uri },
            queueIndex = queueIndex,
            repeatMode = repeat,
            shuffleEnabled = shuffle
        )
        scope.launch(Dispatchers.IO) {
            sessionDataStore.saveSession(snapshot)
        }
    }

    fun clearSavedSession() {
        scope.launch(Dispatchers.IO) {
            sessionDataStore.clearSession()
        }
    }

    suspend fun restoreSession(libraryTracks: List<AudioTrack>) {
        if (libraryTracks.isEmpty()) return
        // Do not overwrite if something is already loaded or playing
        if (_currentTrack.value != null || _queue.value.isNotEmpty()) return

        val snapshot = sessionDataStore.getSessionSnapshot() ?: return
        val libraryMap = libraryTracks.associateBy { it.uri }

        // Filter queue to valid existing tracks
        val validQueue = snapshot.queueUris.mapNotNull { libraryMap[it] }
        if (validQueue.isEmpty()) {
            clearSavedSession()
            return
        }

        // Find target track or advance to next valid
        val targetUri = snapshot.songUri
        var matchedIndex = validQueue.indexOfFirst { it.uri == targetUri }
        if (matchedIndex == -1) {
            matchedIndex = snapshot.queueIndex.coerceIn(0, validQueue.size - 1)
        }
        val targetTrack = validQueue[matchedIndex]

        // Validate target track can be read gracefully
        try {
            val parsed = Uri.parse(targetTrack.uri)
            if (parsed.scheme == ContentResolver.SCHEME_CONTENT) {
                context.contentResolver.openFileDescriptor(parsed, "r")?.close()
            }
        } catch (_: Exception) {
            clearSavedSession()
            return
        }

        val duration = if (snapshot.durationMs > 0L) snapshot.durationMs else targetTrack.durationMs
        var position = snapshot.positionMs.coerceIn(0L, duration)
        // If within last 3 seconds, restore from 0
        if (duration > 3000L && (duration - position) <= 3000L) {
            position = 0L
        }

        // Immediately update StateFlows so mini player renders at saved position
        _queue.value = validQueue
        _currentTrack.value = targetTrack
        _currentPositionMs.value = position
        _durationMs.value = duration
        _isPlaying.value = false
        _repeatMode.value = snapshot.repeatMode
        _shuffleEnabled.value = snapshot.shuffleEnabled
        _formatInfo.value = AudioFormatDetector.detectFromMime(targetTrack.mimeType)

        val controller = mediaController
        if (controller != null) {
            applyRestoredSessionToController(controller, validQueue, matchedIndex, position, snapshot.repeatMode, snapshot.shuffleEnabled)
        } else {
            pendingRestore = PendingRestore(validQueue, matchedIndex, position, snapshot.repeatMode, snapshot.shuffleEnabled)
        }
    }

    private fun updateCurrentTrackFromMediaItem(item: MediaItem?) {
        val currentMediaId = item?.mediaId ?: return
        val matched = _queue.value.find { it.uri == currentMediaId || it.id.toString() == currentMediaId }
        _currentTrack.value = matched ?: item.let {
            val meta = it.mediaMetadata
            AudioTrack(
                id = currentMediaId.hashCode().toLong(),
                uri = currentMediaId,
                title = meta.title?.toString() ?: context.getString(R.string.default_song_title),
                artist = meta.artist?.toString() ?: context.getString(R.string.unknown_artist),
                album = meta.albumTitle?.toString() ?: context.getString(R.string.default_album_title),
                durationMs = mediaController?.duration?.coerceAtLeast(0L) ?: 0L,
                albumArtUri = meta.artworkUri?.toString()
            )
        }

        // Fetch format from service if bound
        MusicPlaybackService.serviceInstance?.let { service ->
            _formatInfo.value = service.currentFormatInfo
        } ?: run {
            _formatInfo.value = AudioFormatDetector.detectFromMime(_currentTrack.value?.mimeType)
        }
    }

    private fun startProgressPolling() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPositionMs.value = controller.currentPosition.coerceAtLeast(0L)
                        _durationMs.value = controller.duration.coerceAtLeast(0L)
                        MusicPlaybackService.serviceInstance?.let { service ->
                            if (_formatInfo.value != service.currentFormatInfo && service.currentFormatInfo.codec != "UNKNOWN") {
                                _formatInfo.value = service.currentFormatInfo
                            }
                        }

                        // Debounced periodic save (~5s) while playing, skipping if position hasn't changed
                        val now = System.currentTimeMillis()
                        if (now - lastSavedTime >= 5000L && Math.abs(_currentPositionMs.value - lastSavedPositionMs) >= 1000L) {
                            lastSavedTime = now
                            lastSavedPositionMs = _currentPositionMs.value
                            saveSessionNow()
                        }
                    }
                }
                delay(500)
            }
        }
    }

    fun playTrackList(tracks: List<AudioTrack>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        _queue.value = tracks
        _currentTrack.value = tracks.getOrNull(startIndex)
        _formatInfo.value = AudioFormatDetector.detectFromMime(_currentTrack.value?.mimeType)

        val controller = mediaController
        if (controller == null) {
            pendingPlay = PendingPlay(tracks, startIndex)
            return
        }

        val mediaItems = tracks.map { track ->
            val metadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.albumArtUri?.let { Uri.parse(it) })
                .build()

            MediaItem.Builder()
                .setMediaId(track.uri)
                .setUri(track.uri)
                .setMediaMetadata(metadata)
                .build()
        }

        controller.setMediaItems(mediaItems, startIndex.coerceIn(0, tracks.size - 1), 0L)
        controller.prepare()
        controller.play()

        saveSessionNow()
    }

    fun addToQueue(track: AudioTrack) {
        val controller = mediaController ?: return
        val currentList = _queue.value.toMutableList()
        currentList.add(track)
        _queue.value = currentList

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUri?.let { Uri.parse(it) })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.uri)
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()

        controller.addMediaItem(mediaItem)
        saveSessionNow()
    }

    fun updateTrackInPlayback(oldUri: String, updatedTrack: AudioTrack) {
        val currentQueue = _queue.value
        val index = currentQueue.indexOfFirst { it.uri == oldUri }
        if (index != -1) {
            val updatedQueue = currentQueue.toMutableList()
            updatedQueue[index] = updatedTrack
            _queue.value = updatedQueue
        }

        if (_currentTrack.value?.uri == oldUri) {
            _currentTrack.value = updatedTrack
        }

        val controller = mediaController
        if (controller != null && index != -1 && index < controller.mediaItemCount) {
            val newMetadata = MediaMetadata.Builder()
                .setTitle(updatedTrack.title)
                .setArtist(updatedTrack.artist)
                .setAlbumTitle(updatedTrack.album)
                .setArtworkUri(updatedTrack.albumArtUri?.let { Uri.parse(it) })
                .build()

            val newMediaItem = MediaItem.Builder()
                .setMediaId(updatedTrack.uri)
                .setUri(updatedTrack.uri)
                .setMediaMetadata(newMetadata)
                .build()

            if (oldUri == updatedTrack.uri) {
                controller.replaceMediaItem(index, newMediaItem)
            } else {
                val isCurrent = controller.currentMediaItemIndex == index
                val currentPos = controller.currentPosition
                val isPlaying = controller.isPlaying
                controller.replaceMediaItem(index, newMediaItem)
                if (isCurrent) {
                    controller.seekTo(index, currentPos)
                    if (isPlaying) controller.play()
                }
            }
        }

        saveSessionNow()
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        if (index in 0 until _queue.value.size) {
            val currentList = _queue.value.toMutableList()
            currentList.removeAt(index)
            _queue.value = currentList
            controller.removeMediaItem(index)
            saveSessionNow()
        }
    }

    fun removeTrackFromPlayback(trackUri: String) {
        val controller = mediaController ?: return
        val currentQueue = _queue.value.toMutableList()
        val index = currentQueue.indexOfFirst { it.uri == trackUri }
        if (index == -1) return

        val isCurrent = _currentTrack.value?.uri == trackUri

        if (isCurrent) {
            if (currentQueue.size > 1) {
                val nextIndex = if (index < currentQueue.size - 1) index + 1 else index - 1
                val nextTrack = currentQueue[nextIndex]
                controller.seekTo(nextIndex, 0L)
                _currentTrack.value = nextTrack
                controller.removeMediaItem(index)
                currentQueue.removeAt(index)
                _queue.value = currentQueue
                saveSessionNow()
            } else {
                controller.stop()
                controller.clearMediaItems()
                _queue.value = emptyList()
                _currentTrack.value = null
                _isPlaying.value = false
                _currentPositionMs.value = 0L
                _durationMs.value = 0L
                clearSavedSession()
            }
        } else {
            currentQueue.removeAt(index)
            _queue.value = currentQueue
            controller.removeMediaItem(index)
            saveSessionNow()
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        val controller = mediaController ?: return
        if (from in 0 until _queue.value.size && to in 0 until _queue.value.size) {
            val currentList = _queue.value.toMutableList()
            Collections.swap(currentList, from, to)
            _queue.value = currentList
            controller.moveMediaItem(from, to)
            saveSessionNow()
        }
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        controller.clearMediaItems()
        _queue.value = emptyList()
        _currentTrack.value = null
        clearSavedSession()
    }

    fun pause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        if (controller.hasPreviousMediaItem() || controller.currentPosition > 3000) {
            if (controller.currentPosition > 3000) {
                controller.seekTo(0)
            } else {
                controller.seekToPreviousMediaItem()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
    }

    fun toggleRepeatMode() {
        val controller = mediaController ?: return
        val nextMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        _shuffleEnabled.value = newShuffle
    }

    fun setPlaybackSpeed(speed: Float) {
        val controller = mediaController ?: return
        controller.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = 0
            return
        }

        _sleepTimerRemainingSeconds.value = minutes * 60
        sleepTimerJob = scope.launch {
            while (_sleepTimerRemainingSeconds.value > 0) {
                delay(1000)
                _sleepTimerRemainingSeconds.value -= 1
            }
            // Timer expired: pause player smoothly
            mediaController?.pause()
        }
    }

    fun release() {
        progressPollingJob?.cancel()
        sleepTimerJob?.cancel()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
        mediaController = null
    }
}
