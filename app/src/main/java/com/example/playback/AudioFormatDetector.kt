package com.example.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.example.data.model.AudioFormatInfo
import com.example.data.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object AudioFormatDetector {

    @OptIn(UnstableApi::class)
    fun detectFromTracks(tracks: Tracks, fallbackMime: String? = null): AudioFormatInfo? {
        for (trackGroup in tracks.groups) {
            if (trackGroup.type == C.TRACK_TYPE_AUDIO && trackGroup.isSelected) {
                for (i in 0 until trackGroup.length) {
                    if (trackGroup.isTrackSelected(i)) {
                        return detectFromFormat(trackGroup.getTrackFormat(i), fallbackMime)
                    }
                }
            }
        }
        return null
    }

    @OptIn(UnstableApi::class)
    fun detectFromFormat(format: Format?, fallbackMime: String? = null): AudioFormatInfo {
        if (format == null) {
            return detectFromMime(fallbackMime)
        }

        val sampleMime = format.sampleMimeType ?: fallbackMime ?: ""
        val (codecName, isLossless) = resolveCodecAndLossless(sampleMime, format.codecs)

        val bitrateKbps = if (format.bitrate > 0) {
            format.bitrate / 1000
        } else if (format.averageBitrate > 0) {
            format.averageBitrate / 1000
        } else {
            0
        }

        val sampleRate = if (format.sampleRate > 0) format.sampleRate else 0

        val bitDepth = when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }

        val channels = if (format.channelCount > 0) format.channelCount else 2

        return AudioFormatInfo(
            codec = codecName,
            isLossless = isLossless,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            channelCount = channels
        )
    }

    fun detectFromMime(mimeType: String?): AudioFormatInfo {
        val (codecName, isLossless) = resolveCodecAndLossless(mimeType ?: "", null)
        return AudioFormatInfo(
            codec = codecName,
            isLossless = isLossless,
            bitrateKbps = 0,
            sampleRateHz = 0,
            bitDepth = 0,
            channelCount = 2
        )
    }

    fun detectFromTrackMetadata(
        mimeType: String?,
        displayName: String? = null,
        path: String? = null,
        uri: String? = null,
        contentType: String? = null
    ): AudioFormatInfo {
        val fromMime = resolveCodecAndLossless(mimeType ?: "", null)
        if (fromMime.first != "UNKNOWN") {
            return AudioFormatInfo(codec = fromMime.first, isLossless = fromMime.second)
        }
        val fromContentType = resolveCodecAndLossless(contentType ?: "", null)
        if (fromContentType.first != "UNKNOWN") {
            return AudioFormatInfo(codec = fromContentType.first, isLossless = fromContentType.second)
        }
        val ext = extractExtension(displayName, path, uri)
        val fromExt = resolveCodecFromExtension(ext)
        return if (fromExt != null) {
            AudioFormatInfo(codec = fromExt.first, isLossless = fromExt.second)
        } else {
            AudioFormatInfo()
        }
    }

    suspend fun detectFromFile(context: Context, track: AudioTrack): AudioFormatInfo = withContext(Dispatchers.IO) {
        val uri = try {
            if (track.uri.isNotBlank()) Uri.parse(track.uri) else null
        } catch (_: Exception) {
            null
        }

        var extractorMime: String? = null
        var extractorCodecs: String? = null
        var sampleRateHz = 0
        var bitrateKbps = 0
        var bitDepth = 0
        var channels = 0

        var extractor: MediaExtractor? = null
        try {
            extractor = MediaExtractor()
            var dataSourceSet = false
            if (uri != null) {
                try {
                    extractor.setDataSource(context, uri, null)
                    dataSourceSet = true
                } catch (_: Exception) {
                    try {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            extractor.setDataSource(pfd.fileDescriptor)
                            dataSourceSet = true
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            if (!dataSourceSet && track.path.isNotEmpty() && File(track.path).exists()) {
                extractor.setDataSource(track.path)
                dataSourceSet = true
            }
            if (dataSourceSet) {
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/") || mime.equals("application/ogg", ignoreCase = true)) {
                        extractorMime = mime
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            format.containsKey(MediaFormat.KEY_CODECS_STRING)
                        ) {
                            extractorCodecs = format.getString(MediaFormat.KEY_CODECS_STRING)
                        }
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(0)
                        }
                        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            val br = format.getInteger(MediaFormat.KEY_BIT_RATE)
                            if (br > 0) bitrateKbps = br / 1000
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            if (ch > 0) channels = ch
                        }
                        if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            bitDepth = pcmEncodingToBitDepth(format.getInteger(MediaFormat.KEY_PCM_ENCODING))
                        }
                        if (bitDepth <= 0 && format.containsKey("bits-per-sample")) {
                            bitDepth = try {
                                format.getInteger("bits-per-sample").coerceAtLeast(0)
                            } catch (_: Exception) {
                                0
                            }
                        }
                        break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
        }

        val resolvedFromExtractor = resolveCodecAndLossless(extractorMime ?: "", extractorCodecs)
        val needsRetriever = resolvedFromExtractor.first == "UNKNOWN" ||
            sampleRateHz <= 0 ||
            bitrateKbps <= 0 ||
            (resolvedFromExtractor.second && bitDepth <= 0)

        var retrieverMime: String? = null
        if (needsRetriever) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                var dataSourceSet = false
                if (uri != null) {
                    try {
                        retriever.setDataSource(context, uri)
                        dataSourceSet = true
                    } catch (_: Exception) {
                        try {
                            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                                retriever.setDataSource(pfd.fileDescriptor)
                                dataSourceSet = true
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                if (!dataSourceSet && track.path.isNotEmpty() && File(track.path).exists()) {
                    retriever.setDataSource(track.path)
                    dataSourceSet = true
                }
                if (dataSourceSet) {
                    retrieverMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    if (bitrateKbps <= 0) {
                        val br = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                        if (br != null && br > 0L) {
                            bitrateKbps = (br / 1000L).toInt().coerceAtLeast(0)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (sampleRateHz <= 0) {
                            sampleRateHz = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                                ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        }
                        if (bitDepth <= 0) {
                            bitDepth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                                ?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {
                }
            }
        }

        val contentType = if (uri != null &&
            resolvedFromExtractor.first == "UNKNOWN" &&
            track.mimeType.isNullOrBlank() &&
            retrieverMime.isNullOrBlank()
        ) {
            try {
                context.contentResolver.getType(uri)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }

        val (codecName, isLossless) = when {
            resolvedFromExtractor.first != "UNKNOWN" -> resolvedFromExtractor
            else -> {
                val fromRetriever = resolveCodecAndLossless(retrieverMime ?: "", null)
                if (fromRetriever.first != "UNKNOWN") {
                    fromRetriever
                } else {
                    val fallback = detectFromTrackMetadata(
                        mimeType = track.mimeType,
                        displayName = track.displayName,
                        path = track.path,
                        uri = track.uri,
                        contentType = contentType
                    )
                    fallback.codec to fallback.isLossless
                }
            }
        }

        AudioFormatInfo(
            codec = codecName,
            isLossless = isLossless,
            bitrateKbps = bitrateKbps,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            channelCount = if (channels > 0) channels else 2
        )
    }

    private fun pcmEncodingToBitDepth(encoding: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            when (encoding) {
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> return 24
                AudioFormat.ENCODING_PCM_32BIT -> return 32
            }
        }
        return when (encoding) {
            AudioFormat.ENCODING_PCM_16BIT -> 16
            AudioFormat.ENCODING_PCM_FLOAT -> 32
            else -> 0
        }
    }

    private fun extractExtension(displayName: String?, path: String?, uri: String?): String {
        val candidates = listOfNotNull(displayName, path, uri)
        for (raw in candidates) {
            val cleaned = raw.substringBefore('?').substringBefore('#')
            val lastSegment = cleaned.substringAfterLast('/')
            if (lastSegment.contains('.')) {
                val ext = lastSegment.substringAfterLast('.').lowercase(Locale.ROOT).trim()
                if (ext.isNotEmpty() && ext.length <= 6) return ext
            }
        }
        return ""
    }

    private fun resolveCodecFromExtension(ext: String): Pair<String, Boolean>? {
        return when (ext.lowercase(Locale.ROOT)) {
            "flac" -> "FLAC" to true
            "alac" -> "ALAC" to true
            "wav" -> "WAV" to true
            "aiff", "aif" -> "AIFF" to true
            "pcm" -> "PCM" to true
            "ape" -> "APE" to true
            "wv" -> "WavPack" to true
            "dsf", "dff" -> "DSD" to true
            "mp3" -> "MP3" to false
            "aac", "m4a", "m4b", "m4p" -> "AAC" to false
            "ogg", "oga" -> "OGG Vorbis" to false
            "opus" -> "Opus" to false
            "wma" -> "WMA" to false
            "amr" -> "AMR" to false
            "mid", "midi" -> "MIDI" to false
            "mka" -> "MKA" to false
            else -> null
        }
    }

    private fun resolveCodecAndLossless(mime: String, codecs: String?): Pair<String, Boolean> {
        val lowerMime = mime.lowercase(Locale.ROOT).trim()
        val lowerCodecs = codecs?.lowercase(Locale.ROOT)?.trim() ?: ""

        return when {
            // Lossless
            lowerMime.contains("flac") || lowerCodecs.contains("flac") -> "FLAC" to true
            lowerMime.contains("alac") || lowerCodecs.contains("alac") -> "ALAC" to true
            lowerMime.contains("wav") || lowerMime.contains("wave") || lowerMime.contains("audio/x-wav") -> "WAV" to true
            lowerMime.contains("aiff") || lowerMime.contains("x-aiff") -> "AIFF" to true
            lowerMime == MimeTypes.AUDIO_RAW -> "PCM" to true

            // Lossy
            lowerMime.contains("mpeg") || lowerMime.contains("mp3") || lowerMime.contains("audio/mp3") -> "MP3" to false
            lowerMime.contains("mp4a") || lowerMime.contains("aac") || lowerCodecs.contains("mp4a") -> "AAC" to false
            lowerMime.contains("vorbis") || lowerMime.contains("ogg") -> "OGG Vorbis" to false
            lowerMime.contains("opus") || lowerCodecs.contains("opus") -> "Opus" to false
            lowerMime.contains("wma") -> "WMA" to false
            else -> {
                if (lowerMime.startsWith("audio/") && !lowerMime.endsWith("/*")) {
                    val subtype = lowerMime
                        .removePrefix("audio/")
                        .removePrefix("x-")
                        .trim()
                    if (subtype.isEmpty() || subtype == "unknown" || subtype == "octet-stream" || subtype == "*") {
                        "UNKNOWN" to false
                    } else {
                        subtype.uppercase(Locale.ROOT) to false
                    }
                } else {
                    "UNKNOWN" to false
                }
            }
        }
    }
}
