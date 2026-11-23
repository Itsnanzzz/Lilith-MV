package com.example

import com.example.data.LyricLine
import com.example.data.LyricsParser
import com.example.data.LyricsSource
import com.example.data.model.AudioFormatInfo
import com.example.ui.music.LYRICS_DEFAULT_OFFSET_MS
import com.example.ui.music.applyLyricsOffset
import com.example.ui.music.calculateLyricTapSeekPositionMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsOffsetAndAudioFormatTest {

    @Test
    fun defaultLyricsOffset_isHalfSecondEarlierWithoutBakingIntoParser() {
        assertEquals(500L, LYRICS_DEFAULT_OFFSET_MS)

        val lrc = """
            [00:05.00]Line 1
            [00:10.00]Line 2
            [00:15.00]Line 3
        """.trimIndent()
        val parsed = LyricsParser.parse(lrc, LyricsSource.IMPORTED)
        assertEquals(listOf(5_000L, 10_000L, 15_000L), parsed.lines.map { it.timeMs })

        // At playback 4_500ms with default +500ms offset, effective position is 5_000ms -> Line 1 is active
        val effective = applyLyricsOffset(4_500L, LYRICS_DEFAULT_OFFSET_MS)
        assertEquals(0, LyricsParser.findActiveLineIndex(parsed.lines, effective))
    }

    @Test
    fun tapToSeek_invertsOffsetSoTappedLineActivatesImmediately() {
        val lines = listOf(
            LyricLine(300L, "Early line"),
            LyricLine(5_000L, "Mid line"),
            LyricLine(10_000L, "Late line")
        )

        val seekForMid = calculateLyricTapSeekPositionMs(5_000L, LYRICS_DEFAULT_OFFSET_MS)
        assertEquals(4_500L, seekForMid)
        assertEquals(1, LyricsParser.findActiveLineIndex(lines, applyLyricsOffset(seekForMid, LYRICS_DEFAULT_OFFSET_MS)))

        val seekForEarly = calculateLyricTapSeekPositionMs(300L, LYRICS_DEFAULT_OFFSET_MS)
        assertEquals(0L, seekForEarly)
        assertEquals(0, LyricsParser.findActiveLineIndex(lines, applyLyricsOffset(seekForEarly, LYRICS_DEFAULT_OFFSET_MS)))

        val negativeOffsetSeek = calculateLyricTapSeekPositionMs(5_000L, -1_000L)
        assertEquals(6_000L, negativeOffsetSeek)
        assertEquals(1, LyricsParser.findActiveLineIndex(lines, applyLyricsOffset(negativeOffsetSeek, -1_000L)))
    }

    @Test
    fun audioFormatInfo_formatsDetailsAndSkipsMissingOrZeroValues() {
        val flac = AudioFormatInfo(
            codec = "FLAC",
            isLossless = true,
            bitDepth = 24,
            sampleRateHz = 44100,
            bitrateKbps = 0
        )
        assertEquals("FLAC • 24-bit • 44.1 kHz", flac.getDisplayText(showDetails = true))
        assertEquals("LOSSLESS", flac.getDisplayText(showDetails = false))

        val mp3 = AudioFormatInfo(
            codec = "MP3",
            isLossless = false,
            sampleRateHz = 44100,
            bitrateKbps = 192
        )
        assertEquals("MP3 • 44.1 kHz • 192 kbps", mp3.getDisplayText(showDetails = true))
        assertEquals("LOSSY", mp3.getDisplayText(showDetails = false))

        val empty = AudioFormatInfo()
        assertFalse(empty.hasValidCodec())
        assertNull(empty.getDisplayText(showDetails = true))
        assertNull(empty.getDisplayText(showDetails = false))

        val zeroAndUnknown = AudioFormatInfo(
            codec = "0",
            isLossless = false,
            bitrateKbps = -1,
            sampleRateHz = 0,
            bitDepth = -16
        )
        assertFalse(zeroAndUnknown.hasValidCodec())
        assertNull(zeroAndUnknown.getDisplayText(showDetails = true))
        assertNull(zeroAndUnknown.getDisplayText(showDetails = false))

        val codecOnlyLossy = AudioFormatInfo(codec = "MP3", isLossless = false)
        assertEquals("MP3", codecOnlyLossy.getDisplayText(showDetails = true))
        assertEquals("LOSSY", codecOnlyLossy.getDisplayText(showDetails = false))

        val losslessOnly = AudioFormatInfo(codec = "Unknown", isLossless = true)
        assertFalse(losslessOnly.hasValidCodec())
        assertEquals("LOSSLESS", losslessOnly.getDisplayText(showDetails = true))
        assertEquals("LOSSLESS", losslessOnly.getDisplayText(showDetails = false))
    }

    @Test
    fun audioFormatInfo_mergeWithFallbackPreservesLiveCodecAndFillsMissingFileDetails() {
        val liveFromTracks = AudioFormatInfo(
            codec = "FLAC",
            isLossless = true,
            bitrateKbps = 0,
            sampleRateHz = 48000,
            bitDepth = 0
        )
        val fromFile = AudioFormatInfo(
            codec = "FLAC",
            isLossless = true,
            bitrateKbps = 1536,
            sampleRateHz = 44100,
            bitDepth = 24
        )
        val merged = liveFromTracks.mergeWithFallback(fromFile)
        assertTrue(merged.hasValidCodec())
        assertEquals("FLAC • 24-bit • 48 kHz • 1536 kbps", merged.getDisplayText(showDetails = true))
    }
}
