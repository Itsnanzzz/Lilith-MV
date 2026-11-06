package com.example.ui.music

import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LilithApplication
import com.example.R
import com.example.data.DeleteTrackResult
import com.example.data.MediaRepository
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistSongEntity
import com.example.data.model.AlbumItem
import com.example.data.model.AudioTrack
import com.example.data.model.FolderItem
import com.example.playback.EqualizerState
import com.example.playback.MusicPlaybackService
import com.example.playback.MusicPlayerManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class MusicTab {
    SONGS, ALBUMS, FOLDERS, PLAYLISTS
}

sealed class MusicDeleteEvent {
    data class LaunchIntentSender(val intentSender: IntentSender, val track: AudioTrack) : MusicDeleteEvent()
    data class RequestStoragePermission(val track: AudioTrack) : MusicDeleteEvent()
    data class ShowMessage(val message: String) : MusicDeleteEvent()
}

data class MusicUiState(
    val selectedTab: MusicTab = MusicTab.SONGS,
    val searchQuery: String = "",
    val allTracks: List<AudioTrack> = emptyList(),
    val filteredTracks: List<AudioTrack> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val selectedAlbum: AlbumItem? = null,
    val selectedFolder: FolderItem? = null,
    val selectedPlaylist: PlaylistEntity? = null,
    val playlistSongs: List<AudioTrack> = emptyList(),
    val isScanning: Boolean = false,
    val isNowPlayingExpanded: Boolean = false,
    val isQueueSheetVisible: Boolean = false,
    val isEqualizerDialogVisible: Boolean = false,
    val isSpeedDialogVisible: Boolean = false,
    val isSleepTimerDialogVisible: Boolean = false,
    val isCreatePlaylistDialogVisible: Boolean = false,
    val trackToDelete: AudioTrack? = null,
    val isDeletingTrack: Boolean = false
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val database = (application as LilithApplication).database
    val playerManager = MusicPlayerManager(application)

    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val _deleteEvents = MutableSharedFlow<MusicDeleteEvent>()
    val deleteEvents: SharedFlow<MusicDeleteEvent> = _deleteEvents.asSharedFlow()

    val playlists = database.playlistDao().getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val equalizerState: StateFlow<EqualizerState> =
        MusicPlaybackService.serviceInstance?.equalizerController?.state
            ?: MutableStateFlow(EqualizerState())

    init {
        scanMediaLibrary()
    }

    fun scanMediaLibrary() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val tracks = repository.getAudioTracks()
            val albums = repository.groupIntoAlbums(tracks)
            val folders = repository.groupIntoFolders(tracks)

            _uiState.value = _uiState.value.copy(
                allTracks = tracks,
                filteredTracks = filterTracks(tracks, _uiState.value.searchQuery),
                albums = albums,
                folders = folders,
                isScanning = false
            )
            playerManager.restoreSession(tracks)
        }
    }

    fun setTab(tab: MusicTab) {
        _uiState.value = _uiState.value.copy(
            selectedTab = tab,
            selectedAlbum = null,
            selectedFolder = null,
            selectedPlaylist = null
        )
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredTracks = filterTracks(_uiState.value.allTracks, query)
        )
    }

    private fun filterTracks(tracks: List<AudioTrack>, query: String): List<AudioTrack> {
        if (query.isBlank()) return tracks
        val lower = query.lowercase()
        return tracks.filter {
            it.title.lowercase().contains(lower) ||
                    it.artist.lowercase().contains(lower) ||
                    it.album.lowercase().contains(lower)
        }
    }

    fun selectAlbum(album: AlbumItem?) {
        _uiState.value = _uiState.value.copy(selectedAlbum = album)
    }

    fun selectFolder(folder: FolderItem?) {
        _uiState.value = _uiState.value.copy(selectedFolder = folder)
    }

    fun selectPlaylist(playlist: PlaylistEntity?) {
        _uiState.value = _uiState.value.copy(selectedPlaylist = playlist)
        if (playlist != null) {
            viewModelScope.launch {
                database.playlistDao().getSongsForPlaylist(playlist.id).collect { songEntities ->
                    val songs = songEntities.map { entity ->
                        AudioTrack(
                            id = entity.audioId,
                            uri = entity.audioUri,
                            title = entity.title,
                            artist = entity.artist,
                            album = entity.album,
                            durationMs = entity.durationMs
                        )
                    }
                    _uiState.value = _uiState.value.copy(playlistSongs = songs)
                }
            }
        }
    }

    fun playTrack(track: AudioTrack, playlist: List<AudioTrack>? = null) {
        val listToPlay = playlist ?: _uiState.value.filteredTracks
        val index = listToPlay.indexOfFirst { it.uri == track.uri }
        playerManager.playTrackList(listToPlay, if (index >= 0) index else 0)
    }

    fun playAll(tracks: List<AudioTrack>) {
        if (tracks.isNotEmpty()) {
            playerManager.playTrackList(tracks, 0)
        }
    }

    fun shuffleAll(tracks: List<AudioTrack>) {
        if (tracks.isNotEmpty()) {
            playerManager.playTrackList(tracks.shuffled(), 0)
        }
    }

    fun openSafTrack(uri: Uri) {
        viewModelScope.launch {
            val track = repository.parseTrackFromUri(uri)
            val updatedTracks = listOf(track) + _uiState.value.allTracks
            _uiState.value = _uiState.value.copy(
                allTracks = updatedTracks,
                filteredTracks = filterTracks(updatedTracks, _uiState.value.searchQuery)
            )
            playerManager.playTrackList(listOf(track), 0)
        }
    }

    suspend fun findMatchingTrack(uri: Uri, displayName: String?, size: Long): AudioTrack? {
        val cached = _uiState.value.allTracks
        if (cached.isNotEmpty()) {
            val uriStr = uri.toString()
            cached.find { it.uri == uriStr }?.let { return it }
            val uriId = try {
                if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == "media") {
                    ContentUris.parseId(uri)
                } else null
            } catch (_: Exception) { null }
            if (uriId != null) {
                cached.find { it.id == uriId }?.let { return it }
            }
            if (!displayName.isNullOrBlank()) {
                val nameWithoutExt = displayName.substringBeforeLast('.')
                cached.find { t ->
                    val matchName = t.title.equals(displayName, ignoreCase = true) ||
                            t.title.equals(nameWithoutExt, ignoreCase = true) ||
                            (t.path.isNotEmpty() && File(t.path).name.equals(displayName, ignoreCase = true))
                    val matchSize = size <= 0L || (t.path.isNotEmpty() && File(t.path).length() == size)
                    matchName && matchSize
                }?.let { return it }
            }
        }
        return repository.findMatchingTrack(uri, displayName, size)
    }

    suspend fun parseTrackFromUri(uri: Uri, displayName: String?): AudioTrack {
        return repository.parseTrackFromUri(uri, displayName)
    }

    fun playIncomingTrack(track: AudioTrack) {
        playerManager.playTrackList(listOf(track), 0)
        setNowPlayingExpanded(true)
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            database.playlistDao().insertPlaylist(PlaylistEntity(name = name.trim()))
            _uiState.value = _uiState.value.copy(isCreatePlaylistDialogVisible = false)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: AudioTrack) {
        viewModelScope.launch {
            database.playlistDao().insertPlaylistSong(
                PlaylistSongEntity(
                    playlistId = playlistId,
                    audioId = track.id,
                    audioUri = track.uri,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    orderIndex = System.currentTimeMillis().toInt()
                )
            )
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, track: AudioTrack): PlaylistSongEntity? {
        val existing = database.playlistDao().getPlaylistSongByUri(playlistId, track.uri)
        database.playlistDao().removeSongFromPlaylist(playlistId, track.uri)
        return existing
    }

    fun restorePlaylistSong(entity: PlaylistSongEntity) {
        viewModelScope.launch {
            database.playlistDao().insertPlaylistSong(entity)
        }
    }

    fun deletePlaylist(playlist: PlaylistEntity) {
        viewModelScope.launch {
            database.playlistDao().deletePlaylist(playlist)
            if (_uiState.value.selectedPlaylist?.id == playlist.id) {
                _uiState.value = _uiState.value.copy(selectedPlaylist = null)
            }
        }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _uiState.value = _uiState.value.copy(isNowPlayingExpanded = expanded)
    }

    fun setQueueSheetVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isQueueSheetVisible = visible)
    }

    fun setEqualizerDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isEqualizerDialogVisible = visible)
    }

    fun setSpeedDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isSpeedDialogVisible = visible)
    }

    fun setSleepTimerDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isSleepTimerDialogVisible = visible)
    }

    fun setCreatePlaylistDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isCreatePlaylistDialogVisible = visible)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        MusicPlaybackService.serviceInstance?.equalizerController?.setEnabled(enabled)
    }

    fun setEqualizerPreset(presetIndex: Int) {
        MusicPlaybackService.serviceInstance?.equalizerController?.setPreset(presetIndex)
    }

    fun setBassBoost(strength: Int) {
        MusicPlaybackService.serviceInstance?.equalizerController?.setBassBoost(strength)
    }

    fun requestDeleteTrack(track: AudioTrack) {
        _uiState.value = _uiState.value.copy(trackToDelete = track, isDeletingTrack = false)
    }

    fun cancelDeleteTrack() {
        _uiState.value = _uiState.value.copy(trackToDelete = null, isDeletingTrack = false)
    }

    fun confirmDeleteTrack() {
        val track = _uiState.value.trackToDelete ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeletingTrack = true)
            when (val result = repository.deleteTrack(track)) {
                is DeleteTrackResult.Success -> {
                    onTrackDeletedSuccessfully(track)
                }
                is DeleteTrackResult.RequiresIntentSender -> {
                    _deleteEvents.emit(MusicDeleteEvent.LaunchIntentSender(result.intentSender, result.track))
                }
                is DeleteTrackResult.RequiresPermission -> {
                    _deleteEvents.emit(MusicDeleteEvent.RequestStoragePermission(result.track))
                }
                is DeleteTrackResult.Error -> {
                    _uiState.value = _uiState.value.copy(isDeletingTrack = false, trackToDelete = null)
                    _deleteEvents.emit(
                        MusicDeleteEvent.ShowMessage(
                            getApplication<Application>().getString(R.string.delete_failed_with_reason, result.message)
                        )
                    )
                }
            }
        }
    }

    fun onIntentSenderResult(approved: Boolean, track: AudioTrack) {
        viewModelScope.launch {
            if (approved) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    val deleted = repository.deleteTrackAfterSecurityApproval(track)
                    if (deleted) {
                        onTrackDeletedSuccessfully(track)
                    } else {
                        _uiState.value = _uiState.value.copy(isDeletingTrack = false, trackToDelete = null)
                        _deleteEvents.emit(
                            MusicDeleteEvent.ShowMessage(
                                getApplication<Application>().getString(R.string.delete_failed)
                            )
                        )
                    }
                } else {
                    onTrackDeletedSuccessfully(track)
                }
            } else {
                _uiState.value = _uiState.value.copy(isDeletingTrack = false, trackToDelete = null)
            }
        }
    }

    fun onStoragePermissionResult(granted: Boolean, track: AudioTrack) {
        if (granted) {
            confirmDeleteTrack()
        } else {
            _uiState.value = _uiState.value.copy(isDeletingTrack = false, trackToDelete = null)
            viewModelScope.launch {
                _deleteEvents.emit(
                    MusicDeleteEvent.ShowMessage(
                        getApplication<Application>().getString(R.string.storage_permission_required)
                    )
                )
            }
        }
    }

    fun onTrackDeletedSuccessfully(track: AudioTrack) {
        viewModelScope.launch {
            // 1. Remove from all playlists in Room
            database.playlistDao().removeSongFromAllPlaylists(track.uri)

            // 2. Remove from playback / queue
            playerManager.removeTrackFromPlayback(track.uri)

            // 3. Update memory lists immediately
            val updatedAll = _uiState.value.allTracks.filter { it.uri != track.uri }
            val updatedFiltered = filterTracks(updatedAll, _uiState.value.searchQuery)

            // 4. Update groupings and counters; empty albums/folders automatically excluded
            val updatedAlbums = repository.groupIntoAlbums(updatedAll)
            val updatedFolders = repository.groupIntoFolders(updatedAll)

            val updatedSelectedAlbum = _uiState.value.selectedAlbum?.let { album ->
                updatedAlbums.find { it.title == album.title }
            }
            val updatedSelectedFolder = _uiState.value.selectedFolder?.let { folder ->
                updatedFolders.find { it.name == folder.name }
            }
            val updatedPlaylistSongs = _uiState.value.playlistSongs.filter { it.uri != track.uri }

            _uiState.value = _uiState.value.copy(
                allTracks = updatedAll,
                filteredTracks = updatedFiltered,
                albums = updatedAlbums,
                folders = updatedFolders,
                selectedAlbum = updatedSelectedAlbum,
                selectedFolder = updatedSelectedFolder,
                playlistSongs = updatedPlaylistSongs,
                isDeletingTrack = false,
                trackToDelete = null
            )

            _deleteEvents.emit(
                MusicDeleteEvent.ShowMessage(
                    getApplication<Application>().getString(R.string.song_deleted)
                )
            )
        }
    }

    override fun onCleared() {
        playerManager.release()
        super.onCleared()
    }
}
