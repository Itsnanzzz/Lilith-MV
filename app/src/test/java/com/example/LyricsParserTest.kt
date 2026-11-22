package com.example

import com.example.data.LyricLine
import com.example.data.LyricsParser
import com.example.data.LyricsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset

class LyricsParserTest {

    @Test
    fun parseSyncedLrc_handlesAllTimestampVariantsAndMetadata() {
        val input = """
            [ti:Sample Song]
            [ar:Sample Artist]
            [al:Sample Album]
            [by:Tester]
            [length:03:10]
            [00:05]First line without millis
            [00:10.25]Second line with centis
            [00:15.375]Third line with millis
            [00:20.00]
            [00:25.50]<00:25.50>Enhanced <00:26.00>word <00:26.50>tags
        """.trimIndent()

        val result = LyricsParser.parse(input, LyricsSource.IMPORTED)

        assertTrue(result.synced)
        assertEquals(LyricsSource.IMPORTED, result.source)
        assertEquals(5, result.lines.size)
        assertEquals(LyricLine(5_000L, "First line without millis"), result.lines[0])
        assertEquals(LyricLine(10_250L, "Second line with centis"), result.lines[1])
        assertEquals(LyricLine(15_375L, "Third line with millis"), result.lines[2])
        assertEquals(LyricLine(20_000L, ""), result.lines[3])
        assertEquals(LyricLine(25_500L, "Enhanced word tags"), result.lines[4])
    }

    @Test
    fun parseSyncedLrc_expandsMultiTimestampsSortsAndDeduplicates() {
        val input = """
            [00:30.00][00:10.00]Chorus line
            [00:20.00]Verse line
            [00:20.00]Verse line
            [00:40.00]Outro line
        """.trimIndent()

        val result = LyricsParser.parse(input, LyricsSource.EMBEDDED)

        assertTrue(result.synced)
        assertEquals(
            listOf(
                LyricLine(10_000L, "Chorus line"),
                LyricLine(20_000L, "Verse line"),
                LyricLine(30_000L, "Chorus line"),
                LyricLine(40_000L, "Outro line")
            ),
            result.lines
        )
    }

    @Test
    fun parseSyncedLrc_honorsPositiveAndNegativeOffsets() {
        val earlier = """
            [offset:500]
            [00:01.00]Line 1
            [00:02.00]Line 2
            [00:00.20]Clamped line
        """.trimIndent()

        val earlierResult = LyricsParser.parse(earlier, LyricsSource.IMPORTED)
        assertTrue(earlierResult.synced)
        assertEquals(0L, earlierResult.lines[0].timeMs)
        assertEquals("Clamped line", earlierResult.lines[0].text)
        assertEquals(500L, earlierResult.lines[1].timeMs)
        assertEquals(1_500L, earlierResult.lines[2].timeMs)

        val later = """
            [offset:-1200]
            [00:01.00]A
            [00:02.00]B
            [00:03.00]C
        """.trimIndent()

        val laterResult = LyricsParser.parse(later, LyricsSource.IMPORTED)
        assertEquals(listOf(2_200L, 3_200L, 4_200L), laterResult.lines.map { it.timeMs })
    }

    @Test
    fun parsePlainText_requiresThreeTimestampsForSyncedAndCollapsesBlanks() {
        val twoTimestamps = """
            [00:01.00]Line one
            [00:02.00]Line two
            Line three
        """.trimIndent()
        assertFalse(LyricsParser.parse(twoTimestamps, LyricsSource.IMPORTED).synced)

        val plainWithGaps = "  First stanza  \r\n\r\n  Second line  \r\n\r\n\r\n\r\n  After big gap  \r\n\r\n"
        val plain = LyricsParser.parse(plainWithGaps, LyricsSource.IMPORTED)

        assertFalse(plain.synced)
        assertTrue(plain.lines.all { it.timeMs == null })
        assertEquals(
            listOf("First stanza", "", "Second line", "", "After big gap"),
            plain.lines.map { it.text }
        )
    }

    @Test
    fun decodeLyricsBytes_handlesUtf8BomUtf16BomsAndWindows1252() {
        val utf8Bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "Café\r\nLine 2".toByteArray(Charsets.UTF_8)
        assertEquals("Café\nLine 2", LyricsParser.decodeLyricsBytes(utf8Bom))

        val utf16LeBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "Hola\r\nMundo".toByteArray(Charsets.UTF_16LE)
        assertEquals("Hola\nMundo", LyricsParser.decodeLyricsBytes(utf16LeBom))

        val utf16BeBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            "Tokyo\r\nNight".toByteArray(Charsets.UTF_16BE)
        assertEquals("Tokyo\nNight", LyricsParser.decodeLyricsBytes(utf16BeBom))

        val win1252 = "naïve — test".toByteArray(Charset.forName("windows-1252"))
        assertEquals("naïve — test", LyricsParser.decodeLyricsBytes(win1252))
    }

