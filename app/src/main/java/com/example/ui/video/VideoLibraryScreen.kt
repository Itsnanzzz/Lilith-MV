package com.example.ui.video

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.FolderItem
import com.example.data.model.VideoItem
import com.example.ui.common.TimeUtils
import com.example.ui.theme.AppChip
import com.example.ui.theme.AppIconButton
import com.example.ui.theme.AppSearchField
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import com.example.ui.theme.LilithTheme
import com.example.ui.theme.rememberIsAnimationDisabled
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Clean, minimal flat Video Library Screen with Swipeable Folder Pager:
 * - 40dp AppSearchField with two 40dp tonal circular buttons for SAF file picker and refresh
 * - 32dp AppChip filter row for video folders with smooth auto-scrolling
 * - HorizontalPager for "All" + each folder page
 * - Flat list of video rows with 16:9 rounded thumbnails (12dp radius), duration badge pill,
 *   15sp SemiBold title, 13sp folder/resolution metadata, and three-dot action menu.
 */
@Composable
fun VideoLibraryScreen(
    viewModel: VideoLibraryViewModel,
    onVideoSelected: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeTokens.colors
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isAnimationDisabled = rememberIsAnimationDisabled()
    val snackbarHostState = remember { SnackbarHostState() }

    val safVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.openSafVideo(it) }
    }

    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onIntentSenderResult(result.resultCode == Activity.RESULT_OK)
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onStoragePermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        viewModel.actionEvents.collect { event ->
            when (event) {
                is VideoActionEvent.LaunchIntentSender -> {
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(event.intentSender).build()
                    )
                }
                is VideoActionEvent.RequestStoragePermission -> {
                    storagePermissionLauncher.launch(event.permission)
                }
                is VideoActionEvent.ShowMessage -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    val onShareVideo: (VideoItem) -> Unit = { video ->
        try {
            val uri = Uri.parse(video.uri)
            if (uri.scheme == "file") {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.cant_share_file))
                }
            } else {
                val mime = video.mimeType.takeIf { it.isNotBlank() } ?: "video/*"
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newUri(context.contentResolver, "video", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, context.getString(R.string.share_video_title))
                context.startActivity(chooser)
            }
        } catch (_: Exception) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.cant_share_file))
            }
        }
    }

    // Single source of truth for selected folder by name
    var selectedFolderName by rememberSaveable { mutableStateOf<String?>(null) }

    val pageCount = 1 + uiState.folders.size

    val initialPage = remember(uiState.folders) {
        val currentName = selectedFolderName
        if (currentName == null) 0
        else {
            val idx = uiState.folders.indexOfFirst { it.name == currentName }
            if (idx >= 0) idx + 1 else 0
        }
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { pageCount }
    )

    val chipsLazyRowState = rememberLazyListState()

    // Sync selectedFolderName when pager settles on a new page, and auto-scroll chips LazyRow
    LaunchedEffect(pagerState.settledPage, uiState.folders) {
        val page = pagerState.settledPage
        val folderName = if (page == 0 || page > uiState.folders.size) null else uiState.folders[page - 1].name
        selectedFolderName = folderName
        if (page < pageCount) {
            chipsLazyRowState.animateScrollToItem(page)
        }
    }

    // Keep user on the same folder when folder list updates.
    // Fall back to "All" (page 0) if current folder disappears.
    LaunchedEffect(uiState.folders) {
        val currentName = selectedFolderName
        val targetPage = if (currentName == null) 0 else {
            val idx = uiState.folders.indexOfFirst { it.name == currentName }
            if (idx >= 0) idx + 1 else 0
        }
        if (pagerState.currentPage != targetPage && targetPage < pagerState.pageCount) {
            pagerState.scrollToPage(targetPage)
        }
    }

    // Hoisted per-page scroll states keyed by folder name ("All" for page 0)
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val activeKeys = remember(uiState.folders) { setOf("All") + uiState.folders.map { it.name } }
    LaunchedEffect(activeKeys) {
        listStates.keys.retainAll(activeKeys)
    }

    val onChipClick: (Int) -> Unit = { targetPage ->
        coroutineScope.launch {
            val distance = abs(targetPage - pagerState.currentPage)
            if (isAnimationDisabled || distance > 2) {
                pagerState.scrollToPage(targetPage)
            } else {
                pagerState.animateScrollToPage(targetPage)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .testTag("video_library_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Search Bar & Action Buttons (Matches Music Tab Top Row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppTokens.ScreenHorizontalPadding, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppSearchField(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholder = stringResource(R.string.search_placeholder),
                    modifier = Modifier.weight(1f),
                    testTag = "search_video_input"
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Open Video from file system (SAF) - 40dp tonal circle button
                AppIconButton(
                    onClick = { safVideoLauncher.launch(arrayOf("video/*")) },
                    icon = Icons.Default.FileOpen,
                    contentDescription = stringResource(R.string.open_saf_file),
                    isTonalCircle = true,
                    iconSize = 20.dp,
                    testTag = "open_saf_video_button"
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Refresh library - 40dp tonal circle button
                AppIconButton(
                    onClick = { viewModel.scanVideos() },
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.scan_library),
                    isTonalCircle = true,
                    iconSize = 20.dp,
                    testTag = "refresh_videos_button"
                )
            }

            // 2. Folder filter chips row (Auto-scrolls to selected chip with end padding)
            LazyRow(
                state = chipsLazyRowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(
                    start = AppTokens.ScreenHorizontalPadding,
                    end = AppTokens.ScreenHorizontalPadding + 16.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "All") {
                    val isSelected = pagerState.currentPage == 0
                    AppChip(
                        text = stringResource(R.string.filter_all_count, uiState.allVideos.size),
                        selected = isSelected,
                        onClick = { onChipClick(0) },
                        testTag = "chip_video_folder_all"
                    )
                }
                itemsIndexed(
                    items = uiState.folders,
                    key = { _, folder -> folder.name }
                ) { index, folder ->
                    val pageIndex = index + 1
                    val isSelected = pagerState.currentPage == pageIndex
                    AppChip(
                        text = "${folder.name} (${folder.trackCount})",
                        selected = isSelected,
                        onClick = { onChipClick(pageIndex) },
                        testTag = "chip_video_folder_${folder.name.lowercase()}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Swipeable Horizontal Pager across folder pages
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
                        CircularProgressIndicator(
                            color = colors.accentText,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0,
                        key = { page ->
                            if (page == 0) "All" else uiState.folders.getOrNull(page - 1)?.name ?: "page_$page"
                        }
                    ) { page ->
                        val pageKey = if (page == 0) "All" else uiState.folders.getOrNull(page - 1)?.name ?: "page_$page"
                        val listState = listStates.getOrPut(pageKey) { LazyListState() }

                        val displayVideos = remember(uiState.allVideos, uiState.folders, uiState.searchQuery, page) {
                            val folderVideos = if (page == 0) {
                                uiState.allVideos
                            } else {
                                val fName = uiState.folders.getOrNull(page - 1)?.name
                                if (fName != null) uiState.allVideos.filter { it.folderName == fName } else emptyList()
                            }
                            if (uiState.searchQuery.isNotBlank()) {
                                val q = uiState.searchQuery.lowercase()
                                folderVideos.filter { it.title.lowercase().contains(q) }
                            } else {
                                folderVideos
                            }
                        }

                        if (displayVideos.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = colors.textSecondary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.empty_video_list),
                                        style = AppTypography.body.copy(fontWeight = FontWeight.Medium),
                                        color = colors.textPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.empty_video_subtitle),
                                        style = AppTypography.caption,
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = AppTokens.ScreenHorizontalPadding,
                                    top = 6.dp,
                                    end = AppTokens.ScreenHorizontalPadding,
                                    bottom = 20.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = displayVideos,
                                    key = { it.uri }
                                ) { video ->
                                    VideoCardItem(
                                        video = video,
                                        onClick = { onVideoSelected(video) },
                                        isOperating = uiState.operatingVideoId == video.id,
                                        onInfoClick = { viewModel.showInfo(video) },
                                        onShareClick = { onShareVideo(video) },
                                        onRenameClick = { viewModel.showRenameDialog(video) },
                                        onDeleteClick = { viewModel.showDeleteDialog(video) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(AppTokens.Radius12),
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                    actionColor = colors.onAccent
                )
            }
        )
    }

    // --- Action Dialogs ---
    uiState.videoForInfo?.let { video ->
        VideoInfoDialog(
            video = video,
            onDismiss = { viewModel.dismissInfo() },
            onCopyPath = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.copied))
                }
            },
            loadExtraInfo = { viewModel.loadExtraInfo(it) }
        )
    }

    uiState.videoToRename?.let { video ->
        VideoRenameDialog(
            video = video,
            isRenaming = uiState.isRenamingVideo,
            onConfirm = { newFullName -> viewModel.confirmRename(video, newFullName) },
            onDismiss = { viewModel.dismissRenameDialog() },
            validateName = { rawText -> viewModel.validateNewName(video, rawText) }
        )
    }

    uiState.videoToDelete?.let { video ->
        VideoDeleteConfirmationDialog(
            video = video,
            isDeleting = uiState.isDeletingVideo,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }
}

