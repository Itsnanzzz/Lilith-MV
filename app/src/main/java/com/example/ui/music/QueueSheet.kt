package com.example.ui.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.AudioTrack
import com.example.ui.common.TimeUtils
import com.example.ui.theme.LocalNeumorphicColors
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicCard
import com.example.ui.theme.NeumorphicIconButton
import com.example.ui.theme.neumorphic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<AudioTrack>,
    currentTrack: AudioTrack?,
    onTrackClick: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    val nc = LocalNeumorphicColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = nc.background,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .neumorphic(
                        shape = CircleShape,
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.PRESSED
                    )
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .neumorphic(
                                shape = CircleShape,
                                level = NeumorphLevel.SMALL,
                                style = NeumorphStyle.RAISED
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = nc.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.queue_title) + " (${queue.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = nc.textPrimary
                    )
                }

                if (queue.isNotEmpty()) {
                    NeumorphicIconButton(
                        onClick = onClearQueue,
                        level = NeumorphLevel.SMALL,
                        size = 38.dp,
                        modifier = Modifier.testTag("clear_queue_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_queue),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    NeumorphicCard(
                        shape = RoundedCornerShape(20.dp),
                        level = NeumorphLevel.SMALL,
                        style = NeumorphStyle.PRESSED,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.queue_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = nc.textSecondary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = queue,
                        key = { index, item -> "queue_${item.uri}_$index" }
                    ) { index, track ->
                        val isCurrent = currentTrack?.uri == track.uri

                        NeumorphicCard(
                            shape = RoundedCornerShape(16.dp),
                            level = NeumorphLevel.SMALL,
                            style = if (isCurrent) NeumorphStyle.PRESSED else NeumorphStyle.RAISED,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onTrackClick(index) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Index badge
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .neumorphic(
                                            shape = CircleShape,
                                            level = NeumorphLevel.SMALL,
                                            style = if (isCurrent) NeumorphStyle.RAISED else NeumorphStyle.PRESSED
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) nc.accent else nc.textSecondary
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isCurrent) nc.accent else nc.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${TimeUtils.formatDuration(track.durationMs)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = nc.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Reorder up
                                NeumorphicIconButton(
                                    onClick = { onMoveUp(index) },
                                    enabled = index > 0,
                                    level = NeumorphLevel.SMALL,
                                    size = 32.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Naik",
                                        tint = if (index > 0) nc.textPrimary else nc.textSecondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Reorder down
                                NeumorphicIconButton(
                                    onClick = { onMoveDown(index) },
                                    enabled = index < queue.size - 1,
                                    level = NeumorphLevel.SMALL,
                                    size = 32.dp
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Turun",
                                        tint = if (index < queue.size - 1) nc.textPrimary else nc.textSecondary.copy(alpha = 0.35f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Remove
                                NeumorphicIconButton(
                                    onClick = { onRemove(index) },
                                    level = NeumorphLevel.SMALL,
                                    size = 32.dp,
                                    modifier = Modifier.testTag("remove_from_queue_$index")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Hapus",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