    @Test
    fun findActiveLineIndex_binarySearchHandlesBounds() {
        val lines = listOf(
            LyricLine(5_000L, "A"),
            LyricLine(10_000L, "B"),
            LyricLine(20_000L, "C")
        )
        assertEquals(-1, LyricsParser.findActiveLineIndex(lines, 0L))
        assertEquals(-1, LyricsParser.findActiveLineIndex(lines, 4_999L))
        assertEquals(0, LyricsParser.findActiveLineIndex(lines, 5_000L))
        assertEquals(0, LyricsParser.findActiveLineIndex(lines, 9_999L))
        assertEquals(1, LyricsParser.findActiveLineIndex(lines, 10_000L))
        assertEquals(2, LyricsParser.findActiveLineIndex(lines, 99_999L))
        assertEquals(-1, LyricsParser.findActiveLineIndex(emptyList(), 5_000L))
        assertNull(LyricsParser.parse("Plain only", LyricsSource.EMBEDDED).lines.first().timeMs)
    }

    @Test
    fun cleanLyrics_stripsVoicePrefixesV1V2V1000AndInvisibleChars() {
        val synced = """
            [00:05.00]v1: First singer line
            [00:10.00]V2: Second singer line
            [00:15.00]v1000: Ad-lib vocal
            [00:20.00]v3：Fullwidth colon line${'\u200B'}
        """.trimIndent()

        val result = LyricsParser.parse(synced, LyricsSource.EMBEDDED)
        assertTrue(result.synced)
        assertEquals(
            listOf(
                LyricLine(5_000L, "First singer line"),
                LyricLine(10_000L, "Second singer line"),
                LyricLine(15_000L, "Ad-lib vocal"),
                LyricLine(20_000L, "Fullwidth colon line")
            ),
            result.lines
        )

        val plain = LyricsParser.parse("v1: Plain first\nv2: Plain second", LyricsSource.IMPORTED)
        assertFalse(plain.synced)
        assertEquals(listOf("Plain first", "Plain second"), plain.lines.map { it.text })
    }

    @Test
    fun cleanLyrics_stripsRolePrefixesOnlyWhenSameLetterAppearsTwiceOrMore() {
        val repeatedMAndSingleF = """
            [00:05.00]M: Line one
            [00:10.00]F: Single female line
            [00:15.00]M: Line two
            [00:20.00]D: Single duet line
        """.trimIndent()

        val result = LyricsParser.parse(repeatedMAndSingleF, LyricsSource.IMPORTED)
        assertTrue(result.synced)
        assertEquals(
            listOf(
                "Line one",
                "F: Single female line",
                "Line two",
                "D: Single duet line"
            ),
            result.lines.map { it.text }
        )

        val singleEach = "M: Morning comes\nF: Forever stays\nD: Dancing away"
        val plainResult = LyricsParser.parse(singleEach, LyricsSource.IMPORTED)
        assertEquals(
            listOf("M: Morning comes", "F: Forever stays", "D: Dancing away"),
            plainResult.lines.map { it.text }
        )
    }

    @Test
    fun cleanLyrics_dropsSectionLabelsWithoutLeavingBlankPauses() {
        val synced = """
            [00:02.00][Intro]
            [00:05.00]Actual first line
            [00:08.00][Chorus]
            [00:10.00](Pre-Chorus 2)
            [00:12.00][Bridge x2]
            [00:15.00][Instrumental]
            [00:20.00]
            [00:25.00]Actual second line
        """.trimIndent()

        val result = LyricsParser.parse(synced, LyricsSource.IMPORTED)
        assertTrue(result.synced)
        assertEquals(
            listOf(
                LyricLine(5_000L, "Actual first line"),
                LyricLine(20_000L, ""),
                LyricLine(25_000L, "Actual second line")
            ),
            result.lines
        )
    }

    @Test
    fun cleanLyrics_handlesBackgroundMarkersAndRemovesImmediateDuplicates() {
        val synced = """
            [00:05.00]Don't let me go
            [00:06.00]v1000: Don't let me go
            [00:10.00]Hold on tonight
            [00:11.00][bg: Hold on tonight]
            [00:15.00][bg: Ooh, yeah]
        """.trimIndent()

        val result = LyricsParser.parse(synced, LyricsSource.EMBEDDED)
        assertTrue(result.synced)
        assertEquals(
            listOf(
                LyricLine(5_000L, "Don't let me go"),
                LyricLine(10_000L, "Hold on tonight"),
                LyricLine(15_000L, "Ooh, yeah")
            ),
            result.lines
        )
    }

    @Test
    fun cleanLyrics_dropsCreditLinesOnlyInFirstOrLastThreeLines() {
        val synced = """
            [00:01.00]Lyrics by Alice
            [00:02.00]Written by Bob
            [00:03.00]Line 1
            [00:04.00]Line 2
            [00:05.00]Composer: Should stay in middle
            [00:06.00]Line 3
            [00:07.00]Line 4
            [00:08.00]Produced by Carol
            [00:09.00]Composer: Dave
        """.trimIndent()

        val result = LyricsParser.parse(synced, LyricsSource.IMPORTED)
        assertTrue(result.synced)
        assertEquals(
            listOf(
                "Line 1",
                "Line 2",
                "Composer: Should stay in middle",
                "Line 3",
                "Line 4"
            ),
            result.lines.map { it.text }
        )
    }

    @Test
    fun cleanLyrics_keepsSyncedWhenCleaningReducesBelowThreeLines() {
        val input = """
            [00:01.00][Intro]
            [00:02.00][Chorus]
            [00:10.00]Only surviving line
        """.trimIndent()

        val result = LyricsParser.parse(input, LyricsSource.IMPORTED)
        assertTrue(result.synced)
        assertEquals(listOf(LyricLine(10_000L, "Only surviving line")), result.lines)
    }
}
