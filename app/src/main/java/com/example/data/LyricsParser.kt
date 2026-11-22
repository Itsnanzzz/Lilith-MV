package com.example.data

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class LyricsSource {
    IMPORTED,
    EMBEDDED
}

data class LyricLine(
    val timeMs: Long?,
    val text: String
)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    val source: LyricsSource
)

/**
 * Pure Kotlin LRC and plain-text lyrics parser + character decoder + text cleaner.
 * Contains zero Android framework dependencies so it can run directly in JVM unit tests.
 */
object LyricsParser {

    const val PARSER_VERSION = 2

    private val LEADING_TIMESTAMP_REGEX = Regex("""^\[(\d+):([0-5]?\d)(?:\.(\d{1,3}))?]""")
    private val WORD_TIME_TAG_REGEX = Regex("""<\d+:[0-5]?\d(?:\.\d{1,3})?>""")
    private val METADATA_TAG_REGEX = Regex("""^\[([a-zA-Z]+):(.*)]$""")

    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD\u200E\u200F\uFEFF]""")
    private val VOICE_V1000_PREFIX_REGEX = Regex("""^\s*v1000\s*[:：]\s*""", RegexOption.IGNORE_CASE)
    private val VOICE_ID_PREFIX_REGEX = Regex("""^\s*v\d{1,4}\s*[:：]\s*""", RegexOption.IGNORE_CASE)
    private val ROLE_PREFIX_REGEX = Regex("""^\s*([MFD])\s*[:：]\s*""", RegexOption.IGNORE_CASE)
    private val BG_WRAPPER_REGEX = Regex("""^\s*\[\s*bg\s*:\s*(.*?)\s*]\s*$""", RegexOption.IGNORE_CASE)
    private val SECTION_LABEL_REGEX = Regex(
        """^\s*(?:\[\s*(?:verse|chorus|bridge|intro|outro|pre[\s-]?chorus|post[\s-]?chorus|hook|instrumental|interlude|refrain|solo)(?:\s+\d+|\s+x\d+)*\s*]|\(\s*(?:verse|chorus|bridge|intro|outro|pre[\s-]?chorus|post[\s-]?chorus|hook|instrumental|interlude|refrain|solo)(?:\s+\d+|\s+x\d+)*\s*\))\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val CREDIT_LINE_REGEX = Regex(
        """^\s*(?:(?:lyrics|written|composed|produced|arranged)\s+by\b|(?:composer|lyricist|producer|writer)\s*[:：]).*$""",
        RegexOption.IGNORE_CASE
    )

    private val SKIPPED_METADATA_KEYS = setOf(
        "ti", "ar", "al", "au", "by", "length", "re", "ve", "la", "id", "offset"
    )

    private val WINDOWS_1252: Charset = try {
        Charset.forName("windows-1252")
    } catch (_: Exception) {
        Charsets.ISO_8859_1
    }

    private data class CandidateLine(
        val timeMs: Long?,
        val text: String,
        val wasOriginallyBlank: Boolean,
        val isBackground: Boolean = false
    )

    /**
     * Decodes raw lyrics bytes using BOM detection (UTF-8, UTF-16LE, UTF-16BE),
     * strict UTF-8 validation, and windows-1252 fallback, normalizing line endings to \n.
     */
    fun decodeLyricsBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        val rawText = when {
            bytes.size >= 3 &&
                bytes[0] == 0xEF.toByte() &&
                bytes[1] == 0xBB.toByte() &&
                bytes[2] == 0xBF.toByte() -> {
                String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            }
            bytes.size >= 2 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xFE.toByte() -> {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            bytes.size >= 2 &&
                bytes[0] == 0xFE.toByte() &&
                bytes[1] == 0xFF.toByte() -> {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            else -> {
                try {
                    val decoder = Charsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                    decoder.decode(ByteBuffer.wrap(bytes)).toString()
                } catch (_: CharacterCodingException) {
                    String(bytes, WINDOWS_1252)
                }
            }
        }

        return normalizeLineEndings(rawText).removePrefix("\uFEFF")
    }

    fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }

    /**
     * Parses and cleans LRC or plain lyrics text.
     * - Synced when at least 3 timestamped lines exist before cleaning; after cleaning, remains
     *   synced if at least 1 timestamped line remains, otherwise falls back to plain.
     * - Positive [offset:ms] makes lyrics appear earlier (effective timestamp = tagTime - offsetMs).
     */
    fun parse(rawText: String, source: LyricsSource): Lyrics {
        val normalized = normalizeLineEndings(rawText).removePrefix("\uFEFF")
        if (normalized.isBlank()) {
            return Lyrics(lines = emptyList(), synced = false, source = source)
        }

        val rawLines = normalized.split('\n')

        var offsetMs = 0L
        for (line in rawLines) {
            val trimmed = line.trim()
            val metaMatch = METADATA_TAG_REGEX.matchEntire(trimmed)
            if (metaMatch != null && metaMatch.groupValues[1].equals("offset", ignoreCase = true)) {
                val parsedOffset = metaMatch.groupValues[2].trim().toLongOrNull()
                if (parsedOffset != null) {
                    offsetMs = parsedOffset
                }
            }
        }

        val rawTimestamped = mutableListOf<LyricLine>()

        for (rawLine in rawLines) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) continue

            val metaMatch = METADATA_TAG_REGEX.matchEntire(trimmed)
            if (metaMatch != null && metaMatch.groupValues[1].lowercase() in SKIPPED_METADATA_KEYS) {
                continue
            }

            val timestamps = mutableListOf<Long>()
            var cursor = 0
            while (cursor < trimmed.length) {
                while (cursor < trimmed.length && trimmed[cursor].isWhitespace()) {
                    cursor++
                }
                if (cursor >= trimmed.length) break
                val match = LEADING_TIMESTAMP_REGEX.find(trimmed.substring(cursor)) ?: break
                val parsedMs = parseTimestampGroups(
                    minutesStr = match.groupValues[1],
                    secondsStr = match.groupValues[2],
                    fractionStr = match.groupValues[3]
                )
                if (parsedMs != null) {
                    timestamps.add(parsedMs)
                }
                cursor += match.range.last + 1
            }

            if (timestamps.isNotEmpty()) {
                val body = WORD_TIME_TAG_REGEX.replace(trimmed.substring(cursor), "").trim()
                for (t in timestamps) {
                    val adjusted = (t - offsetMs).coerceAtLeast(0L)
                    rawTimestamped.add(LyricLine(timeMs = adjusted, text = body))
                }
            }
        }

        val sortedRawSynced = rawTimestamped
            .sortedBy { it.timeMs ?: Long.MAX_VALUE }
            .distinct()

        if (sortedRawSynced.size >= 3) {
            val cleanedSynced = safeCleanSynced(sortedRawSynced)
            if (cleanedSynced.isNotEmpty()) {
                return Lyrics(
                    lines = cleanedSynced,
                    synced = true,
                    source = source
                )
            }
        }

        // Plain-text fallback
        val uncleanedPlain = buildUncleanedPlainLines(rawLines)
        val cleanedPlain = safeCleanPlain(rawLines, uncleanedPlain)
        return Lyrics(
            lines = cleanedPlain,
            synced = false,
            source = source
        )
    }

    private fun safeCleanSynced(sortedRawSynced: List<LyricLine>): List<LyricLine> {
        return try {
            val preCleaned = mutableListOf<CandidateLine>()
            for (line in sortedRawSynced) {
                val strippedBase = stripInvisibleAndTrim(line.text)
                if (line.text.isEmpty()) {
                    // Preserve empty timestamped pause lines
                    preCleaned.add(
                        CandidateLine(
                            timeMs = line.timeMs,
                            text = "",
                            wasOriginallyBlank = true
                        )
                    )
                    continue
                }
                if (strippedBase.isEmpty() || SECTION_LABEL_REGEX.matches(strippedBase)) {
                    // Drop section labels completely (do not render as blank pause)
                    continue
                }
                val bgResult = unwrapBackgroundAndVoice(strippedBase)
                if (bgResult.text.isEmpty()) {
                    continue
                }
                preCleaned.add(
                    CandidateLine(
                        timeMs = line.timeMs,
                        text = bgResult.text,
                        wasOriginallyBlank = false,
                        isBackground = bgResult.isBackground
                    )
                )
            }

            val roleCleared = applyConditionalRolePrefixes(preCleaned)
            val withoutCredits = dropEdgeCreditLines(roleCleared)
            val dedupedBg = deduplicateBackgroundLines(withoutCredits)

            dedupedBg.map { LyricLine(timeMs = it.timeMs, text = it.text) }.distinct()
        } catch (_: Throwable) {
            sortedRawSynced
        }
    }

    private fun buildUncleanedPlainLines(rawLines: List<String>): List<LyricLine> {
        val trimmedLines = rawLines.map { it.trim() }
        val firstNonBlank = trimmedLines.indexOfFirst { it.isNotEmpty() }
        if (firstNonBlank == -1) return emptyList()
        val lastNonBlank = trimmedLines.indexOfLast { it.isNotEmpty() }
        val sliced = trimmedLines.subList(firstNonBlank, lastNonBlank + 1)
        return collapseExtraBlankLines(sliced).map { LyricLine(timeMs = null, text = it) }
    }

    private fun safeCleanPlain(
        rawLines: List<String>,
        fallback: List<LyricLine>
    ): List<LyricLine> {
        return try {
            val preCleaned = mutableListOf<CandidateLine>()
            for (rawLine in rawLines) {
                val cleanedRaw = WORD_TIME_TAG_REGEX.replace(stripInvisibleAndTrim(rawLine), "").trim()
                if (cleanedRaw.isEmpty()) {
                    preCleaned.add(
                        CandidateLine(
                            timeMs = null,
                            text = "",
                            wasOriginallyBlank = true
                        )
                    )
                    continue
                }
                val metaMatch = METADATA_TAG_REGEX.matchEntire(cleanedRaw)
                if (metaMatch != null && metaMatch.groupValues[1].lowercase() in SKIPPED_METADATA_KEYS) {
                    continue
                }
                if (SECTION_LABEL_REGEX.matches(cleanedRaw)) {
                    continue
                }
                val bgResult = unwrapBackgroundAndVoice(cleanedRaw)
                if (bgResult.text.isEmpty()) {
                    continue
                }
                preCleaned.add(
                    CandidateLine(
                        timeMs = null,
                        text = bgResult.text,
                        wasOriginallyBlank = false,
                        isBackground = bgResult.isBackground
                    )
                )
            }

            val roleCleared = applyConditionalRolePrefixes(preCleaned)
            val withoutCredits = dropEdgeCreditLines(roleCleared)
            val dedupedBg = deduplicateBackgroundLines(withoutCredits)

            val textLines = dedupedBg.map { it.text }
            val firstNonBlank = textLines.indexOfFirst { it.isNotEmpty() }
            if (firstNonBlank == -1) return emptyList()
            val lastNonBlank = textLines.indexOfLast { it.isNotEmpty() }
            val sliced = textLines.subList(firstNonBlank, lastNonBlank + 1)

            collapseExtraBlankLines(sliced).map { LyricLine(timeMs = null, text = it) }
        } catch (_: Throwable) {
            fallback
        }
    }

    private data class UnwrapResult(
        val text: String,
        val isBackground: Boolean
    )

    private fun stripInvisibleAndTrim(text: String): String {
        return INVISIBLE_CHARS_REGEX.replace(text, "").trim()
    }

    private fun unwrapBackgroundAndVoice(input: String): UnwrapResult {
        var text = input
        var isBackground = false

        val bgMatch = BG_WRAPPER_REGEX.matchEntire(text)
        if (bgMatch != null) {
            isBackground = true
            text = stripInvisibleAndTrim(bgMatch.groupValues[1])
        }

        if (VOICE_V1000_PREFIX_REGEX.containsMatchIn(text)) {
            isBackground = true
        }
        text = VOICE_ID_PREFIX_REGEX.replaceFirst(text, "").trim()

        // Handle rare reversed order: e.g. "v1000: [bg: ooh]"
        val nestedBgMatch = BG_WRAPPER_REGEX.matchEntire(text)
        if (nestedBgMatch != null) {
            isBackground = true
            text = stripInvisibleAndTrim(nestedBgMatch.groupValues[1])
            text = VOICE_ID_PREFIX_REGEX.replaceFirst(text, "").trim()
        }

        return UnwrapResult(text = text, isBackground = isBackground)
    }

    private fun applyConditionalRolePrefixes(lines: List<CandidateLine>): List<CandidateLine> {
        val letterCounts = mutableMapOf<Char, Int>()
        for (line in lines) {
            if (line.text.isEmpty()) continue
            val match = ROLE_PREFIX_REGEX.find(line.text) ?: continue
            val letter = match.groupValues[1].uppercase()[0]
            letterCounts[letter] = (letterCounts[letter] ?: 0) + 1
        }

        val removableLetters = letterCounts.filterValues { it >= 2 }.keys
        if (removableLetters.isEmpty()) return lines

        return lines.mapNotNull { line ->
            if (line.wasOriginallyBlank) return@mapNotNull line
            val match = ROLE_PREFIX_REGEX.find(line.text) ?: return@mapNotNull line
            val letter = match.groupValues[1].uppercase()[0]
            if (letter !in removableLetters) {
                line
            } else {
                val stripped = line.text.removeRange(match.range).trim()
                if (stripped.isEmpty()) null else line.copy(text = stripped)
            }
        }
    }

    private fun dropEdgeCreditLines(lines: List<CandidateLine>): List<CandidateLine> {
        val nonBlankIndices = lines.indices.filter { lines[it].text.isNotEmpty() }
        if (nonBlankIndices.isEmpty()) return lines

        val edgeIndices = HashSet<Int>(6)
        nonBlankIndices.take(3).forEach { edgeIndices.add(it) }
        nonBlankIndices.takeLast(3).forEach { edgeIndices.add(it) }

        return lines.filterIndexed { index, line ->
            !(index in edgeIndices && CREDIT_LINE_REGEX.matches(line.text))
        }
    }

    private fun deduplicateBackgroundLines(lines: List<CandidateLine>): List<CandidateLine> {
        val result = ArrayList<CandidateLine>(lines.size)
        for (line in lines) {
            if (line.isBackground && !line.wasOriginallyBlank) {
                val prev = result.lastOrNull()
                if (prev != null && prev.text == line.text) {
                    continue
                }
            }
            result.add(line)
        }
        return result
    }

    private fun collapseExtraBlankLines(lines: List<String>): List<String> {
        val collapsed = mutableListOf<String>()
        var blankRun = 0
        for (line in lines) {
            if (line.isEmpty()) {
                blankRun++
            } else {
                if (blankRun > 0) {
                    val toEmit = if (blankRun >= 3) 1 else blankRun
                    repeat(toEmit) { collapsed.add("") }
                    blankRun = 0
                }
                collapsed.add(line)
            }
        }
        return collapsed
    }

    /**
     * Binary search for the active lyric line index given effective playback position in ms.
     * Returns -1 if playback position precedes the first line timestamp.
     */
    fun findActiveLineIndex(lines: List<LyricLine>, effectivePositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val timeMs = lines[mid].timeMs ?: Long.MAX_VALUE
            if (timeMs <= effectivePositionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun parseTimestampGroups(
        minutesStr: String,
        secondsStr: String,
        fractionStr: String
    ): Long? {
        val minutes = minutesStr.toLongOrNull() ?: return null
        val seconds = secondsStr.toLongOrNull() ?: return null
        if (seconds !in 0L..59L) return null
        val millis = if (fractionStr.isEmpty()) {
            0L
        } else {
            fractionStr.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        }
        return (minutes * 60L + seconds) * 1000L + millis
    }
}
