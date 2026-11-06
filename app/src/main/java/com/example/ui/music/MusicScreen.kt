package com.example.ui.music

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.db.PlaylistEntity
import com.example.data.model.AlbumItem
import com.example.data.model.AudioTrack
import com.example.data.model.FolderItem
import com.example.playback.AudioFormatDetector
import com.example.ui.common.MusicThumbnail
import com.example.ui.common.TimeUtils
import com.example.ui.theme.AppBadge
import com.example.ui.theme.AppChip
import com.example.ui.theme.AppIconButton
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppSearchField
import com.example.ui.theme.AppSecondaryButton
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import com.example.ui.theme.LilithTheme
import com.example.ui.theme.rememberIsAnimationDisabled
import kotlinx.coroutines.launch

@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeTokens.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentPlayingTrack by viewModel.playerManager.currentTrack.collectAsState()
    val isPlaying by viewModel.playerManager.isPlaying.collectAsState()
    val isAnimationDisabled = rememberIsAnimationDisabled()

    // Hoisted scroll positions so each page keeps its own position when swiped away and back
    val songsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val foldersListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()

    val initialPage = when (uiState.selectedTab) {
        MusicTab.SONGS -> 0
        MusicTab.ALBUMS -> 1
        MusicTab.FOLDERS -> 2
        MusicTab.PLAYLISTS -> 3
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 4 }
    )

    // Sync ViewModel tab when pager settles on a new page
    LaunchedEffect(pagerState.settledPage) {
        val newTab = when (pagerState.settledPage) {
            0 -> MusicTab.SONGS
            1 -> MusicTab.ALBUMS
            2 -> MusicTab.FOLDERS
            3 -> MusicTab.PLAYLISTS
            else -> MusicTab.SONGS
        }
        if (uiState.selectedTab != newTab) {
            viewModel.setTab(newTab)
        }
    }

    // Sync pager if uiState.selectedTab changes externally (e.g. restoring state or initial load)
    LaunchedEffect(uiState.selectedTab) {
        val targetPage = when (uiState.selectedTab) {
            MusicTab.SONGS -> 0
            MusicTab.ALBUMS -> 1
            MusicTab.FOLDERS -> 2
            MusicTab.PLAYLISTS -> 3
        }
        if (pagerState.currentPage != targetPage && !pagerState.isScrollInProgress) {
            if (isAnimationDisabled) {
                pagerState.scrollToPage(targetPage)
            } else {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    // SAF file picker for audio tracks
    val safAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.openSafTrack(it) }
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val track = uiState.trackToDelete
        if (track != null) {
            viewModel.onIntentSenderResult(result.resultCode == Activity.RESULT_OK, track)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val track = uiState.trackToDelete
        if (track != null) {
            viewModel.onStoragePermissionResult(isGranted, track)
        }
    }

    val renameIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onRenameIntentSenderResult(result.resultCode == Activity.RESULT_OK)
    }

    val renameStoragePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onRenameStoragePermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        viewModel.deleteEvents.collect { event ->
            when (event) {
                is MusicDeleteEvent.LaunchIntentSender -> {
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build()
                    )
                }
                is MusicDeleteEvent.RequestStoragePermission -> {
                    storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                is MusicDeleteEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.actionEvents.collect { event ->
            when (event) {
                is MusicActionEvent.LaunchRenameIntentSender -> {
                    renameIntentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build()
                    )
                }
                is MusicActionEvent.RequestRenameStoragePermission -> {
                    renameStoragePermissionLauncher.launch(event.permission)
                }
                is MusicActionEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("music_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Top Search & Action Row: Search Field (40dp) + 2 Tonal Circle Icon Buttons (40dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTokens.ScreenHorizontalPadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholder = stringResource(R.string.search_placeholder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("search_music_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Open SAF Audio: 40dp tonal circle in surfaceMuted with 20dp icon
                AppIconButton(
                    onClick = { safAudioLauncher.launch(arrayOf("audio/*")) },
                    icon = Icons.Default.FileOpen,
                    contentDescription = stringResource(R.string.open_saf_file),
                    isTonalCircle = true,
                    iconSize = 20.dp,
                    tintOverride = colors.textPrimary,
                    testTag = "open_saf_audio_button"
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Rescan Library: 40dp tonal circle in surfaceMuted with 20dp icon
                AppIconButton(
                    onClick = { viewModel.scanMediaLibrary() },
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.scan_library),
                    isTonalCircle = true,
                    iconSize = 20.dp,
                    tintOverride = colors.textPrimary,
                    testTag = "rescan_library_button"
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Filter Chips: Songs, Albums, Folders, Playlists (32dp tall, 13sp, 20dp start padding, 4 chips share row evenly)
            val tabs = listOf(
                MusicTab.SONGS to stringResource(R.string.tab_songs),
                MusicTab.ALBUMS to stringResource(R.string.tab_albums),
                MusicTab.FOLDERS to stringResource(R.string.tab_folders),
                MusicTab.PLAYLISTS to stringResource(R.string.tab_playlists)
            )

            val selectedTabIndex = if (pagerState.isScrollInProgress) {
                pagerState.currentPage
            } else {
                pagerState.settledPage
            }

            val onTabClick: (Int, MusicTab) -> Unit = { pageIndex, tab ->
                val isSelected = selectedTabIndex == pageIndex
                if (isSelected) {
                    when (tab) {
                        MusicTab.ALBUMS -> if (uiState.selectedAlbum != null) viewModel.selectAlbum(null)
                        MusicTab.FOLDERS -> if (uiState.selectedFolder != null) viewModel.selectFolder(null)
                        MusicTab.PLAYLISTS -> if (uiState.selectedPlaylist != null) viewModel.selectPlaylist(null)
                        MusicTab.SONGS -> {}
                    }
                } else {
                    coroutineScope.launch {
                        if (isAnimationDisabled) {
                            pagerState.scrollToPage(pageIndex)
                        } else {
                            pagerState.animateScrollToPage(pageIndex)
                        }
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTokens.ScreenHorizontalPadding, vertical = 6.dp)
            ) {
                val fontScale = LocalDensity.current.fontScale
                val canFitSingleRow = maxWidth >= 300.dp && fontScale <= 1.25f

                if (canFitSingleRow) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, (tab, title) ->
                            val isSelected = selectedTabIndex == index
                            AppChip(
                                text = title,
                                selected = isSelected,
                                onClick = { onTabClick(index, tab) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, (tab, title) ->
                            val isSelected = selectedTabIndex == index
                            AppChip(
                                text = title,
                                selected = isSelected,
                                onClick = { onTabClick(index, tab) },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Tab Content - Swipeable Horizontal Pager across the 4 sections
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.isScanning) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                } else {
                    val isDetailShowing = when (pagerState.currentPage) {
                        1 -> uiState.selectedAlbum != null
                        2 -> uiState.selectedFolder != null
                        3 -> uiState.selectedPlaylist != null
                        else -> false
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !isDetailShowing,
                        beyondViewportPageCount = 0,
                        key = { page -> page }
                    ) { page ->
                        when (page) {
                            0 -> {
                                SongsListView(
                                    tracks = uiState.filteredTracks,
                                    currentPlayingTrackUri = currentPlayingTrack?.uri,
                                    isPlaying = isPlaying,
                                    playlists = playlists,
                                    listState = songsListState,
                                    onPlayTrack = { track -> viewModel.playTrack(track) },
                                    onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                    onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                    onInfo = { track -> viewModel.showTrackInfo(track) },
                                    onShare = { track -> shareSong(context, snackbarHostState, track) },
                                    onRename = { track -> viewModel.showRenameTrackDialog(track) },
                                    onDeletePermanently = { track -> viewModel.requestDeleteTrack(track) },
                                    isDeletingTrack = uiState.isDeletingTrack,
                                    operatingTrackId = uiState.operatingTrackId
                                )
                            }

                            1 -> {
                                if (uiState.selectedAlbum != null) {
                                    val album = uiState.selectedAlbum!!
                                    val albumTracks = uiState.allTracks.filter { it.album == album.title }
                                    CollectionDetailView(
                                        title = album.title,
                                        subtitle = album.artist,
                                        artTarget = album.albumArtUri,
                                        tracks = albumTracks,
                                        currentPlayingTrackUri = currentPlayingTrack?.uri,
                                        isPlaying = isPlaying,
                                        playlists = playlists,
                                        onBack = { viewModel.selectAlbum(null) },
                                        onPlayTrack = { track -> viewModel.playTrack(track, albumTracks) },
                                        onPlayAll = { viewModel.playAll(albumTracks) },
                                        onShuffleAll = { viewModel.shuffleAll(albumTracks) },
                                        onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                        onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                        showBackButton = true,
                                        onInfo = { track -> viewModel.showTrackInfo(track) },
                                        onShare = { track -> shareSong(context, snackbarHostState, track) },
                                        onRename = { track -> viewModel.showRenameTrackDialog(track) },
                                        onDeletePermanently = { track -> viewModel.requestDeleteTrack(track) },
                                        isDeletingTrack = uiState.isDeletingTrack,
                                        operatingTrackId = uiState.operatingTrackId
                                    )
                                } else {
                                    val displayAlbums = remember(uiState.albums, uiState.searchQuery) {
                                        if (uiState.searchQuery.isBlank()) {
                                            uiState.albums
                                        } else {
                                            val q = uiState.searchQuery.trim()
                                            uiState.albums.filter {
                                                it.title.contains(q, ignoreCase = true) ||
                                                        it.artist.contains(q, ignoreCase = true)
                                            }
                                        }
                                    }
                                    AlbumsGridView(
                                        albums = displayAlbums,
                                        gridState = albumsGridState,
                                        onAlbumClick = { viewModel.selectAlbum(it) }
                                    )
                                }
                            }

                            2 -> {
                                if (uiState.selectedFolder != null) {
                                    val folder = uiState.selectedFolder!!
                                    val folderTracks = uiState.allTracks.filter { it.folderName == folder.name }
                                    CollectionDetailView(
                                        title = folder.name,
                                        subtitle = pluralStringResource(R.plurals.songs_count, folderTracks.size, folderTracks.size),
                                        artTarget = folderTracks.firstOrNull()?.albumArtUri ?: folderTracks.firstOrNull()?.uri,
                                        tracks = folderTracks,
                                        currentPlayingTrackUri = currentPlayingTrack?.uri,
                                        isPlaying = isPlaying,
                                        playlists = playlists,
                                        onBack = { viewModel.selectFolder(null) },
                                        onPlayTrack = { track -> viewModel.playTrack(track, folderTracks) },
                                        onPlayAll = { viewModel.playAll(folderTracks) },
                                        onShuffleAll = { viewModel.shuffleAll(folderTracks) },
                                        onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                        onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                        showBackButton = true,
                                        onInfo = { track -> viewModel.showTrackInfo(track) },
                                        onShare = { track -> shareSong(context, snackbarHostState, track) },
                                        onRename = { track -> viewModel.showRenameTrackDialog(track) },
                                        onDeletePermanently = { track -> viewModel.requestDeleteTrack(track) },
                                        isDeletingTrack = uiState.isDeletingTrack,
                                        operatingTrackId = uiState.operatingTrackId
                                    )
                                } else {
                                    val displayFolders = remember(uiState.folders, uiState.searchQuery) {
                                        if (uiState.searchQuery.isBlank()) {
                                            uiState.folders
                                        } else {
                                            val q = uiState.searchQuery.trim()
                                            uiState.folders.filter {
                                                it.name.contains(q, ignoreCase = true)
                                            }
                                        }
                                    }
                                    FoldersListView(
                                        folders = displayFolders,
                                        listState = foldersListState,
                                        onFolderClick = { viewModel.selectFolder(it) }
                                    )
                                }
                            }

                            3 -> {
                                val currentPlaylist = uiState.selectedPlaylist
                                if (currentPlaylist != null) {
                                    CollectionDetailView(
                                        title = currentPlaylist.name,
                                        subtitle = pluralStringResource(R.plurals.songs_count, uiState.playlistSongs.size, uiState.playlistSongs.size),
                                        artTarget = uiState.playlistSongs.firstOrNull()?.albumArtUri ?: uiState.playlistSongs.firstOrNull()?.uri,
                                        tracks = uiState.playlistSongs,
                                        currentPlayingTrackUri = currentPlayingTrack?.uri,
                                        isPlaying = isPlaying,
                                        playlists = playlists,
                                        onBack = { viewModel.selectPlaylist(null) },
                                        onPlayTrack = { track -> viewModel.playTrack(track, uiState.playlistSongs) },
                                        onPlayAll = { viewModel.playAll(uiState.playlistSongs) },
                                        onShuffleAll = { viewModel.shuffleAll(uiState.playlistSongs) },
                                        onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                        onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                        showBackButton = false,
                                        currentPlaylistId = currentPlaylist.id,
                                        onInfo = { track -> viewModel.showTrackInfo(track) },
                                        onShare = { track -> shareSong(context, snackbarHostState, track) },
                                        onRename = { track -> viewModel.showRenameTrackDialog(track) },
                                        operatingTrackId = uiState.operatingTrackId,
                                        onRemoveFromPlaylist = { track ->
                                            coroutineScope.launch {
                                                val removedEntity = viewModel.removeTrackFromPlaylist(currentPlaylist.id, track)
                                                val result = snackbarHostState.showSnackbar(
                                                    message = context.getString(R.string.playlist_song_removed),
                                                    actionLabel = context.getString(R.string.undo),
                                                    duration = SnackbarDuration.Short
                                                )
                                                if (result == SnackbarResult.ActionPerformed && removedEntity != null) {
                                                    viewModel.restorePlaylistSong(removedEntity)
                                                }
                                            }
                                        }
                                    )
                                } else {
                                    val displayPlaylists = remember(playlists, uiState.searchQuery) {
                                        if (uiState.searchQuery.isBlank()) {
                                            playlists
                                        } else {
                                            val q = uiState.searchQuery.trim()
                                            playlists.filter {
                                                it.name.contains(q, ignoreCase = true)
                                            }
                                        }
                                    }
                                    PlaylistsView(
                                        playlists = displayPlaylists,
                                        listState = playlistsListState,
                                        onPlaylistClick = { viewModel.selectPlaylist(it) },
                                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                                        onCreatePlaylist = { viewModel.setCreatePlaylistDialogVisible(true) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Snackbar above bottom docked panel with 12dp radius and primary container
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp, start = 20.dp, end = 20.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(AppTokens.Radius12),
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    actionColor = colors.onPrimary
                )
            }
        )
    }

    if (uiState.isCreatePlaylistDialogVisible) {
        CreatePlaylistDialog(
            onConfirm = { viewModel.createPlaylist(it) },
            onDismiss = { viewModel.setCreatePlaylistDialogVisible(false) }
        )
    }

    val trackToDelete = uiState.trackToDelete
    if (trackToDelete != null) {
        DeletePermanentConfirmationDialog(
            track = trackToDelete,
            isDeleting = uiState.isDeletingTrack,
            onConfirm = { viewModel.confirmDeleteTrack() },
            onDismiss = { viewModel.cancelDeleteTrack() }
        )
    }

    uiState.trackForInfo?.let { track ->
        SongInfoDialog(
            track = track,
            onDismiss = { viewModel.dismissTrackInfo() },
            onCopyPath = { snackbarHostState.showSnackbar(context.getString(R.string.copied)) },
            loadExtraInfo = { viewModel.loadSongExtraInfo(it) }
        )
    }

    uiState.trackToRename?.let { track ->
        MediaRenameDialog(
            title = stringResource(R.string.rename_song_title),
            baseName = track.baseName,
            extension = track.extension,
            isRenaming = uiState.isRenamingTrack,
            onConfirm = { viewModel.confirmRenameTrack(track, it) },
            onDismiss = { viewModel.dismissRenameTrackDialog() },
            validateName = { viewModel.validateTrackName(track, it) }
        )
    }
}

/**
 * Flat Song list: No cards, no per-item shadows, no dividers.
 */
@Composable
fun SongsListView(
    tracks: List<AudioTrack>,
    currentPlayingTrackUri: String?,
    isPlaying: Boolean,
    playlists: List<PlaylistEntity>,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (Long, AudioTrack) -> Unit,
    onInfo: (AudioTrack) -> Unit = {},
    onShare: (AudioTrack) -> Unit = {},
    onRename: (AudioTrack) -> Unit = {},
    currentPlaylistId: Long? = null,
    onRemoveFromPlaylist: ((AudioTrack) -> Unit)? = null,
    onDeletePermanently: ((AudioTrack) -> Unit)? = null,
    isDeletingTrack: Boolean = false,
    operatingTrackId: Long? = null,
    emptyMessage: String = stringResource(R.string.no_media_found),
    listState: LazyListState = rememberLazyListState()
) {
    if (tracks.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.MusicNote,
            title = emptyMessage,
            subtitle = stringResource(R.string.empty_music_list)
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                items = tracks,
                key = { it.uri }
            ) { track ->
                SongRowItem(
                    track = track,
                    isCurrentTrack = track.uri == currentPlayingTrackUri,
                    isPlaying = isPlaying && track.uri == currentPlayingTrackUri,
                    playlists = playlists,
                    onClick = { onPlayTrack(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { pId -> onAddToPlaylist(pId, track) },
                    onInfo = { onInfo(track) },
                    onShare = { onShare(track) },
                    onRename = { onRename(track) },
                    currentPlaylistId = currentPlaylistId,
                    onRemoveFromPlaylist = onRemoveFromPlaylist?.let { { it(track) } },
                    onDeletePermanently = onDeletePermanently?.let { { it(track) } },
                    isDeleting = isDeletingTrack,
                    isOperating = operatingTrackId == track.id
                )
            }
        }
    }
}

/**
 * Flat Song Row:
 * - 48dp artwork with 10dp radius
 * - Title 15sp SemiBold, one line, ellipsis
 * - Subtitle row: AppBadge, artist, duration ("Artist • 2:46") in 13sp textSecondary
 * - Three-dot button: 20dp icon, 48dp touch target, textSecondary
 * - Currently playing row: surfaceMuted rounded background (12dp), title in primary, animated equalizer bars
 */
@Composable
fun SongRowItem(
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    playlists: List<PlaylistEntity>,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    onInfo: () -> Unit = {},
    onShare: () -> Unit = {},
    onRename: () -> Unit = {},
    currentPlaylistId: Long? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null,
    isDeleting: Boolean = false,
    isOperating: Boolean = false
) {
    val colors = AppThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val formatInfo = remember(track.mimeType) {
        AudioFormatDetector.detectFromMime(track.mimeType)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTokens.Radius12))
            .background(if (isCurrentTrack) colors.surfaceMuted else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("song_item_${track.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 48dp Artwork with 10dp radius
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(AppTokens.Radius10))
                    .background(colors.surfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                val artTarget = track.albumArtUri?.takeIf { it.isNotBlank() } ?: track.uri
                MusicThumbnail(
                    artTarget = artTarget,
                    contentDescription = track.album,
                    modifier = Modifier.fillMaxSize()
                )

                if (isCurrentTrack) {
                    AnimatedEqualizerBars(
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title + subtitle row (badge, artist, duration)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = AppTypography.listTitle,
                    color = if (isCurrentTrack) colors.accentText else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBadge(isLossless = formatInfo.isLossless)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${track.artist} • ${TimeUtils.formatDuration(track.durationMs)}",
                        style = AppTypography.listSubtitle,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Flat Three-Dot Button
            Box(contentAlignment = Alignment.Center) {
                AppIconButton(
                    onClick = { menuExpanded = true },
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    iconSize = 20.dp,
                    tintOverride = colors.textSecondary,
                    testTag = "song_more_button_${track.id}"
                )

                // Popup menu: surface color, 16dp radius, soft shadow, 14sp items, 20dp icons
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .widthIn(min = 190.dp, max = 240.dp)
                        .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.6f)
                        .verticalScroll(rememberScrollState())
                        .shadow(elevation = 8.dp, shape = RoundedCornerShape(AppTokens.Radius16))
                        .background(colors.surface, shape = RoundedCornerShape(AppTokens.Radius16))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.add_to_queue),
                                color = colors.textPrimary,
                                style = AppTypography.body
                            )
                        },
                        onClick = {
                            onAddToQueue()
                            menuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    // "Remove from Playlist" (Inside playlist detail only)
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.playlist_remove_song),
                                    color = colors.destructive,
                                    style = AppTypography.body.copy(fontWeight = FontWeight.Medium)
                                )
                            },
                            onClick = {
                                onRemoveFromPlaylist()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = colors.destructive,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }

                    // Eligible playlists to add to
                    val eligiblePlaylists = remember(playlists, currentPlaylistId) {
                        playlists.filter { it.id != currentPlaylistId }
                    }

                    if (eligiblePlaylists.isNotEmpty()) {
                        eligiblePlaylists.forEach { playlist ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.playlist_add_to, playlist.name),
                                        color = colors.textPrimary,
                                        style = AppTypography.body,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    onAddToPlaylist(playlist.id)
                                    menuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }

                    // Info, Share, Rename — shown in every song row, including playlist detail
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_info),
                                color = colors.textPrimary,
                                style = AppTypography.body
                            )
                        },
                        onClick = { menuExpanded = false; onInfo() },
                        enabled = !isOperating,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.action_info),
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_share),
                                color = colors.textPrimary,
                                style = AppTypography.body
                            )
                        },
                        onClick = { menuExpanded = false; onShare() },
                        enabled = !isOperating,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.action_share),
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_rename),
                                color = colors.textPrimary,
                                style = AppTypography.body
                            )
                        },
                        onClick = { menuExpanded = false; onRename() },
                        enabled = !isOperating,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.action_rename),
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    // "Delete Permanently" as LAST item, separated by hairline divider (Never in playlist detail)
                    if (currentPlaylistId == null && onDeletePermanently != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = colors.hairline
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.delete_permanently),
                                    color = colors.destructive,
                                    style = AppTypography.body.copy(fontWeight = FontWeight.Medium)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeletePermanently()
                            },
                            enabled = !isDeleting,
                            leadingIcon = {
                                if (isDeleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = colors.destructive
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = colors.destructive,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("delete_permanently_item_${track.id}")
                        )
                    }
                }
            }
        }
    }
}

