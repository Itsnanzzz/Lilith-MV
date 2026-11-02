package com.example.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.example.data.model.AudioFormatInfo
import java.util.Locale

object AudioFormatDetector {

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

        val sampleRate = if (format.sampleRate != Format.NO_VALUE) format.sampleRate else 0

        val bitDepth = when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_FLOAT -> 32
            else -> if (isLossless && (codecName == "FLAC" || codecName == "WAV" || codecName == "ALAC")) 16 else 0
        }

        val channels = if (format.channelCount != Format.NO_VALUE) format.channelCount else 2

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
            bitDepth = if (isLossless) 16 else 0,
            channelCount = 2
        )
    }

    private fun resolveCodecAndLossless(mime: String, codecs: String?): Pair<String, Boolean> {
        val lowerMime = mime.lowercase(Locale.ROOT)
        val lowerCodecs = codecs?.lowercase(Locale.ROOT) ?: ""

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
                if (lowerMime.startsWith("audio/")) {
                    lowerMime.removePrefix("audio/").uppercase(Locale.ROOT) to false
                } else {
                    "AUDIO" to false
                }
            }
        }
    }
}
