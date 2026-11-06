package com.example.ui.video

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.VideoExtraInfo
import com.example.data.model.VideoItem
import com.example.ui.common.TimeUtils
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppSecondaryButton
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return String.format(Locale.ROOT, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

fun formatTimestamp(seconds: Long): String {
    if (seconds <= 0) return "Unknown"
    val date = Date(seconds * 1000L)
    val sdf = SimpleDateFormat("MMM dd, yyyy, HH:mm", Locale.ROOT)
    return sdf.format(date)
}

/**
 * Flat Video Details Dialog:
 * Displays basic metadata immediately and lazily fetches codecs/bitrate/fps on Dispatchers.IO.
 * Long-press on Path copies it to clipboard and triggers onCopyPath.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoInfoDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onCopyPath: (String) -> Unit,
    loadExtraInfo: suspend (VideoItem) -> VideoExtraInfo
) {
    val colors = AppThemeTokens.colors
    val clipboardManager = LocalClipboardManager.current
    var extraInfo by remember { mutableStateOf<VideoExtraInfo?>(null) }

    LaunchedEffect(video.uri) {
        extraInfo = loadExtraInfo(video)
    }

    val loadingText = stringResource(R.string.info_loading)
    val unknownText = stringResource(R.string.info_unknown)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.video_info_title),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoDetailRow(label = stringResource(R.string.info_name), value = video.fullDisplayName)

                    val displayPath = video.folderPath
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppTokens.Radius8))
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    clipboardManager.setText(AnnotatedString(displayPath))
                                    onCopyPath(displayPath)
                                }
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.info_path),
                            style = AppTypography.caption,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayPath,
                            style = AppTypography.body.copy(fontSize = 13.sp),
                            color = colors.primary
                        )
                    }

                    InfoDetailRow(label = stringResource(R.string.info_size), value = formatFileSize(video.size))
                    InfoDetailRow(label = stringResource(R.string.info_duration), value = TimeUtils.formatVideoDuration(video.durationMs))

                    val resStr = if (video.width > 0 && video.height > 0) "${video.width} x ${video.height}" else unknownText
                    InfoDetailRow(label = stringResource(R.string.info_resolution), value = resStr)

                    val formatStr = video.mimeType.ifEmpty { unknownText }
                    InfoDetailRow(label = stringResource(R.string.info_format), value = formatStr)

                    InfoDetailRow(
                        label = stringResource(R.string.info_video_codec),
                        value = extraInfo?.videoCodec ?: loadingText
                    )
                    InfoDetailRow(
                        label = stringResource(R.string.info_audio_codec),
                        value = extraInfo?.audioCodec ?: loadingText
                    )
                    InfoDetailRow(
                        label = stringResource(R.string.info_frame_rate),
                        value = extraInfo?.frameRate ?: loadingText
                    )
                    InfoDetailRow(
                        label = stringResource(R.string.info_bitrate),
                        value = extraInfo?.bitrate ?: loadingText
                    )

                    val addedStr = if (video.dateAdded > 0) formatTimestamp(video.dateAdded) else unknownText
                    InfoDetailRow(label = stringResource(R.string.info_date_added), value = addedStr)

                    val modStr = if (video.dateModified > 0) formatTimestamp(video.dateModified) else unknownText
                    InfoDetailRow(label = stringResource(R.string.info_date_modified), value = modStr)
                }
            }
        },
        confirmButton = {
            AppSecondaryButton(
                text = stringResource(R.string.close),
                onClick = onDismiss,
                testTag = "info_dialog_close_button"
            )
        }
    )
}

@Composable
private fun InfoDetailRow(label: String, value: String) {
    val colors = AppThemeTokens.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AppTypography.caption,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = AppTypography.body,
            color = colors.textPrimary
        )
    }
}

/**
 * Flat Video Rename Dialog:
 * Pre-fills base name, displays immutable extension suffix, and validates live.
 */
@Composable
fun VideoRenameDialog(
    video: VideoItem,
    isRenaming: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validateName: suspend (String) -> String?
) {
    val colors = AppThemeTokens.colors
    val currentBaseName = remember(video) { video.baseName }
    val extension = remember(video) { video.extension }

    var textState by remember(video) {
        mutableStateOf(
            TextFieldValue(
                text = currentBaseName,
                selection = TextRange(0, currentBaseName.length)
            )
        )
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    LaunchedEffect(textState.text) {
        val currentText = textState.text
        validationError = validateName(currentText)
    }

    val trimmed = textState.text.trim()
    val isUnchanged = trimmed == currentBaseName
    val canRename = validationError == null && !isUnchanged && trimmed.isNotEmpty() && !isRenaming

    AlertDialog(
        onDismissRequest = { if (!isRenaming) onDismiss() },
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.rename_video_title),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(AppTokens.Radius12))
                        .background(colors.surfaceMuted)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = textState,
                            onValueChange = { textState = it },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .testTag("rename_video_text_field"),
                            textStyle = AppTypography.body.copy(color = colors.textPrimary, fontSize = 15.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.primary)
                        )

                        if (extension.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = ".$extension",
                                style = AppTypography.body.copy(
                                    color = colors.textSecondary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                if (validationError != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = validationError ?: "",
                        style = AppTypography.caption,
                        color = colors.destructive,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            AppPrimaryButton(
                text = stringResource(R.string.rename_confirm),
                onClick = {
                    val full = if (extension.isNotEmpty()) "$trimmed.$extension" else trimmed
                    onConfirm(full)
                },
                enabled = canRename,
                modifier = Modifier.testTag("rename_dialog_confirm_button")
            )
        },
        dismissButton = {
            AppSecondaryButton(
                text = stringResource(R.string.rename_cancel),
                onClick = onDismiss,
                testTag = "rename_dialog_cancel_button"
            )
        }
    )
}

/**
 * Permanent Delete Video Confirmation Dialog:
 * Matches song permanent delete dialog with red destructive confirm button.
 */
@Composable
fun VideoDeleteConfirmationDialog(
    video: VideoItem,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors

    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = stringResource(R.string.delete_video_title),
                style = AppTypography.screenTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = video.fullDisplayName,
                    style = AppTypography.listTitle,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.delete_video_message),
                    style = AppTypography.body,
                    color = colors.textSecondary,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                shape = RoundedCornerShape(AppTokens.RadiusPill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.destructive,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                modifier = Modifier.testTag("delete_video_confirm_button")
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.delete),
                    color = Color.White,
                    style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        },
        dismissButton = {
            AppSecondaryButton(
                text = stringResource(R.string.playlist_cancel),
                onClick = onDismiss,
                testTag = "delete_video_cancel_button"
            )
        }
    )
}
