package com.example.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.UpdateCheckResult
import com.example.data.UpdateUrlValidator
import com.example.ui.theme.AppPrimaryButton
import com.example.ui.theme.AppSecondaryButton
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UpdateAvailableDialog(
    update: UpdateCheckResult.UpdateAvailable,
    installedVersion: String,
    onDownload: () -> Unit,
    onOpenReleasePage: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = AppThemeTokens.colors
    val context = LocalContext.current

    val publishedDateText = remember(update.publishedAt) {
        formatUpdatePublishedDate(update.publishedAt)
    }
    val apkSizeText = remember(update.apkSize, context) {
        update.apkSize?.takeIf { it > 0L }?.let { bytes ->
            Formatter.formatShortFileSize(context, bytes)
        }
    }

    BoxWithConstraints {
        val maxNotesHeight = (maxHeight * 0.40f).coerceAtLeast(120.dp)

        AlertDialog(
            onDismissRequest = onDismiss,
            shape = RoundedCornerShape(AppTokens.Radius24),
            containerColor = colors.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.update_dialog_title),
                        style = AppTypography.screenTitle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("update_dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = colors.textSecondary
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.update_dialog_subtitle,
                            update.version,
                            installedVersion
                        ),
                        style = AppTypography.listTitle.copy(fontSize = 14.sp),
                        color = colors.textPrimary
                    )

                    if (publishedDateText.isNotEmpty() || apkSizeText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (publishedDateText.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.update_dialog_published,
                                    publishedDateText
                                ),
                                style = AppTypography.caption,
                                color = colors.textSecondary
                            )
                        }
                        if (apkSizeText != null) {
                            Text(
                                text = stringResource(R.string.update_dialog_size, apkSizeText),
                                style = AppTypography.caption,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxNotesHeight)
                            .clip(RoundedCornerShape(AppTokens.Radius12))
                            .background(colors.surfaceMuted)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = update.notes.ifBlank {
                                stringResource(R.string.update_dialog_no_notes)
                            },
                            style = AppTypography.body,
                            color = colors.textPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppPrimaryButton(
                        text = stringResource(R.string.update_dialog_download),
                        onClick = onDownload,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        testTag = "update_dialog_download_button"
                    )
                    Text(
                        text = stringResource(R.string.update_dialog_browser_hint),
                        style = AppTypography.caption,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    AppSecondaryButton(
                        text = stringResource(R.string.update_dialog_release_page),
                        onClick = onOpenReleasePage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        testTag = "update_dialog_release_page_button"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = onSkipVersion,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("update_dialog_skip_button")
                        ) {
                            Text(
                                text = stringResource(R.string.update_dialog_skip),
                                style = AppTypography.label.copy(fontSize = 13.sp),
                                color = colors.textSecondary
                            )
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("update_dialog_later_button")
                        ) {
                            Text(
                                text = stringResource(R.string.update_dialog_later),
                                style = AppTypography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.accentText
                            )
                        }
                    }
                }
            }
        )
    }
}

fun openValidatedUpdateUrl(
    context: Context,
    rawUrl: String,
    isAssetUrl: Boolean,
    onFailed: () -> Unit
) {
    val safeUrl = if (isAssetUrl) {
        UpdateUrlValidator.validateAssetDownloadUrl(rawUrl)
    } else {
        UpdateUrlValidator.validateReleasePageUrl(rawUrl)
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))
    if (context !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        onFailed()
    } catch (_: Exception) {
        onFailed()
    }
}

fun formatUpdateTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault()
    ).format(Date(timestampMs))
}

private fun formatUpdatePublishedDate(isoTimestamp: String): String {
    val trimmed = isoTimestamp.trim()
    if (trimmed.isEmpty()) return ""
    return try {
        val instant = Instant.parse(trimmed)
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date.from(instant))
    } catch (_: Exception) {
        trimmed.take(10)
    }
}
