package com.example.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AudioFormatInfo
import com.example.ui.theme.NeumorphLevel
import com.example.ui.theme.NeumorphStyle
import com.example.ui.theme.NeumorphicTheme
import com.example.ui.theme.NeumorphicTokens
import com.example.ui.theme.neumorphic
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
    val colors = NeumorphicTheme.colors
    val isLossless = formatInfo.isLossless
    val textColor = textColorOverride ?: if (isLossless) colors.accent else colors.textSecondary
    val label = formatInfo.getBadgeLabel()

    val badgeShape = RoundedCornerShape(NeumorphicTokens.RadiusPill)
    val horizontalPadding = if (showDetails) 8.dp else 5.dp
    val verticalPadding = if (showDetails) 3.dp else 1.5.dp
    val iconSize = if (showDetails) 11.dp else 9.dp
    val textSize = if (showDetails) 10.sp else 8.5.sp

    Box(
        modifier = modifier
            .neumorphic(
                shape = badgeShape,
                level = NeumorphLevel.SMALL,
                style = NeumorphStyle.PRESSED,
                lightShadowColorOverride = lightShadowOverride ?: colors.lightShadow,
                darkShadowColorOverride = darkShadowOverride ?: colors.darkShadow,
                backgroundColorOverride = surfaceOverride ?: colors.surface
            )
            .clip(badgeShape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .testTag("media_format_badge"),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isLossless) Icons.Default.HighQuality else Icons.Default.GraphicEq,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(iconSize)
            )
            Text(
                text = " " + if (showDetails) formatInfo.getDetailsSummary() else label,
                color = textColor,
                fontSize = textSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
        }
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