/**
 * Animated 3-bar equalizer overlay for currently playing song artwork.
 */
@Composable
fun AnimatedEqualizerBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val shouldAnimate = isPlaying && !isAnimationDisabled

    val infiniteTransition = rememberInfiniteTransition(label = "eq_bars")
    val height1 by if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
    } else {
        remember(isPlaying) { mutableFloatStateOf(if (isPlaying) 0.7f else 0.4f) }
    }

    val height2 by if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 0.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(380, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
    } else {
        remember(isPlaying) { mutableFloatStateOf(if (isPlaying) 0.9f else 0.5f) }
    }

    val height3 by if (shouldAnimate) {
        infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(520, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
    } else {
        remember(isPlaying) { mutableFloatStateOf(if (isPlaying) 0.6f else 0.3f) }
    }

    val colors = AppThemeTokens.colors

    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.40f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.5.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.size(width = 18.dp, height = 18.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height1)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.accentText)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height2)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.accentText)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(height3)
                    .clip(RoundedCornerShape(1.dp))
                    .background(colors.accentText)
            )
        }
    }
}

/**
 * Collection DETAIL screen (Album, Artist, Folder, Playlist):
 * - Header like the reference: artwork ~96dp with 20dp radius on the left
 * - Title 24sp Bold, subtitle 13sp textSecondary
 * - Two equal-width pill buttons: "Play" (AppPrimaryButton) and "Shuffle" (AppSecondaryButton)
 */
