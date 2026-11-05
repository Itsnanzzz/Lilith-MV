package com.example.ui.video

import android.app.Application
import android.media.AudioManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.LilithApplication
import com.example.R
import com.example.data.MediaRepository
import com.example.data.SettingsDataStore
import com.example.data.db.VideoPlaybackPositionEntity
import com.example.data.model.AspectRatioMode
import com.example.data.model.SubtitleSettings
import com.example.data.model.SubtitleTrackInfo
import com.example.data.model.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioTrackChoice(
    val groupIndex: Int,
    val trackIndex: Int,
    val name: String,
    val isSelected: Boolean
)

data class VideoPlayerUiState(
    val currentVideo: VideoItem? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val controlsVisible: Boolean = true,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val isLooping: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val audioTracks: List<AudioTrackChoice> = emptyList(),
    val subtitleTracks: List<SubtitleTrackInfo> = emptyList(),
    val selectedSubtitleId: String? = null,
    val subtitleSettings: SubtitleSettings = SubtitleSettings(),
    val resumePositionMs: Long? = null,
    val isControlsLocked: Boolean = false,
    val isMuted: Boolean = false,
    val isAudioOnly: Boolean = false,
    val errorMessage: String? = null,
    // Gesture feedback overlays
    val volumeLevel: Float? = null, // 0.0 - 1.0, null if hidden
    val brightnessLevel: Float? = null, // 0.0 - 1.0, null if hidden
    val seekDelta: Int? = null // +/- seconds for double tap
)

