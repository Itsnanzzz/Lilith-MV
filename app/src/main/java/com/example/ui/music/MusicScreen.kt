package com.example.ui.music

import android.Manifest
import android.app.Activity
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.db.PlaylistEntity
import com.example.data.model.AlbumItem
import com.example.data.model.ArtistItem
import com.example.data.model.AudioTrack
import com.example.data.model.FolderItem
import com.example.playback.AudioFormatDetector
import com.example.ui.common.MediaFormatBadge
import com.example.ui.common.MusicThumbnail
import com.example.ui.common.TimeUtils
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicCard
import com.example.ui.theme.NeumorphicChip
import com.example.ui.theme.NeumorphicIconButton
import com.example.ui.theme.NeumorphicTextField
import com.example.ui.theme.NeumorphicTheme
import com.example.ui.theme.NeumorphicTokens
import com.example.ui.theme.neumorphic
import kotlinx.coroutines.launch

@Composable
fun MusicScreen(
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier
) {
    val colors = NeumorphicTheme.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val currentPlayingTrack by viewModel.playerManager.currentTrack.collectAsState()

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("music_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Search & Action Header: Slimmer 40dp height, same horizontal length, 48dp touch targets
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeumorphicTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.search_placeholder), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cari",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("search_music_input")
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Open SAF audio button: visual 40dp inside 48dp touch target box
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NeumorphicIconButton(
                        onClick = { safAudioLauncher.launch(arrayOf("audio/*")) },
                        size = 40.dp,
                        level = NeumorphLevel.SMALL,
                        contentDescription = stringResource(R.string.open_saf_file),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.FileOpen,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.testTag("open_saf_audio_button")
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Rescan Library button: visual 40dp inside 48dp touch target box
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    NeumorphicIconButton(
                        onClick = { viewModel.scanMediaLibrary() },
                        size = 40.dp,
                        level = NeumorphLevel.SMALL,
                        contentDescription = stringResource(R.string.scan_library),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.testTag("rescan_library_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 2. Compact Filter Chips: Lagu, Album, Artis, Folder, Playlist (fits 1 row without scroll on ~360dp phone)
            val tabs = listOf(
                MusicTab.SONGS to stringResource(R.string.tab_songs),
                MusicTab.ALBUMS to stringResource(R.string.tab_albums),
                MusicTab.ARTISTS to stringResource(R.string.tab_artists),
                MusicTab.FOLDERS to stringResource(R.string.tab_folders),
                MusicTab.PLAYLISTS to stringResource(R.string.tab_playlists)
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                val availableWidth = maxWidth
                if (availableWidth >= 320.dp) {
                    // Shared row width across 5 chips with 6dp spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { (tab, title) ->
                            val isSelected = uiState.selectedTab == tab
                            NeumorphicChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected && tab == MusicTab.PLAYLISTS && uiState.selectedPlaylist != null) {
                                        viewModel.selectPlaylist(null)
                                    } else {
                                        viewModel.setTab(tab)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_${tab.name.lowercase()}"),
                                label = {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                } else {
                    // Fallback scrollable row for narrow devices with aligned start padding
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(tabs) { (tab, title) ->
                            val isSelected = uiState.selectedTab == tab
                            NeumorphicChip(
                                selected = isSelected,
                                onClick = {
                                    if (isSelected && tab == MusicTab.PLAYLISTS && uiState.selectedPlaylist != null) {
                                        viewModel.selectPlaylist(null)
                                    } else {
                                        viewModel.setTab(tab)
                                    }
                                },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}"),
                                label = {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        maxLines = 1
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Main Tab Content
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
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else {
                    when (uiState.selectedTab) {
                        MusicTab.SONGS -> {
                            SongsListView(
                                tracks = uiState.filteredTracks,
                                currentPlayingTrackUri = currentPlayingTrack?.uri,
                                playlists = playlists,
                                onPlayTrack = { track -> viewModel.playTrack(track) },
                                onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                onDeletePermanently = { track -> viewModel.requestDeleteTrack(track) },
                                isDeletingTrack = uiState.isDeletingTrack
                            )
                        }

                        MusicTab.ALBUMS -> {
                            if (uiState.selectedAlbum != null) {
                                val albumTracks = uiState.allTracks.filter { it.album == uiState.selectedAlbum?.title }
                                DetailTrackListView(
                                    title = uiState.selectedAlbum?.title ?: "Album",
                                    subtitle = uiState.selectedAlbum?.artist ?: "",
                                    tracks = albumTracks,
                                    currentPlayingTrackUri = currentPlayingTrack?.uri,
                                    playlists = playlists,
                                    onBack = { viewModel.selectAlbum(null) },
                                    onPlayTrack = { track -> viewModel.playTrack(track, albumTracks) },
                                    onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                    onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                    showBackButton = true,
                                    onDeletePermanently = { track -> viewModel.requestDeleteTrack(track) },
                                    isDeletingTrack = uiState.isDeletingTrack
                                )
                            } else {
                                AlbumsGridView(
                                    albums = uiState.albums,
                                    onAlbumClick = { viewModel.selectAlbum(it) }
                                )
                            }
                        }

                        MusicTab.ARTISTS -> {
                            if (uiState.selectedArtist != null) {
                                val artistTracks = uiState.allTracks.filter { it.artist == uiState.selectedArtist?.name }
                                DetailTrackListView(
                                    title = uiState.selectedArtist?.name ?: "Artis",
                                    subtitle = "${artistTracks.size} Lagu",
                                    tracks = artistTracks,
                                    currentPlayingTrackUri = currentPlayingTrack?.uri,
                                    playlists = playlists,
                                    onBack = { viewModel.selectArtist(null) },
                                    onPlayTrack = { track -> viewModel.playTrack(track, artistTracks) },
                                    onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                    onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                    showBackButton = true,
                                    onDeletePermanently = { track -> viewModel.requestDeleteTrack(track) },
                                    isDeletingTrack = uiState.isDeletingTrack
                                )
                            } else {
                                ArtistsListView(
                                    artists = uiState.artists,
                                    onArtistClick = { viewModel.selectArtist(it) }
                                )
                            }
                        }

                        MusicTab.FOLDERS -> {
                            if (uiState.selectedFolder != null) {
                                val folderTracks = uiState.allTracks.filter { it.folderName == uiState.selectedFolder?.name }
                                DetailTrackListView(
                                    title = uiState.selectedFolder?.name ?: "Folder",
                                    subtitle = "${folderTracks.size} Lagu",
                                    tracks = folderTracks,
                                    currentPlayingTrackUri = currentPlayingTrack?.uri,
                                    playlists = playlists,
                                    onBack = { viewModel.selectFolder(null) },
                                    onPlayTrack = { track -> viewModel.playTrack(track, folderTracks) },
                                    onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                    onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                    showBackButton = true,
                                    onDeletePermanently = { track -> viewModel.requestDeleteTrack(track) },
                                    isDeletingTrack = uiState.isDeletingTrack
                                )
                            } else {
                                FoldersListView(
                                    folders = uiState.folders,
                                    onFolderClick = { viewModel.selectFolder(it) }
                                )
                            }
                        }

                        MusicTab.PLAYLISTS -> {
                            val currentPlaylist = uiState.selectedPlaylist
                            if (currentPlaylist != null) {
                                DetailTrackListView(
                                    title = currentPlaylist.name,
                                    subtitle = "${uiState.playlistSongs.size} Lagu",
                                    tracks = uiState.playlistSongs,
                                    currentPlayingTrackUri = currentPlayingTrack?.uri,
                                    playlists = playlists,
                                    onBack = { viewModel.selectPlaylist(null) },
                                    onPlayTrack = { track -> viewModel.playTrack(track, uiState.playlistSongs) },
                                    onAddToQueue = { track -> viewModel.playerManager.addToQueue(track) },
                                    onAddToPlaylist = { pId, track -> viewModel.addTrackToPlaylist(pId, track) },
                                    showBackButton = false,
                                    currentPlaylistId = currentPlaylist.id,
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
                                PlaylistsView(
                                    playlists = playlists,
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

        // Snackbar Host for Playlist Removal & Undo Actions
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
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
}

/**
 * List of songs with consistent 16dp horizontal padding and adequate bottom scroll clearance.
 */
@Composable
fun SongsListView(
    tracks: List<AudioTrack>,
    currentPlayingTrackUri: String?,
    playlists: List<PlaylistEntity>,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (Long, AudioTrack) -> Unit,
    currentPlaylistId: Long? = null,
    onRemoveFromPlaylist: ((AudioTrack) -> Unit)? = null,
    onDeletePermanently: ((AudioTrack) -> Unit)? = null,
    isDeletingTrack: Boolean = false,
    emptyMessage: String = stringResource(R.string.no_media_found)
) {
    val colors = NeumorphicTheme.colors

    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                fontSize = 14.sp,
                color = colors.textSecondary
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 24.dp)
        ) {
            items(
                items = tracks,
                key = { it.uri }
            ) { track ->
                SongRowItem(
                    track = track,
                    isPlaying = track.uri == currentPlayingTrackUri,
                    playlists = playlists,
                    onClick = { onPlayTrack(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { pId -> onAddToPlaylist(pId, track) },
                    currentPlaylistId = currentPlaylistId,
                    onRemoveFromPlaylist = onRemoveFromPlaylist?.let { { it(track) } },
                    onDeletePermanently = onDeletePermanently?.let { { it(track) } },
                    isDeleting = isDeletingTrack
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Slimmer, tidier Neumorphic Song item card.
 * - Lightest shadow level (SMALL).
 * - Album art 48dp with 8dp rounded corners.
 * - Flat 3-dot options button (no shadow inside a raised card, 48dp touch target).
 * - Restyled popup menu with "Hapus dari Playlist" support.
 */
@Composable
fun SongRowItem(
    track: AudioTrack,
    isPlaying: Boolean,
    playlists: List<PlaylistEntity>,
    onClick: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (Long) -> Unit,
    currentPlaylistId: Long? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onDeletePermanently: (() -> Unit)? = null,
    isDeleting: Boolean = false
) {
    val colors = NeumorphicTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    val formatInfo = remember(track.mimeType) {
        AudioFormatDetector.detectFromMime(track.mimeType)
    }

    val itemShape = RoundedCornerShape(12.dp)
    val cardStyle = if (isPlaying) NeumorphStyle.PRESSED else NeumorphStyle.RAISED

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(
                shape = itemShape,
                level = NeumorphLevel.SMALL, // Lightest shadow level
                style = cardStyle,
                lightShadowColorOverride = colors.lightShadow,
                darkShadowColorOverride = colors.darkShadow,
                backgroundColorOverride = colors.surface
            )
            .clip(itemShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp) // Slimmer padding
            .testTag("song_item_${track.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Album Art: 48dp container with rounded corners (8dp)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surface),
                contentAlignment = Alignment.Center
            ) {
                val artTarget = track.albumArtUri?.takeIf { it.isNotBlank() } ?: track.uri
                MusicThumbnail(
                    artTarget = artTarget,
                    contentDescription = track.album,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title & compact row below with badge, artist, and duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isPlaying) colors.accent else colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaFormatBadge(formatInfo = formatInfo)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${track.artist} • ${TimeUtils.formatDuration(track.durationMs)}",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            // Flat 3-dot options button (never a raised circle in a raised card, 48dp touch target, 20dp icon)
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { menuExpanded = true }
                        )
                        .testTag("song_more_button_${track.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opsi",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Popup menu: Neumorphic raised surface, comfortable item height, 20dp icons, 14sp text
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 220.dp)
                        .background(colors.surface, shape = RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.add_to_queue),
                                color = colors.textPrimary,
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            onAddToQueue()
                            menuExpanded = false
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )

                    // "Hapus dari Playlist" entry if viewing inside playlist
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.playlist_remove_song),
                                    color = Color(0xFFE57373),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                onRemoveFromPlaylist()
                                menuExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = Color(0xFFE57373),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        )
                    }

                    // Hide current playlist from "+ playlist" list
                    val eligiblePlaylists = remember(playlists, currentPlaylistId) {
                        playlists.filter { it.id != currentPlaylistId }
                    }

                    if (eligiblePlaylists.isNotEmpty()) {
                        eligiblePlaylists.forEach { playlist ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "+ ${playlist.name}",
                                        color = colors.textPrimary,
                                        fontSize = 14.sp,
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
                                        Icons.Default.PlaylistPlay,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            )
                        }
                    }

                    // "Hapus Permanen" as the LAST item, separated by a divider
                    // Shown in Songs, Album, Artist, Folder; NEVER inside playlist detail
                    if (currentPlaylistId == null && onDeletePermanently != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = colors.textSecondary.copy(alpha = 0.2f)
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.delete_permanently),
                                    color = Color(0xFFE57373),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
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
                                        color = Color(0xFFE57373)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = Color(0xFFE57373),
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

@Composable
fun AlbumsGridView(
    albums: List<AlbumItem>,
    onAlbumClick: (AlbumItem) -> Unit
) {
    val colors = NeumorphicTheme.colors

    if (albums.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_media_found), color = colors.textSecondary)
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(albums, key = { it.title }) { album ->
                val cardShape = RoundedCornerShape(NeumorphicTokens.RadiusMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(
                            shape = cardShape,
                            level = NeumorphLevel.SMALL,
                            style = NeumorphStyle.RAISED,
                            lightShadowColorOverride = colors.lightShadow,
                            darkShadowColorOverride = colors.darkShadow,
                            backgroundColorOverride = colors.surface
                        )
                        .clip(cardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAlbumClick(album) }
                        )
                        .padding(10.dp)
                        .testTag("album_card_${album.id}")
                ) {
                    Column {
                        val imgShape = RoundedCornerShape(12.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(imgShape),
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.artist} • ${album.trackCount} lagu",
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsListView(
    artists: List<ArtistItem>,
    onArtistClick: (ArtistItem) -> Unit
) {
    val colors = NeumorphicTheme.colors

    if (artists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_media_found), color = colors.textSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(artists, key = { it.name }) { artist ->
                val cardShape = RoundedCornerShape(NeumorphicTokens.RadiusMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(
                            shape = cardShape,
                            level = NeumorphLevel.SMALL,
                            style = NeumorphStyle.RAISED,
                            lightShadowColorOverride = colors.lightShadow,
                            darkShadowColorOverride = colors.darkShadow,
                            backgroundColorOverride = colors.surface
                        )
                        .clip(cardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onArtistClick(artist) }
                        )
                        .padding(14.dp)
                        .testTag("artist_card_${artist.name}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .neumorphic(
                                    shape = CircleShape,
                                    level = NeumorphLevel.SMALL,
                                    style = NeumorphStyle.RAISED,
                                    lightShadowColorOverride = colors.lightShadow,
                                    darkShadowColorOverride = colors.darkShadow,
                                    backgroundColorOverride = colors.surface
                                )
                                .clip(CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${artist.trackCount} Lagu",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun FoldersListView(
    folders: List<FolderItem>,
    onFolderClick: (FolderItem) -> Unit
) {
    val colors = NeumorphicTheme.colors

    if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_media_found), color = colors.textSecondary)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(folders, key = { it.path }) { folder ->
                val cardShape = RoundedCornerShape(NeumorphicTokens.RadiusMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neumorphic(
                            shape = cardShape,
                            level = NeumorphLevel.SMALL,
                            style = NeumorphStyle.RAISED,
                            lightShadowColorOverride = colors.lightShadow,
                            darkShadowColorOverride = colors.darkShadow,
                            backgroundColorOverride = colors.surface
                        )
                        .clip(cardShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onFolderClick(folder) }
                        )
                        .padding(14.dp)
                        .testTag("folder_card_${folder.name}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .neumorphic(
                                    shape = RoundedCornerShape(10.dp),
                                    level = NeumorphLevel.SMALL,
                                    style = NeumorphStyle.RAISED,
                                    lightShadowColorOverride = colors.lightShadow,
                                    darkShadowColorOverride = colors.darkShadow,
                                    backgroundColorOverride = colors.surface
                                )
                                .clip(RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "${folder.trackCount} Lagu",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun PlaylistsView(
    playlists: List<PlaylistEntity>,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onDeletePlaylist: (PlaylistEntity) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val colors = NeumorphicTheme.colors

    Box(modifier = Modifier.fillMaxSize()) {
        if (playlists.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Belum ada playlist",
                    fontSize = 14.sp,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                NeumorphicChip(
                    selected = false,
                    onClick = onCreatePlaylist,
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.playlist_create))
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val cardShape = RoundedCornerShape(NeumorphicTokens.RadiusMedium)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .neumorphic(
                                shape = cardShape,
                                level = NeumorphLevel.SMALL,
                                style = NeumorphStyle.RAISED,
                                lightShadowColorOverride = colors.lightShadow,
                                darkShadowColorOverride = colors.darkShadow,
                                backgroundColorOverride = colors.surface
                            )
                            .clip(cardShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onPlaylistClick(playlist) }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .neumorphic(
                                        shape = RoundedCornerShape(10.dp),
                                        level = NeumorphLevel.SMALL,
                                        style = NeumorphStyle.RAISED,
                                        lightShadowColorOverride = colors.lightShadow,
                                        darkShadowColorOverride = colors.darkShadow,
                                        backgroundColorOverride = colors.surface
                                    )
                                    .clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = playlist.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f)
                            )

                            NeumorphicIconButton(
                                onClick = { onDeletePlaylist(playlist) },
                                size = 36.dp,
                                level = NeumorphLevel.SMALL,
                                contentDescription = stringResource(R.string.playlist_delete),
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                modifier = Modifier.testTag("delete_playlist_${playlist.id}")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // Circular Neumorphic FAB for Playlist Creation
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 20.dp)
        ) {
            NeumorphicIconButton(
                onClick = onCreatePlaylist,
                size = 56.dp,
                level = NeumorphLevel.LARGE,
                contentDescription = stringResource(R.string.playlist_create),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(26.dp)
                    )
                },
                modifier = Modifier.testTag("create_playlist_fab")
            )
        }
    }
}

/**
 * Detail Track list view for Album, Artist, Folder, and Playlist.
 * In Playlist detail:
 * - Back button is omitted.
 * - BackHandler navigates back to the playlist list.
 * - Header title is 16sp semi-bold, subtitle 12sp, left-aligned at 16dp horizontal padding.
 * - Songs support "Hapus dari Playlist" with undo Snackbar.
 */
@Composable
fun DetailTrackListView(
    title: String,
    subtitle: String,
    tracks: List<AudioTrack>,
    currentPlayingTrackUri: String?,
    playlists: List<PlaylistEntity>,
    onBack: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onAddToQueue: (AudioTrack) -> Unit,
    onAddToPlaylist: (Long, AudioTrack) -> Unit,
    showBackButton: Boolean = true,
    currentPlaylistId: Long? = null,
    onRemoveFromPlaylist: ((AudioTrack) -> Unit)? = null,
    onDeletePermanently: ((AudioTrack) -> Unit)? = null,
    isDeletingTrack: Boolean = false
) {
    val colors = NeumorphicTheme.colors

    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        if (showBackButton) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NeumorphicIconButton(
                    onClick = onBack,
                    size = 36.dp,
                    level = NeumorphLevel.SMALL,
                    contentDescription = "Kembali",
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        } else {
            // Clean left-aligned header matching list horizontal padding (16dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
            }
        }

        SongsListView(
            tracks = tracks,
            currentPlayingTrackUri = currentPlayingTrackUri,
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