@Composable
fun CollectionDetailView(
    title: String,
    subtitle: String,
    artTarget: Any?,
    tracks: List<AudioTrack>,
    currentPlayingTrackUri: String?,
    isPlaying: Boolean,
    playlists: List<PlaylistEntity>,
    onBack: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (Long, AudioTrack) -> Unit,
    onInfo: (AudioTrack) -> Unit = {},
    onShare: (AudioTrack) -> Unit = {},
    onRename: (AudioTrack) -> Unit = {},
    showBackButton: Boolean = true,
    currentPlaylistId: Long? = null,
    onRemoveFromPlaylist: ((AudioTrack) -> Unit)? = null,
    onDeletePermanently: ((AudioTrack) -> Unit)? = null,
    isDeletingTrack: Boolean = false,
    operatingTrackId: Long? = null
) {
    val colors = AppThemeTokens.colors

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        if (showBackButton) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTokens.ScreenHorizontalPadding, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconButton(
                    onClick = onBack,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    iconSize = 22.dp,
                    tintOverride = colors.textPrimary
                )
            }
        }

        // Collection Header (Matches Reference: 96dp artwork with 20dp radius on left, 24sp Bold title, Play & Shuffle pill buttons)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTokens.ScreenHorizontalPadding, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(AppTokens.Radius20))
                        .background(colors.surfaceMuted),
                    contentAlignment = Alignment.Center
                ) {
                    MusicThumbnail(
                        artTarget = artTarget?.toString(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AppTypography.collectionHeaderTitle,
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = AppTypography.listSubtitle,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Two Equal-Width Pill Buttons: "Play" & "Shuffle"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppPrimaryButton(
                    text = stringResource(R.string.play),
                    onClick = onPlayAll,
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier.weight(1f)
                )

                AppSecondaryButton(
                    text = stringResource(R.string.shuffle_action),
                    onClick = onShuffleAll,
                    icon = Icons.Default.Shuffle,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tracks List
        SongsListView(
            tracks = tracks,
            currentPlayingTrackUri = currentPlayingTrackUri,
            isPlaying = isPlaying,
            playlists = playlists,
            onPlayTrack = onPlayTrack,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            currentPlaylistId = currentPlaylistId,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onDeletePermanently = onDeletePermanently,
            isDeletingTrack = isDeletingTrack,
            emptyMessage = if (currentPlaylistId != null) stringResource(R.string.playlist_empty) else stringResource(R.string.no_media_found)
        )
    }
}

@Composable
fun AlbumsGridView(
    albums: List<AlbumItem>,
    gridState: LazyGridState = rememberLazyGridState(),
    onAlbumClick: (AlbumItem) -> Unit
) {
    val colors = AppThemeTokens.colors

    if (albums.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.MusicNote,
            title = stringResource(R.string.no_media_found),
            subtitle = stringResource(R.string.empty_music_list)
        )
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 145.dp),
            state = gridState,
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums, key = { it.title }) { album ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTokens.Radius12))
                        .clickable { onAlbumClick(album) }
                        .testTag("album_card_${album.id}")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(AppTokens.Radius16))
                            .background(colors.surfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        MusicThumbnail(
                            artTarget = album.albumArtUri,
                            contentDescription = album.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = album.title,
                        style = AppTypography.listTitle,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.artist} • ${pluralStringResource(R.plurals.songs_count, album.trackCount, album.trackCount)}",
                        style = AppTypography.listSubtitle,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun FoldersListView(
    folders: List<FolderItem>,
    listState: LazyListState = rememberLazyListState(),
    onFolderClick: (FolderItem) -> Unit
) {
    val colors = AppThemeTokens.colors

    if (folders.isEmpty()) {
        EmptyStateView(
            icon = Icons.Default.Folder,
            title = stringResource(R.string.no_media_found),
            subtitle = stringResource(R.string.empty_music_list)
        )
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(folders, key = { it.path }) { folder ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTokens.Radius12))
                        .clickable { onFolderClick(folder) }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                        .testTag("folder_card_${folder.name}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(AppTokens.Radius12))
                            .background(colors.surfaceMuted),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            style = AppTypography.listTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = pluralStringResource(R.plurals.songs_count, folder.trackCount, folder.trackCount),
                            style = AppTypography.listSubtitle,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsView(
    playlists: List<PlaylistEntity>,
    listState: LazyListState = rememberLazyListState(),
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val colors = AppThemeTokens.colors

    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.PlaylistPlay,
                title = stringResource(R.string.playlist_none_title),
                subtitle = stringResource(R.string.playlist_none_subtitle),
                actionButton = {
                    AppPrimaryButton(
                        text = stringResource(R.string.playlist_create),
                        onClick = onCreatePlaylist,
                        icon = Icons.Default.Add
                    )
                }
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTokens.Radius12))
                            .clickable { onPlaylistClick(playlist) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(AppTokens.Radius12))
                                .background(colors.surfaceMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Text(
                            text = playlist.name,
                            style = AppTypography.listTitle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        AppIconButton(
                            onClick = { onDeletePlaylist(playlist) },
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.playlist_delete),
                            iconSize = 20.dp,
                            tintOverride = colors.textSecondary,
                            testTag = "delete_playlist_${playlist.id}"
                        )
                    }
                }
            }
        }

        // Circular Flat FAB for Playlist Creation
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 20.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(onClick = onCreatePlaylist)
                    .testTag("create_playlist_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.playlist_create),
                    tint = colors.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

/**
 * Centered Minimal Empty State:
 * 40dp line icon in textSecondary, one title line, one helper line, optional pill button. No cards.
 */
@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null
) {
    val colors = AppThemeTokens.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = title,
            style = AppTypography.screenTitle.copy(fontSize = 18.sp),
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = AppTypography.body,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(20.dp))
            actionButton()
        }
    }
}

