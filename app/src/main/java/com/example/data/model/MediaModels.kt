package com.example.data.model

import android.net.Uri
import android.os.Build
import java.io.File

data class AudioTrack(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long = 0L,
    val durationMs: Long = 0L,
    val path: String = "",
    val folderName: String = "",
    val mimeType: String? = null,
    val albumArtUri: String? = null,
    val size: Long = 0L,
    val displayName: String = "",
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val relativePath: String = ""
) {
    val extension: String
        get() {
            if (displayName.contains('.')) return displayName.substringAfterLast('.')
            if (path.contains('.')) return path.substringAfterLast('.')
            return ""
        }

    val fullDisplayName: String
        get() {
            if (displayName.isNotBlank()) return displayName
            if (path.isNotBlank()) return File(path).name
            val ext = extension
            return if (ext.isNotEmpty()) "$title.$ext" else title
        }

    val baseName: String
        get() {
            if (displayName.isNotBlank() && displayName.contains('.')) return displayName.substringBeforeLast('.')
            if (path.isNotBlank() && path.contains('.')) return File(path).nameWithoutExtension
            return title
        }

    val folderPath: String
        get() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePath.isNotBlank()) {
                val cleanRel = if (relativePath.endsWith('/')) relativePath else "$relativePath/"
                return "$cleanRel$fullDisplayName"
            }
            if (path.isNotBlank()) return path
            return fullDisplayName
        }
}

data class VideoItem(
    val id: Long,
    val uri: String,
    val title: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0L,
    val dateModified: Long = 0L,
    val path: String = "",
    val folderName: String = "",
    val thumbnailUri: String? = null,
    val displayName: String = "",
    val mimeType: String = "",
    val dateAdded: Long = 0L,
    val relativePath: String = ""
) {
    val extension: String
        get() {
            if (displayName.contains('.')) return displayName.substringAfterLast('.')
            if (path.contains('.')) return path.substringAfterLast('.')
            return ""
        }

    val fullDisplayName: String
        get() {
            if (displayName.isNotBlank()) return displayName
            if (path.isNotBlank()) return File(path).name
            val ext = extension
            return if (ext.isNotEmpty()) "$title.$ext" else title
        }

    val baseName: String
        get() {
            if (displayName.isNotBlank() && displayName.contains('.')) return displayName.substringBeforeLast('.')
            if (path.isNotBlank() && path.contains('.')) return File(path).nameWithoutExtension
            return title
        }

    val folderPath: String
        get() {
            if (relativePath.isNotBlank()) {
                val cleanRel = if (relativePath.endsWith('/')) relativePath else "$relativePath/"
                return "$cleanRel$fullDisplayName"
            }
            if (path.isNotBlank()) return path
            return fullDisplayName
        }
}

data class VideoExtraInfo(
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val frameRate: String = "Unknown",
    val bitrate: String = "Unknown"
)

data class SongExtraInfo(
    val codec: String = "Unknown",
    val bitrate: String = "Unknown",
    val sampleRate: String = "Unknown",
    val bitDepth: String = "Unknown",
    val channels: String = "Unknown"
)

data class AudioFormatInfo(
    val codec: String = "UNKNOWN",
    val isLossless: Boolean = false,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 0,
    val bitDepth: Int = 0,
    val channelCount: Int = 2
) {
    fun getBadgeLabel(): String = if (isLossless) "LOSSLESS" else "LOSSY"

    fun getDetailsSummary(): String {
        val parts = mutableListOf<String>()
        if (codec.isNotEmpty() && codec != "UNKNOWN") parts.add(codec)
        if (bitDepth > 0) parts.add("${bitDepth}-bit")
        if (sampleRateHz > 0) {
            val khz = sampleRateHz / 1000.0
            parts.add(if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else "%.1f kHz".format(khz))
        }
        if (bitrateKbps > 0) parts.add("$bitrateKbps kbps")
        return parts.joinToString(" • ")
    }
}

data class AlbumItem(
    val id: Long,
    val title: String,
    val artist: String,
    val trackCount: Int,
    val albumArtUri: String? = null
)

data class ArtistItem(
    val name: String,
    val trackCount: Int
)

data class FolderItem(
    val name: String,
    val path: String,
    val trackCount: Int,
    val isVideo: Boolean = false
)

data class SubtitleTrackInfo(
    val id: String,
    val label: String,
    val language: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val isExternal: Boolean = false,
    val isSelected: Boolean = false
)

data class SubtitleSettings(
    val fontSizeSp: Float = 18f,
    val textColorHex: Long = 0xFFFFFFFF,
    val backgroundColorHex: Long = 0x99000000,
    val delayMs: Long = 0L
)

enum class AspectRatioMode {
    FIT, FILL, ZOOM
}
