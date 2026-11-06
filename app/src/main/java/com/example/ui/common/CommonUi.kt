package com.example.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import com.example.R
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppSecondaryButton
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioFormatInfo
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicTheme
import com.example.ui.theme.NeumorphicTokens
import com.example.ui.theme.neumorphic
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0) return "0:00"
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatVideoDuration(durationMs: Long): String {
        if (durationMs <= 0) return "00:00"
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

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
}

/**
 * Shared Flat Media Rename Dialog for audio and video files:
 * Pre-fills base name, displays immutable extension suffix, and validates live.
 */
@Composable
fun MediaRenameDialog(
    title: String,
    baseName: String,
    extension: String,
    isRenaming: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    validateName: suspend (String) -> String?,
    textFieldTestTag: String = "rename_media_text_field"
) {
    val colors = AppThemeTokens.colors

    var textState by remember(baseName, extension) {
        mutableStateOf(
            TextFieldValue(
                text = baseName,
                selection = TextRange(0, baseName.length)
            )
        )
    }

    var validationError by remember(baseName, extension) { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    LaunchedEffect(textState.text, baseName, extension) {
        validationError = validateName(textState.text)
    }

    val trimmed = textState.text.trim()
    val isUnchanged = trimmed == baseName
    val canRename = validationError == null && !isUnchanged && trimmed.isNotEmpty() && !isRenaming

    AlertDialog(
        onDismissRequest = { if (!isRenaming) onDismiss() },
        shape = RoundedCornerShape(AppTokens.Radius24),
        containerColor = colors.surface,
        title = {
            Text(
                text = title,
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
                            onValueChange = { if (!isRenaming) textState = it },
                            enabled = !isRenaming,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .testTag(textFieldTestTag),
                            textStyle = AppTypography.body.copy(color = colors.textPrimary, fontSize = 15.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.foregroundAccent)
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
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .testTag("rename_dialog_error_text")
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
                onClick = { if (!isRenaming) onDismiss() },
                testTag = "rename_dialog_cancel_button"
            )
        }
    )
}

/**
 * Neumorphic Inset Badge for Lossless / Lossy technical audio tags.
 * Embossed inset chip using accent color for LOSSLESS.
 */
@Composable
fun MediaFormatBadge(
    formatInfo: AudioFormatInfo,
    modifier: Modifier = Modifier,
    showDetails: Boolean = false,
    surfaceOverride: Color? = null,
    lightShadowOverride: Color? = null,
    darkShadowOverride: Color? = null,
    textColorOverride: Color? = null
) {
    val colors = AppThemeTokens.colors
    val isLossless = formatInfo.isLossless
    val label = if (showDetails) formatInfo.getDetailsSummary() else formatInfo.getBadgeLabel()

    Box(
        modifier = modifier
            .height(16.dp)
            .clip(RoundedCornerShape(AppTokens.RadiusPill))
            .then(
                if (isLossless) {
                    Modifier
                        .background(colors.accent)
                        .then(
                            if (colors.isLightWhiteAccent) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = colors.hairline,
                                    shape = RoundedCornerShape(AppTokens.RadiusPill)
                                )
                            } else Modifier
                        )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        color = colors.hairline,
                        shape = RoundedCornerShape(AppTokens.RadiusPill)
                    )
                }
            )
            .padding(horizontal = 6.dp)
            .testTag("media_format_badge"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isLossless) colors.onAccent else colors.textSecondary,
            style = AppTypography.badge
        )
    }
}

/**
 * Music Thumbnail with default app icon fallback (R.drawable.ic_launcher_fg)
 * when music has no album art or fails to load embedded art.
 */
@Composable
fun MusicThumbnail(
    artTarget: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val imageRequest = remember(artTarget) {
        coil.request.ImageRequest.Builder(context)
            .data(artTarget?.takeIf { it.isNotBlank() })
            .crossfade(true)
            .placeholder(com.example.R.drawable.ic_launcher_fg)
            .error(com.example.R.drawable.ic_launcher_fg)
            .fallback(com.example.R.drawable.ic_launcher_fg)
            .build()
    }

    coil.compose.AsyncImage(
        model = imageRequest,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}