@Preview(name = "Music Tab - Songs Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun MusicTabSongsLightPreview() {
    LilithTheme(darkTheme = false) {
        SongsListView(
            tracks = listOf(
                AudioTrack(id = 1, uri = "uri1", title = "To Speak Of Solitude", artist = "Brambles", durationMs = 261000, album = "Charcoal", albumId = 1, mimeType = "audio/mp3", path = "/music/song1.mp3", folderName = "Music"),
                AudioTrack(id = 2, uri = "uri2", title = "Unsayable", artist = "Brambles", durationMs = 172000, album = "Charcoal", albumId = 1, mimeType = "audio/flac", path = "/music/song2.flac", folderName = "Music")
            ),
            currentPlayingTrackUri = "uri2",
            isPlaying = true,
            playlists = emptyList(),
            onPlayTrack = {},
            onAddToQueue = {},
            onAddToPlaylist = { _, _ -> }
        )
    }
}

@Preview(name = "Music Tab - Album Dark", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun MusicTabAlbumsDarkPreview() {
    LilithTheme(darkTheme = true) {
        AlbumsGridView(
            albums = listOf(
                AlbumItem(id = 1, title = "Charcoal", artist = "Brambles", trackCount = 8, albumArtUri = null),
                AlbumItem(id = 2, title = "Deep Focus", artist = "Ambient Collective", trackCount = 12, albumArtUri = null)
            ),
            onAlbumClick = {}
        )
    }
}

@Preview(name = "Music Tab - Folder Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun MusicTabFoldersLightPreview() {
    LilithTheme(darkTheme = false) {
        FoldersListView(
            folders = listOf(
                FolderItem(name = "Music", path = "/storage/emulated/0/Music", trackCount = 24),
                FolderItem(name = "Downloads", path = "/storage/emulated/0/Download", trackCount = 5)
            ),
            onFolderClick = {}
        )
    }
}

@Preview(name = "Music Tab - Playlist Dark", showBackground = true, backgroundColor = 0xFF0E1117)
@Composable
fun MusicTabPlaylistsDarkPreview() {
    LilithTheme(darkTheme = true) {
        PlaylistsView(
            playlists = listOf(
                PlaylistEntity(id = 1, name = "Favorites", createdAt = 0),
                PlaylistEntity(id = 2, name = "Chill Vibes", createdAt = 0)
            ),
            onPlaylistClick = {},
            onDeletePlaylist = {},
            onCreatePlaylist = {}
        )
    }
}
