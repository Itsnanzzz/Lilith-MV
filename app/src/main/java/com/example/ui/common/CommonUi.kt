package com.example.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.theme.AppThemeTokens
import com.example.ui.theme.AppTokens
import com.example.ui.theme.AppTypography
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
}

fun normalizeLanguageTag(locale: Locale?): String {
    val lang = locale?.language?.lowercase(Locale.ROOT)
    return if (lang == "en") "en" else "id"
}

fun logLangDebug(context: android.content.Context, stage: String) {
    val appCompatLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val configLocales = context.resources.configuration.locales.toLanguageTags()
    val localeManagerLocales = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales?.toLanguageTags() ?: "null"
    } else {
        "N/A(<33)"
    }
    val settingsTitle = context.getString(com.example.R.string.settings_title)
    android.util.Log.d(
        "LangDebug",
        "[$stage] appCompatLocales=[$appCompatLocales] configLocales=[$configLocales] localeManagerLocales=[$localeManagerLocales] getString(settings_title)=\"$settingsTitle\""
    )
}

fun getAppLocales(context: android.content.Context): androidx.core.os.LocaleListCompat {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        if (localeManager != null) {
            return androidx.core.os.LocaleListCompat.wrap(localeManager.applicationLocales)
        }
    }
    return androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
}

fun setAppLocales(context: android.content.Context, languageTag: String) {
    com.example.MainActivity.clearLastSyncedLocaleTag()
    val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(languageTag)
    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        if (localeManager != null && localeManager.applicationLocales.toLanguageTags() != languageTag) {
            localeManager.applicationLocales = android.os.LocaleList.forLanguageTags(languageTag)
        }
        try {
            val component = android.content.ComponentName(
                context,
                "androidx.appcompat.app.AppLocalesMetadataHolderService"
            )
            if (context.packageManager.getComponentEnabledSetting(component) !=
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) {
                context.packageManager.setComponentEnabledSetting(
                    component,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            }
        } catch (_: Exception) {
        }
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        try {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (nm != null) {
                val channel = android.app.NotificationChannel(
                    com.example.LilithApplication.CHANNEL_ID_PLAYBACK,
                    context.getAppString(com.example.R.string.notification_channel_name),
                    android.app.NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getAppString(com.example.R.string.notification_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        } catch (_: Exception) {
        }
    }
}

fun android.content.Context.getAppString(
    @androidx.annotation.StringRes resId: Int,
    vararg formatArgs: Any
): String {
    val appLocales = getAppLocales(this)
    val targetLocale = if (!appLocales.isEmpty) appLocales.get(0) else Locale.forLanguageTag("id")
    val localizedContext = if (targetLocale != null) {
        val currentLocale = resources.configuration.locales.get(0)
        if (normalizeLanguageTag(currentLocale) != normalizeLanguageTag(targetLocale)) {
            val config = android.content.res.Configuration(resources.configuration)
            config.setLocale(targetLocale)
            createConfigurationContext(config)
        } else {
            this
        }
    } else {
        this
    }
    return if (formatArgs.isEmpty()) {
        localizedContext.getString(resId)
    } else {
        localizedContext.getString(resId, *formatArgs)
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
    val colors = AppThemeTokens.colors
    val isLossless = formatInfo.isLossless
    val label = if (showDetails) formatInfo.getDetailsSummary() else formatInfo.getBadgeLabel()

    Box(
        modifier = modifier
            .height(16.dp)
            .clip(RoundedCornerShape(AppTokens.RadiusPill))
            .then(
                if (isLossless) {
                    Modifier.background(colors.primary)
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
            color = if (isLossless) colors.onPrimary else colors.textSecondary,
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