@OptIn(UnstableApi::class)
class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val database = (application as LilithApplication).database
    private val settingsDataStore = SettingsDataStore(application)
    private val audioManager = application.getSystemService(Application.AUDIO_SERVICE) as AudioManager

    val trackSelector = DefaultTrackSelector(application)
    var player: ExoPlayer? = null
        private set

    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private var autoHideJob: Job? = null
    private var progressJob: Job? = null
    private var gestureFeedbackJob: Job? = null

    init {
        viewModelScope.launch {
            val subSettings = settingsDataStore.subtitleSettingsFlow.first()
            _uiState.value = _uiState.value.copy(subtitleSettings = subSettings)
        }
    }

    fun getOrCreatePlayer(): ExoPlayer {
        player?.let { return it }
        val app = getApplication<Application>()
        val newPlayer = ExoPlayer.Builder(app)
            .setTrackSelector(trackSelector)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                        if (isPlaying) {
                            scheduleControlsAutoHide()
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        extractAvailableTracks(tracks)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val app = getApplication<Application>()
                        val message = app.getString(R.string.error_unsupported_codec) +
                                "\n(" + (error.localizedMessage ?: app.getString(R.string.error_codec_fallback)) + ")"
                        _uiState.value = _uiState.value.copy(errorMessage = message)
                    }
                })
            }
        player = newPlayer
        startProgressLoop()
        return newPlayer
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                player?.let { p ->
                    if (p.isPlaying) {
                        val pos = p.currentPosition.coerceAtLeast(0L)
                        val dur = p.duration.coerceAtLeast(0L)
                        _currentPositionMs.value = pos
                        _uiState.value = _uiState.value.copy(
                            currentPositionMs = pos,
                            durationMs = dur
                        )
                        // Save last position every 5s if meaningful
                        val video = _uiState.value.currentVideo
                        if (video != null && pos > 5000 && (dur - pos) > 10000) {
                            database.videoProgressDao().savePosition(
                                VideoPlaybackPositionEntity(
                                    videoUri = video.uri,
                                    title = video.title,
                                    positionMs = pos,
                                    durationMs = dur
                                )
                            )
                        }
                    }
                }
                delay(400)
            }
        }
    }

    fun loadVideo(video: VideoItem) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                currentVideo = video,
                errorMessage = null,
                controlsVisible = true
            )

            // Check if there was a saved playback position
            val savedPos = database.videoProgressDao().getPosition(video.uri)
            if (savedPos != null && savedPos.positionMs > 5000 && (savedPos.durationMs - savedPos.positionMs) > 10000) {
                _uiState.value = _uiState.value.copy(resumePositionMs = savedPos.positionMs)
            } else {
                _uiState.value = _uiState.value.copy(resumePositionMs = null)
            }

            // Detect external subtitles in same folder
            val externalSubs = repository.findMatchingSubtitles(video.path)

            prepareMediaItem(video, externalSubs, startPosition = 0L)
        }
    }

    private fun prepareMediaItem(video: VideoItem, externalSubs: List<SubtitleTrackInfo>, startPosition: Long) {
        val p = getOrCreatePlayer()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(video.uri))
            .setMediaId(video.uri)

        // Attach external subtitles as subtitle configurations
        if (externalSubs.isNotEmpty()) {
            val subtitleConfigs = externalSubs.mapNotNull { sub ->
                sub.uri?.let { subUri ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUri))
                        .setMimeType(sub.mimeType ?: MimeTypes.APPLICATION_SUBRIP)
                        .setLanguage(sub.language)
                        .setLabel(sub.label)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                }
            }
            mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)
        }

        val item = mediaItemBuilder.build()
        p.setMediaItem(item, startPosition)
        p.prepare()
        p.play()
    }

    fun resumeFromSavedPosition() {
        val pos = _uiState.value.resumePositionMs ?: return
        player?.seekTo(pos)
        player?.play()
        _uiState.value = _uiState.value.copy(resumePositionMs = null)
    }

    fun dismissResumePrompt() {
        _uiState.value = _uiState.value.copy(resumePositionMs = null)
    }

    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
                _uiState.value = _uiState.value.copy(controlsVisible = true)
                scheduleControlsAutoHide()
            } else {
                it.play()
                scheduleControlsAutoHide()
            }
        }
    }

    companion object {
        const val SEEK_DELTA_SECONDS = 10
        const val SEEK_DELTA_MS = 10_000L
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
        _currentPositionMs.value = positionMs
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
    }

    fun seekRelative(deltaMs: Long) {
        player?.let { p ->
            val current = p.currentPosition
            val duration = if (p.duration > 0) p.duration else _uiState.value.durationMs
            val newPos = if (duration > 0) {
                (current + deltaMs).coerceIn(0L, duration)
            } else {
                (current + deltaMs).coerceAtLeast(0L)
            }
            p.seekTo(newPos)
            _currentPositionMs.value = newPos
            _uiState.value = _uiState.value.copy(currentPositionMs = newPos)
        }
    }

    fun doubleTapSeek(deltaSeconds: Int) {
        player?.let { p ->
            val current = p.currentPosition
            val newPos = (current + deltaSeconds * 1000L).coerceIn(0L, p.duration.coerceAtLeast(0L))
            p.seekTo(newPos)
            _currentPositionMs.value = newPos
            _uiState.value = _uiState.value.copy(
                currentPositionMs = newPos,
                seekDelta = deltaSeconds
            )
            triggerFeedbackDismiss()
        }
    }

    fun adjustVolume(delta: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val newVol = (curVol + (delta * maxVol).toInt()).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        _uiState.value = _uiState.value.copy(volumeLevel = newVol.toFloat() / maxVol)
        triggerFeedbackDismiss()
    }

    fun setBrightnessLevel(level: Float) {
        _uiState.value = _uiState.value.copy(brightnessLevel = level.coerceIn(0.01f, 1.0f))
        triggerFeedbackDismiss()
    }

    private fun triggerFeedbackDismiss() {
        gestureFeedbackJob?.cancel()
        gestureFeedbackJob = viewModelScope.launch {
            delay(1200)
            _uiState.value = _uiState.value.copy(
                volumeLevel = null,
                brightnessLevel = null,
                seekDelta = null
            )
        }
    }

    fun toggleControlsVisibility() {
        val newVisible = !_uiState.value.controlsVisible
        _uiState.value = _uiState.value.copy(controlsVisible = newVisible)
        if (newVisible && _uiState.value.isPlaying) {
            scheduleControlsAutoHide()
        } else {
            autoHideJob?.cancel()
        }
    }

    fun cancelControlsAutoHide() {
        autoHideJob?.cancel()
    }

    fun scheduleControlsAutoHide(delayMs: Long = 3000L) {
        autoHideJob?.cancel()
        autoHideJob = viewModelScope.launch {
            delay(delayMs)
            _uiState.value = _uiState.value.copy(controlsVisible = false)
        }
    }

    fun cycleAspectRatio() {
        val next = when (_uiState.value.aspectRatioMode) {
            AspectRatioMode.FIT -> AspectRatioMode.FILL
            AspectRatioMode.FILL -> AspectRatioMode.ZOOM
            AspectRatioMode.ZOOM -> AspectRatioMode.FIT
        }
        _uiState.value = _uiState.value.copy(aspectRatioMode = next)
    }

    fun toggleTouchLock() {
        val newLock = !_uiState.value.isControlsLocked
        _uiState.value = _uiState.value.copy(
            isControlsLocked = newLock,
            controlsVisible = !newLock
        )
    }

    fun setTouchLocked(locked: Boolean) {
        _uiState.value = _uiState.value.copy(
            isControlsLocked = locked,
            controlsVisible = !locked
        )
    }

    fun toggleMute() {
        val newMute = !_uiState.value.isMuted
        player?.volume = if (newMute) 0f else 1f
        _uiState.value = _uiState.value.copy(isMuted = newMute)
    }

    fun toggleAudioOnly() {
        val newAudioOnly = !_uiState.value.isAudioOnly
        _uiState.value = _uiState.value.copy(isAudioOnly = newAudioOnly)
    }

    fun toggleLooping() {
        val newLoop = !_uiState.value.isLooping
        player?.repeatMode = if (newLoop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        _uiState.value = _uiState.value.copy(isLooping = newLoop)
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    private fun extractAvailableTracks(tracks: Tracks) {
        val audioList = mutableListOf<AudioTrackChoice>()
        val subList = mutableListOf<SubtitleTrackInfo>()
        var selectedSubId: String? = null

        for (g in 0 until tracks.groups.size) {
            val group = tracks.groups[g]
            val trackGroup = group.mediaTrackGroup

            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (t in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(t)
                    val defaultAudioLabel = getApplication<Application>().getString(R.string.default_audio_track)
                    val lang = format.language?.uppercase() ?: "$defaultAudioLabel ${t + 1}"
                    val label = format.label ?: lang
                    audioList.add(
                        AudioTrackChoice(
                            groupIndex = g,
                            trackIndex = t,
                            name = label,
                            isSelected = group.isTrackSelected(t)
                        )
                    )
                }
            } else if (group.type == C.TRACK_TYPE_TEXT) {
                for (t in 0 until trackGroup.length) {
                    val format = trackGroup.getFormat(t)
                    val subId = "sub_${g}_$t"
                    val isSelected = group.isTrackSelected(t)
                    if (isSelected) selectedSubId = subId
                    val defaultSubLabel = getApplication<Application>().getString(R.string.subtitle_title)
                    val lang = format.language?.uppercase() ?: "$defaultSubLabel ${t + 1}"
                    val label = format.label ?: lang
                    subList.add(
                        SubtitleTrackInfo(
                            id = subId,
                            label = label,
                            language = format.language,
                            mimeType = format.sampleMimeType,
                            isSelected = isSelected
                        )
                    )
                }
            }
        }

        _uiState.value = _uiState.value.copy(
            audioTracks = audioList,
            subtitleTracks = subList,
            selectedSubtitleId = selectedSubId
        )
    }

    fun selectAudioTrack(choice: AudioTrackChoice) {
        val p = player ?: return
        val tracks = p.currentTracks
        if (choice.groupIndex in 0 until tracks.groups.size) {
            val group = tracks.groups[choice.groupIndex]
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, listOf(choice.trackIndex))
                    )
            )
        }
    }

    fun selectSubtitleTrack(subId: String?) {
        val p = player ?: return
        if (subId == null) {
            // Turn off subtitles
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
            _uiState.value = _uiState.value.copy(selectedSubtitleId = null)
        } else {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            )
            val tracks = p.currentTracks
            for (g in 0 until tracks.groups.size) {
                val group = tracks.groups[g]
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (t in 0 until group.length) {
                        if ("sub_${g}_$t" == subId) {
                            trackSelector.setParameters(
                                trackSelector.buildUponParameters()
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, listOf(t))
                                    )
                            )
                            _uiState.value = _uiState.value.copy(selectedSubtitleId = subId)
                            return
                        }
                    }
                }
            }
        }
    }

    fun addExternalSubtitle(uri: Uri, fileName: String) {
        val currentVideo = _uiState.value.currentVideo ?: return
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val mime = when (ext) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt" -> MimeTypes.TEXT_VTT
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }

        val subInfo = SubtitleTrackInfo(
            id = uri.toString(),
            label = getApplication<Application>().getString(R.string.subtitle_manual_label, fileName),
            language = "manual",
            uri = uri.toString(),
            mimeType = mime,
            isExternal = true
        )

        // Reload video with external subtitle configuration while preserving current position
        val curPos = player?.currentPosition ?: 0L
        prepareMediaItem(currentVideo, listOf(subInfo), curPos)
    }

    fun updateSubtitleSettings(newSettings: SubtitleSettings) {
        _uiState.value = _uiState.value.copy(subtitleSettings = newSettings)
        viewModelScope.launch {
            settingsDataStore.updateSubtitleSettings(newSettings)
        }
    }

    fun updateSubtitleDelay(deltaSeconds: Float) {
        val current = _uiState.value.subtitleSettings
        val newDelay = (current.delayMs + (deltaSeconds * 1000).toLong()).coerceIn(-10000L, 10000L)
        _uiState.value = _uiState.value.copy(
            subtitleSettings = current.copy(delayMs = newDelay)
        )
    }

    fun releasePlayer() {
        progressJob?.cancel()
        autoHideJob?.cancel()
        gestureFeedbackJob?.cancel()
        player?.release()
        player = null
        _uiState.value = VideoPlayerUiState()
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}
