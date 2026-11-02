package com.example.ui.video

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.decode.VideoFrameDecoder
import com.example.R
import com.example.data.model.VideoItem
import com.example.ui.common.TimeUtils
import com.example.ui.theme.LocalNeumorphicColors
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicCard
import com.example.ui.theme.NeumorphicChip
import com.example.ui.theme.NeumorphicIconButton
import com.example.ui.theme.NeumorphicTextField
import com.example.ui.theme.neumorphic

@Composable
fun VideoLibraryScreen(
    viewModel: VideoLibraryViewModel,
    onVideoSelected: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val nc = LocalNeumorphicColors.current
    val uiState by viewModel.uiState.collectAsState()

    val safVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.openSafVideo(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(nc.background)
            .testTag("video_library_screen")
    ) {
        // Search & SAF Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NeumorphicTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = stringResource(R.string.search_placeholder),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Cari",
                        tint = nc.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_video_input")
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Open Video from file system (SAF)
            NeumorphicIconButton(
                onClick = { safVideoLauncher.launch(arrayOf("video/*")) },
                size = 46.dp,
                modifier = Modifier.testTag("open_saf_video_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileOpen,
                    contentDescription = stringResource(R.string.open_saf_file),
                    tint = nc.accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Refresh library
            NeumorphicIconButton(
                onClick = { viewModel.scanVideos() },
                size = 46.dp,
                modifier = Modifier.testTag("refresh_videos_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.scan_library),
                    tint = nc.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Folder filter chips
        if (uiState.folders.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    NeumorphicChip(
                        selected = uiState.selectedFolder == null,
                        onClick = { viewModel.selectFolder(null) },
                        label = { Text("Semua (${uiState.allVideos.size})", fontSize = 12.sp) }
                    )
                }
                items(uiState.folders) { folder ->
                    NeumorphicChip(
                        selected = uiState.selectedFolder?.name == folder.name,
                        onClick = { viewModel.selectFolder(folder) },
                        label = { Text("${folder.name} (${folder.trackCount})", fontSize = 12.sp) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // Grid of videos
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
                    CircularProgressIndicator(color = nc.accent)
                }
            } else if (uiState.filteredVideos.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    NeumorphicCard(
                        shape = RoundedCornerShape(20.dp),
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.PRESSED,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.empty_video_list),
                                style = MaterialTheme.typography.bodyMedium,
                                color = nc.textSecondary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = uiState.filteredVideos,
                        key = { it.uri }
                    ) { video ->
                        VideoCardItem(
                            video = video,
                            onClick = { onVideoSelected(video) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Neumorphic Video Row Item:
 * Very thin outer/inner padding, thumbnail on the left, title & metadata on the right.
 */
@Composable
fun VideoCardItem(
    video: VideoItem,
    onClick: () -> Unit
) {
    val nc = LocalNeumorphicColors.current

    NeumorphicCard(
        shape = RoundedCornerShape(14.dp),
        level = NeumorphLevel.SMALL,
        style = NeumorphStyle.RAISED,
        contentPadding = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("video_item_${video.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail on the LEFT (zero padding to maximize content)
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .aspectRatio(16f / 10f)
                    .clip(RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                // Background icon placeholder
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = nc.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )

                val context = LocalContext.current
                val targetUri = video.thumbnailUri?.takeIf { it.isNotBlank() } ?: video.uri
                if (targetUri.isNotBlank()) {
                    val imageRequest = remember(targetUri) {
                        ImageRequest.Builder(context)
                            .data(android.net.Uri.parse(targetUri))
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
                        .size(26.dp)
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

                // Duration badge at bottom right
                if (video.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.75f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = TimeUtils.formatDuration(video.durationMs),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Video Title & Metadata on the RIGHT
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = nc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = video.folderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = nc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video.height > 0) {
                        Text(
                            text = " • ${video.height}p",
                            style = MaterialTheme.typography.bodySmall,
                            color = nc.accent,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