/**
 * Clean Flat Video Row Item:
 * 16:9 thumbnail on the left (12dp radius) with duration badge, title & metadata in the middle,
 * and three-dot more options action menu on the right.
 */
@Composable
fun VideoCardItem(
    video: VideoItem,
    onClick: () -> Unit,
    isOperating: Boolean = false,
    onInfoClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onRenameClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {}
) {
    val colors = AppThemeTokens.colors
    val itemShape = RoundedCornerShape(AppTokens.Radius12)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(itemShape)
            .background(colors.surface)
            .padding(vertical = 4.dp, horizontal = 4.dp)
            .testTag("video_item_${video.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left & middle content: clickable to open/play video
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(itemShape)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail on the LEFT (16:9 aspect ratio, 12dp rounded corners)
            Box(
                modifier = Modifier
                    .width(124.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(AppTokens.Radius12))
                    .background(colors.surfaceMuted),
                contentAlignment = Alignment.Center
            ) {
                // Background placeholder icon
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = colors.textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )

                val context = LocalContext.current
                val targetUri = video.thumbnailUri?.takeIf { it.isNotBlank() } ?: video.uri
                if (targetUri.isNotBlank()) {
                    val imageRequest = remember(targetUri) {
                        ImageRequest.Builder(context)
                            .data(Uri.parse(targetUri))
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = video.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Duration badge at bottom right (dark pill with white 10sp text)
                if (video.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(RoundedCornerShape(AppTokens.RadiusPill))
                            .background(Color.Black.copy(alpha = 0.68f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = TimeUtils.formatDuration(video.durationMs),
                            color = Color.White,
                            style = AppTypography.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Video Title & Metadata in the MIDDLE
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = video.title,
                    style = AppTypography.listTitle,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.folderName,
                        style = AppTypography.listSubtitle,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.height > 0) {
                        Text(
                            text = " • ${video.height}p",
                            style = AppTypography.listSubtitle.copy(fontWeight = FontWeight.Medium),
                            color = colors.accentText,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Three-Dot Action Menu Button on the RIGHT (isolated from play click)
        VideoRowTrailingMenu(
            video = video,
            isOperating = isOperating,
            onInfoClick = onInfoClick,
            onShareClick = onShareClick,
            onRenameClick = onRenameClick,
            onDeleteClick = onDeleteClick
        )
    }
}

/**
 * Trailing Three-Dot Action Menu for video rows:
 * - Flat 48dp icon button with 20dp icon, textSecondary tint
 * - Anchored dropdown menu with Info, Share, Rename, divider, and Delete
 */
@Composable
fun VideoRowTrailingMenu(
    video: VideoItem,
    isOperating: Boolean,
    onInfoClick: () -> Unit,
    onShareClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppThemeTokens.colors
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AppIconButton(
            onClick = { menuExpanded = true },
            icon = Icons.Default.MoreVert,
            contentDescription = stringResource(R.string.more_options),
            iconSize = 20.dp,
            tintOverride = colors.textSecondary,
            testTag = "video_more_button_${video.id}"
        )

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier
                .widthIn(min = 180.dp, max = 220.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(AppTokens.Radius16))
                .background(colors.surface, shape = RoundedCornerShape(AppTokens.Radius16))
        ) {
            // 1. Info
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_info),
                        color = colors.textPrimary,
                        style = AppTypography.body
                    )
                },
                onClick = {
                    menuExpanded = false
                    onInfoClick()
                },
                enabled = !isOperating,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(R.string.action_info),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.testTag("video_menu_info_${video.id}")
            )

            // 2. Share
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_share),
                        color = colors.textPrimary,
                        style = AppTypography.body
                    )
                },
                onClick = {
                    menuExpanded = false
                    onShareClick()
                },
                enabled = !isOperating,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.action_share),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.testTag("video_menu_share_${video.id}")
            )

            // 3. Rename
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.action_rename),
                        color = colors.textPrimary,
                        style = AppTypography.body
                    )
                },
                onClick = {
                    menuExpanded = false
                    onRenameClick()
                },
                enabled = !isOperating,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_rename),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier.testTag("video_menu_rename_${video.id}")
            )

            // Hairline divider
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = colors.hairline
            )

            // 4. Delete
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.delete),
                        color = colors.destructive,
                        style = AppTypography.body.copy(fontWeight = FontWeight.Medium)
                    )
                },
                onClick = {
                    menuExpanded = false
                    onDeleteClick()
                },
                enabled = !isOperating,
                leadingIcon = {
                    if (isOperating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.destructive
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = stringResource(R.string.delete),
                            tint = colors.destructive,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                modifier = Modifier.testTag("video_menu_delete_${video.id}")
            )
        }
    }
}

@Preview(name = "Video Library - All Light", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun VideoLibraryPreview() {
    LilithTheme(darkTheme = false) {
        VideoCardItem(
            video = VideoItem(
                id = 1L,
                uri = "",
                title = "Sample Video Presentation 2024",
                durationMs = 125000L,
                width = 1920,
                height = 1080,
                folderName = "Camera"
            ),
            onClick = {},
            isOperating = false,
            onInfoClick = {},
            onShareClick = {},
            onRenameClick = {},
            onDeleteClick = {}
        )
    }
}
