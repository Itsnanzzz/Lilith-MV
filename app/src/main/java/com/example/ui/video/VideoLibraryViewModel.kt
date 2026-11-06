package com.example.ui.video

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
import com.example.data.DeleteMediaResult
import com.example.data.MediaRepository
import com.example.data.RenameVideoResult
import com.example.data.model.FolderItem
import com.example.data.model.VideoExtraInfo
import com.example.data.model.VideoItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class VideoActionEvent {
    data class LaunchIntentSender(val intentSender: IntentSender, val action: ActionType) : VideoActionEvent() {
        enum class ActionType { DELETE, RENAME }
    }
    data class RequestStoragePermission(val permission: String, val action: ActionType) : VideoActionEvent() {
        enum class ActionType { DELETE, RENAME }
    }
    data class ShowMessage(val message: String) : VideoActionEvent()
}

data class VideoLibraryUiState(
    val allVideos: List<VideoItem> = emptyList(),
    val filteredVideos: List<VideoItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val selectedFolder: FolderItem? = null,
    val searchQuery: String = "",
    val isScanning: Boolean = false,
    val activePlayingVideo: VideoItem? = null,
    val videoForInfo: VideoItem? = null,
    val videoToRename: VideoItem? = null,
    val videoToDelete: VideoItem? = null,
    val isDeletingVideo: Boolean = false,
    val isRenamingVideo: Boolean = false,
    val operatingVideoId: Long? = null
)

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val database = (application as LilithApplication).database

    private val _uiState = MutableStateFlow(VideoLibraryUiState())
    val uiState: StateFlow<VideoLibraryUiState> = _uiState.asStateFlow()

    private val _actionEvents = MutableSharedFlow<VideoActionEvent>()
    val actionEvents: SharedFlow<VideoActionEvent> = _actionEvents.asSharedFlow()

    private sealed class PendingAction {
        data class Delete(val video: VideoItem) : PendingAction()
        data class Rename(val video: VideoItem, val newFullName: String) : PendingAction()
    }
    private var pendingAction: PendingAction? = null

    init {
        scanVideos()
    }

    fun scanVideos() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanning = true)
            val list = repository.getVideos()
            val folders = list.groupBy { it.folderName }.map { (fName, vList) ->
                val sample = vList.firstOrNull()?.path ?: ""
                val parentPath = if (sample.isNotEmpty()) File(sample).parent ?: "" else ""
                FolderItem(
                    name = fName,
                    path = parentPath,
                    trackCount = vList.size,
                    isVideo = true
                )
            }.sortedBy { it.name.lowercase() }

            _uiState.value = _uiState.value.copy(
                allVideos = list,
                filteredVideos = filterVideos(list, _uiState.value.searchQuery, _uiState.value.selectedFolder),
                folders = folders,
                isScanning = false
            )
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredVideos = filterVideos(_uiState.value.allVideos, query, _uiState.value.selectedFolder)
        )
    }

    fun selectFolder(folder: FolderItem?) {
        _uiState.value = _uiState.value.copy(
            selectedFolder = folder,
            filteredVideos = filterVideos(_uiState.value.allVideos, _uiState.value.searchQuery, folder)
        )
    }

    fun openSafVideo(uri: Uri) {
        viewModelScope.launch {
            val video = repository.parseVideoFromUri(uri)
            _uiState.value = _uiState.value.copy(activePlayingVideo = video)
        }
    }

    fun playVideo(video: VideoItem) {
        _uiState.value = _uiState.value.copy(activePlayingVideo = video)
    }

    suspend fun findMatchingVideo(uri: Uri, displayName: String?, size: Long): VideoItem? {
        val cached = _uiState.value.allVideos
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
                cached.find { v ->
                    val matchName = v.title.equals(displayName, ignoreCase = true) ||
                            v.title.equals(nameWithoutExt, ignoreCase = true) ||
                            (v.path.isNotEmpty() && File(v.path).name.equals(displayName, ignoreCase = true))
                    val matchSize = size <= 0L || v.size == size || (v.path.isNotEmpty() && File(v.path).length() == size)
                    matchName && matchSize
                }?.let { return it }
            }
        }
        return repository.findMatchingVideo(uri, displayName, size)
    }

    suspend fun parseVideoFromUri(uri: Uri, displayName: String?, size: Long): VideoItem {
        return repository.parseVideoFromUri(uri, displayName, size)
    }

    fun closePlayer() {
        _uiState.value = _uiState.value.copy(activePlayingVideo = null)
    }

    // --- Info Action ---
    fun showInfo(video: VideoItem) {
        _uiState.value = _uiState.value.copy(videoForInfo = video)
    }

    fun dismissInfo() {
        _uiState.value = _uiState.value.copy(videoForInfo = null)
    }

    suspend fun loadExtraInfo(video: VideoItem): VideoExtraInfo {
        return repository.loadVideoExtraInfo(video)
    }

    // --- Rename Action ---
    fun showRenameDialog(video: VideoItem) {
        _uiState.value = _uiState.value.copy(
            videoToRename = video,
            isRenamingVideo = false
        )
    }

    fun dismissRenameDialog() {
        _uiState.value = _uiState.value.copy(
            videoToRename = null,
            isRenamingVideo = false
        )
    }

    suspend fun validateNewName(video: VideoItem, rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            return getApplication<Application>().getString(R.string.rename_error_empty)
        }
        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (rawText.any { it in invalidChars || it.code < 32 }) {
            return getApplication<Application>().getString(R.string.rename_error_invalid_chars)
        }
        if (rawText.startsWith(".") || rawText.endsWith(".") || rawText.startsWith(" ") || rawText.endsWith(" ")) {
            return getApplication<Application>().getString(R.string.rename_error_dots_spaces)
        }
        val ext = video.extension
        val fullName = if (ext.isNotEmpty()) "$trimmed.$ext" else trimmed
        if (fullName.toByteArray(Charsets.UTF_8).size > 255) {
            return getApplication<Application>().getString(R.string.rename_error_too_long)
        }
        if (trimmed != video.baseName) {
            val existsInState = _uiState.value.allVideos.any {
                it.id != video.id && it.folderName == video.folderName && it.fullDisplayName.equals(fullName, ignoreCase = true)
            }
            if (existsInState || repository.checkVideoNameExists(video, fullName)) {
                return getApplication<Application>().getString(R.string.rename_error_already_exists)
            }
        }
        return null
    }

    fun confirmRename(video: VideoItem, newFullName: String) {
        if (_uiState.value.isRenamingVideo) return
        _uiState.value = _uiState.value.copy(isRenamingVideo = true, operatingVideoId = video.id)
        pendingAction = PendingAction.Rename(video, newFullName)
        viewModelScope.launch {
            when (val res = repository.renameVideo(video, newFullName)) {
                is RenameVideoResult.Success -> {
                    onVideoRenamedSuccessfully(video, newFullName)
                }
                is RenameVideoResult.RequiresIntentSender -> {
                    _actionEvents.emit(
                        VideoActionEvent.LaunchIntentSender(res.intentSender, VideoActionEvent.ActionType.RENAME)
                    )
                }
                is RenameVideoResult.RequiresPermission -> {
                    _actionEvents.emit(
                        VideoActionEvent.RequestStoragePermission(res.permission, VideoActionEvent.ActionType.RENAME)
                    )
                }
                is RenameVideoResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isRenamingVideo = false,
                        videoToRename = null,
                        operatingVideoId = null
                    )
                    pendingAction = null
                    _actionEvents.emit(VideoActionEvent.ShowMessage(res.message))
                }
            }
        }
    }

    private fun onVideoRenamedSuccessfully(video: VideoItem, newFullName: String) {
        viewModelScope.launch {
            val newTitle = newFullName.substringBeforeLast('.')
            val newPath = if (video.path.isNotEmpty()) {
                val parent = File(video.path).parent ?: ""
                if (parent.isNotEmpty()) "$parent/$newFullName" else newFullName
            } else ""

            val updatedVideo = video.copy(
                title = newTitle,
                displayName = newFullName,
                path = newPath
            )

            // Migrate Room resume position in one transaction
            database.videoProgressDao().migrateProgress(
                oldUri = video.uri,
                oldTitle = video.title,
                newUri = updatedVideo.uri,
                newTitle = updatedVideo.title
            )

            // Update in-memory lists in place
            val updatedAll = _uiState.value.allVideos.map {
                if (it.id == video.id || it.uri == video.uri) updatedVideo else it
            }
            val updatedFiltered = filterVideos(updatedAll, _uiState.value.searchQuery, _uiState.value.selectedFolder)

            val active = _uiState.value.activePlayingVideo
            val updatedActive = if (active?.id == video.id || active?.uri == video.uri) updatedVideo else active

            _uiState.value = _uiState.value.copy(
                allVideos = updatedAll,
                filteredVideos = updatedFiltered,
                activePlayingVideo = updatedActive,
                isRenamingVideo = false,
                videoToRename = null,
                operatingVideoId = null
            )
            pendingAction = null
            _actionEvents.emit(
                VideoActionEvent.ShowMessage(
                    getApplication<Application>().getString(R.string.video_renamed)
                )
            )
        }
    }

    // --- Delete Action ---
    fun showDeleteDialog(video: VideoItem) {
        _uiState.value = _uiState.value.copy(
            videoToDelete = video,
            isDeletingVideo = false
        )
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(
            videoToDelete = null,
            isDeletingVideo = false
        )
    }

    fun confirmDelete() {
        val video = _uiState.value.videoToDelete ?: return
        if (_uiState.value.isDeletingVideo) return
        _uiState.value = _uiState.value.copy(isDeletingVideo = true, operatingVideoId = video.id)
        pendingAction = PendingAction.Delete(video)
        viewModelScope.launch {
            when (val res = repository.deleteVideo(video)) {
                is DeleteMediaResult.Success -> {
                    onVideoDeletedSuccessfully(video)
                }
                is DeleteMediaResult.RequiresIntentSender -> {
                    _actionEvents.emit(
                        VideoActionEvent.LaunchIntentSender(res.intentSender, VideoActionEvent.ActionType.DELETE)
                    )
                }
                is DeleteMediaResult.RequiresPermission -> {
                    _actionEvents.emit(
                        VideoActionEvent.RequestStoragePermission(res.permission, VideoActionEvent.ActionType.DELETE)
                    )
                }
                is DeleteMediaResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isDeletingVideo = false,
                        videoToDelete = null,
                        operatingVideoId = null
                    )
                    pendingAction = null
                    _actionEvents.emit(VideoActionEvent.ShowMessage(res.message))
                }
            }
        }
    }

    private fun onVideoDeletedSuccessfully(video: VideoItem) {
        viewModelScope.launch {
            // Delete Room progress row
            database.videoProgressDao().deletePosition(video.uri)
            database.videoProgressDao().deletePositionByTitle(video.title)

            // If active playing video is this one, safely close player
            if (_uiState.value.activePlayingVideo?.id == video.id || _uiState.value.activePlayingVideo?.uri == video.uri) {
                closePlayer()
            }

            // Remove from in-memory list
            val updatedAll = _uiState.value.allVideos.filter { it.id != video.id && it.uri != video.uri }
            val updatedFolders = updatedAll.groupBy { it.folderName }.map { (fName, vList) ->
                val sample = vList.firstOrNull()?.path ?: ""
                val parentPath = if (sample.isNotEmpty()) File(sample).parent ?: "" else ""
                FolderItem(
                    name = fName,
                    path = parentPath,
                    trackCount = vList.size,
                    isVideo = true
                )
            }.sortedBy { it.name.lowercase() }

            val updatedSelectedFolder = _uiState.value.selectedFolder?.let { selected ->
                updatedFolders.find { it.name == selected.name }
            }

            val updatedFiltered = filterVideos(updatedAll, _uiState.value.searchQuery, updatedSelectedFolder)

            _uiState.value = _uiState.value.copy(
                allVideos = updatedAll,
                filteredVideos = updatedFiltered,
                folders = updatedFolders,
                selectedFolder = updatedSelectedFolder,
                isDeletingVideo = false,
                videoToDelete = null,
                operatingVideoId = null
            )
            pendingAction = null
            _actionEvents.emit(
                VideoActionEvent.ShowMessage(
                    getApplication<Application>().getString(R.string.video_deleted)
                )
            )
        }
    }

    // --- Intent & Permission Callbacks ---
    fun onIntentSenderResult(approved: Boolean) {
        viewModelScope.launch {
            val action = pendingAction
            if (approved && action != null) {
                when (action) {
                    is PendingAction.Delete -> {
                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                            val deleted = repository.deleteVideoAfterSecurityApproval(action.video)
                            if (deleted) {
                                onVideoDeletedSuccessfully(action.video)
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isDeletingVideo = false,
                                    videoToDelete = null,
                                    operatingVideoId = null
                                )
                                pendingAction = null
                                _actionEvents.emit(
                                    VideoActionEvent.ShowMessage(
                                        getApplication<Application>().getString(R.string.delete_video_failed)
                                    )
                                )
                            }
                        } else {
                            onVideoDeletedSuccessfully(action.video)
                        }
                    }
                    is PendingAction.Rename -> {
                        val updated = repository.performRenameUpdate(action.video, action.newFullName)
                        if (updated || Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            onVideoRenamedSuccessfully(action.video, action.newFullName)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                isRenamingVideo = false,
                                videoToRename = null,
                                operatingVideoId = null
                            )
                            pendingAction = null
                            _actionEvents.emit(
                                VideoActionEvent.ShowMessage(
                                    getApplication<Application>().getString(R.string.rename_failed)
                                )
                            )
                        }
                    }
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isDeletingVideo = false,
                    isRenamingVideo = false,
                    videoToDelete = null,
                    videoToRename = null,
                    operatingVideoId = null
                )
                pendingAction = null
            }
        }
    }

    fun onStoragePermissionResult(granted: Boolean) {
        if (granted) {
            when (val action = pendingAction) {
                is PendingAction.Delete -> confirmDelete()
                is PendingAction.Rename -> confirmRename(action.video, action.newFullName)
                null -> {}
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isDeletingVideo = false,
                isRenamingVideo = false,
                videoToDelete = null,
                videoToRename = null,
                operatingVideoId = null
            )
            pendingAction = null
            viewModelScope.launch {
                _actionEvents.emit(
                    VideoActionEvent.ShowMessage(
                        getApplication<Application>().getString(R.string.storage_permission_required)
                    )
                )
            }
        }
    }

    private fun filterVideos(list: List<VideoItem>, query: String, folder: FolderItem?): List<VideoItem> {
        var result = list
        if (folder != null) {
            result = result.filter { it.folderName == folder.name }
        }
        if (query.isNotBlank()) {
            val lower = query.lowercase()
            result = result.filter { it.title.lowercase().contains(lower) }
        }
        return result
    }
}
