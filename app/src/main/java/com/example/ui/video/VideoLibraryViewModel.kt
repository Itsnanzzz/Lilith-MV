package com.example.ui.video

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LilithApplication
import com.example.data.MediaRepository
import com.example.data.model.FolderItem
import com.example.data.model.VideoItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class VideoLibraryUiState(
    val allVideos: List<VideoItem> = emptyList(),
    val filteredVideos: List<VideoItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val selectedFolder: FolderItem? = null,
    val searchQuery: String = "",
    val isScanning: Boolean = false,
    val activePlayingVideo: VideoItem? = null
)

class VideoLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)
    private val database = (application as LilithApplication).database

    private val _uiState = MutableStateFlow(VideoLibraryUiState())
    val uiState: StateFlow<VideoLibraryUiState> = _uiState.asStateFlow()

    private var loadedLocaleTag: String? = null

    init {
        scanVideos()
    }

    fun syncLocale(languageTag: String) {
        if (loadedLocaleTag != null && loadedLocaleTag != languageTag) {
            loadedLocaleTag = languageTag
            scanVideos()
        } else {
            loadedLocaleTag = languageTag
        }
    }

    fun scanVideos() {
        viewModelScope.launch {
            loadedLocaleTag = com.example.ui.common.normalizeLanguageTag(
                com.example.ui.common.getAppLocales(getApplication()).get(0)
            )
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
            if (video != null) {
                _uiState.value = _uiState.value.copy(activePlayingVideo = video)
            }
        }
    }

    fun playVideo(video: VideoItem) {
        _uiState.value = _uiState.value.copy(activePlayingVideo = video)
    }

    fun closePlayer() {
        _uiState.value = _uiState.value.copy(activePlayingVideo = null)
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
