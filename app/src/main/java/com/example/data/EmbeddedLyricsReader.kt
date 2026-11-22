package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.Locale
import kotlin.math.min

/**
 * Self-contained, bounded local embedded tag lyrics reader.
 *
 * Supported formats:
 * - MP3 / ID3v2 (v2.2 ULT, v2.3 & v2.4 USLT and millisecond SYLT; unsync + all 4 text encodings)
 * - FLAC (VORBIS_COMMENT metadata block: LYRICS / SYNCEDLYRICS / UNSYNCEDLYRICS)
 * - OGG Vorbis & Opus (OggS packet reassembly + vorbis/OpusTags comments)
 * - MP4 / M4A (moov -> udta -> meta -> ilst -> ©lyr -> data; seeks over mdat without reading audio frames)
 *
 * Not implemented (returns null safely):
 * - ID3v2 compressed/encrypted frames (ZLIB/encryption)
 * - ID3v2 SYLT with MPEG-frame timestamps (timestamp format 0x01)
 * - WMA/ASF, APEv2 tags, WAV/AIFF RIFF INFO/ID3 chunks, Matroska/MKA tags
 */
class EmbeddedLyricsReader(context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    companion object {
        const val MAX_TAG_BYTES = 1_572_864 // 1.5 MB cap
        private const val HEADER_SNIFF_BYTES = 16
        private val SUPPORTED_LYRICS_KEYS = setOf("LYRICS", "SYNCEDLYRICS", "UNSYNCEDLYRICS")
    }

    suspend fun readEmbeddedLyrics(uriString: String): Lyrics? = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val uri = try {
            Uri.parse(uriString)
        } catch (_: Exception) {
            return@withContext null
        }

        try {
            val header = readHeaderSniff(uri) ?: return@withContext null
            currentCoroutineContext().ensureActive()

            val rawLyricsText: String? = when {
                isId3v2(header) -> readId3v2Lyrics(uri)
                isFlac(header) -> readFlacLyrics(uri)
                isOgg(header) -> readOggLyrics(uri)
                isMp4(header) -> readMp4Lyrics(uri)
                else -> null
            }

            currentCoroutineContext().ensureActive()
            val cleaned = rawLyricsText?.takeIf { it.isNotBlank() } ?: return@withContext null
            val parsed = LyricsParser.parse(cleaned, LyricsSource.EMBEDDED)
            parsed.takeIf { it.lines.isNotEmpty() }
        } catch (_: Throwable) {
            // Never crash playback or UI on malformed tags, I/O errors, or revoked URIs.
            null
        }
    }

    private fun readHeaderSniff(uri: Uri): ByteArray? {
        return contentResolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(HEADER_SNIFF_BYTES)
            val read = readUpTo(stream, buf, HEADER_SNIFF_BYTES)
            if (read >= 4) buf.copyOf(read) else null
        }
    }

    private fun isId3v2(header: ByteArray): Boolean =
        header.size >= 10 &&
            header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()

    private fun isFlac(header: ByteArray): Boolean =
        header.size >= 4 &&
            header[0] == 'f'.code.toByte() &&
            header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() &&
            header[3] == 'C'.code.toByte()

    private fun isOgg(header: ByteArray): Boolean =
        header.size >= 4 &&
            header[0] == 'O'.code.toByte() &&
            header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() &&
            header[3] == 'S'.code.toByte()

    private fun isMp4(header: ByteArray): Boolean =
        header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()

    // =========================================================================
    // ID3v2 (v2.2 / v2.3 / v2.4)
    // =========================================================================

    private suspend fun readId3v2Lyrics(uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.use { rawStream ->
            val stream = BufferedInputStream(rawStream, 8192)
            val header = readExactOrNull(stream, 10) ?: return@use null
            if (!isId3v2(header)) return@use null

            val majorVersion = header[3].toInt() and 0xFF
            if (majorVersion !in 2..4) return@use null
            val flags = header[5].toInt() and 0xFF
            val tagSize = readSynchsafeInt(header, 6)
            if (tagSize <= 0) return@use null

            val readLimit = min(tagSize, MAX_TAG_BYTES)
            val tagBytes = readExactUpTo(stream, readLimit)
            if (tagBytes.isEmpty()) return@use null

            currentCoroutineContext().ensureActive()

            val globalUnsync = (flags and 0x80) != 0
            val bodyBytes = if (majorVersion == 3 && globalUnsync) {
                removeUnsynchronisation(tagBytes)
            } else {
                tagBytes
            }

            var pos = 0
            // Extended header
            val hasExtHeader = (flags and 0x40) != 0
            if (hasExtHeader && majorVersion in 3..4) {
                if (pos + 4 > bodyBytes.size) return@use null
                if (majorVersion == 3) {
                    val extSize = readIntBE(bodyBytes, pos)
                    if (extSize < 0) return@use null
                    pos += 4 + extSize
                } else {
                    val extSize = readSynchsafeInt(bodyBytes, pos)
                    if (extSize < 6) return@use null
                    pos += extSize
                }
            }

            val usltCandidates = mutableListOf<String>()
            val syltCandidates = mutableListOf<String>()

            if (majorVersion == 2) {
                while (pos + 6 <= bodyBytes.size) {
                    currentCoroutineContext().ensureActive()
                    val frameId = String(bodyBytes, pos, 3, Charsets.ISO_8859_1)
                    if (frameId.all { it == '\u0000' }) break
                    val frameSize = readUInt24BE(bodyBytes, pos + 3)
                    pos += 6
                    if (frameSize <= 0 || frameSize > bodyBytes.size - pos) break
                    if (frameId == "ULT") {
                        parseUsltPayload(bodyBytes, pos, frameSize)?.let { usltCandidates.add(it) }
                    }
                    pos += frameSize
                }
            } else {
                while (pos + 10 <= bodyBytes.size) {
                    currentCoroutineContext().ensureActive()
                    val b0 = bodyBytes[pos]
                    if (b0 == 0.toByte()) break // Padding reached
                    val frameId = String(bodyBytes, pos, 4, Charsets.ISO_8859_1)
                    if (!isValidFrameId(frameId)) break

                    val frameSize = if (majorVersion == 4) {
                        readSynchsafeInt(bodyBytes, pos + 4)
                    } else {
                        readIntBE(bodyBytes, pos + 4)
                    }
                    val frameFlags2 = bodyBytes[pos + 9].toInt() and 0xFF
                    pos += 10

                    if (frameSize <= 0 || frameSize > bodyBytes.size - pos) break

                    val isCompressed = (frameFlags2 and if (majorVersion == 4) 0x08 else 0x80) != 0
                    val isEncrypted = (frameFlags2 and if (majorVersion == 4) 0x04 else 0x40) != 0

                    if (!isCompressed && !isEncrypted && (frameId == "USLT" || frameId == "SYLT")) {
                        var payloadStart = pos
                        var payloadLen = frameSize
                        var payloadArray = bodyBytes

                        if (majorVersion == 4) {
                            val hasDli = (frameFlags2 and 0x01) != 0
                            val frameUnsync = globalUnsync || (frameFlags2 and 0x02) != 0
                            if (frameUnsync) {
                                val slice = bodyBytes.copyOfRange(pos, pos + frameSize)
                                payloadArray = removeUnsynchronisation(slice)
                                payloadStart = 0
                                payloadLen = payloadArray.size
                            }
                            if (hasDli && payloadLen >= 4) {
                                payloadStart += 4
                                payloadLen -= 4
                            }
                        }

                        if (payloadLen > 0) {
                            when (frameId) {
                                "USLT" -> parseUsltPayload(payloadArray, payloadStart, payloadLen)
                                    ?.let { usltCandidates.add(it) }
                                "SYLT" -> parseSyltPayload(payloadArray, payloadStart, payloadLen)
                                    ?.let { syltCandidates.add(it) }
                            }
                        }
                    }

                    pos += frameSize
                }
            }

            // Prefer SYLT if it produces synced lyrics (>= 3 lines); else prefer synced USLT, then plain USLT.
            for (sylt in syltCandidates) {
                val parsed = LyricsParser.parse(sylt, LyricsSource.EMBEDDED)
                if (parsed.synced) return@use sylt
            }
            for (uslt in usltCandidates) {
                val parsed = LyricsParser.parse(uslt, LyricsSource.EMBEDDED)
                if (parsed.synced) return@use uslt
            }
            usltCandidates.firstOrNull { it.isNotBlank() } ?: syltCandidates.firstOrNull { it.isNotBlank() }
        }
    }

    private fun parseUsltPayload(data: ByteArray, offset: Int, length: Int): String? {
        if (length < 5) return null // 1 byte encoding + 3 bytes language + >= 1 byte descriptor/text
        val encoding = data[offset].toInt() and 0xFF
        if (encoding !in 0..3) return null
        val afterLang = offset + 4
        val end = offset + length
        val descEnd = findNullTerminator(data, afterLang, end, encoding) ?: return null
        val textStart = descEnd + terminatorWidth(encoding)
        if (textStart >= end) return null
        val decoded = decodeId3Text(data, textStart, end - textStart, encoding)
        return decoded.trimEnd('\u0000').takeIf { it.isNotBlank() }
    }

    private fun parseSyltPayload(data: ByteArray, offset: Int, length: Int): String? {
        if (length < 7) return null // encoding(1) + lang(3) + format(1) + contentType(1) + descriptor
        val encoding = data[offset].toInt() and 0xFF
        if (encoding !in 0..3) return null
        val timestampFormat = data[offset + 4].toInt() and 0xFF
        if (timestampFormat != 0x02) return null // Only millisecond timestamps (0x02) are supported
        val end = offset + length
        val descStart = offset + 6
        val descEnd = findNullTerminator(data, descStart, end, encoding) ?: return null
        var cursor = descEnd + terminatorWidth(encoding)

        val lrcLines = StringBuilder()
        var entryCount = 0
        while (cursor < end) {
            val textEnd = findNullTerminator(data, cursor, end, encoding) ?: break
            val termWidth = terminatorWidth(encoding)
            val tsPos = textEnd + termWidth
            if (tsPos + 4 > end) break
            val text = decodeId3Text(data, cursor, textEnd - cursor, encoding)
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
            val timeMs = readUInt32BE(data, tsPos)
            cursor = tsPos + 4

            val totalSeconds = timeMs / 1000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            val centiseconds = (timeMs % 1000L) / 10L
            lrcLines.append(
                String.format(Locale.ROOT, "[%02d:%02d.%02d]%s\n", minutes, seconds, centiseconds, text)
            )
            entryCount++
        }

        return if (entryCount > 0) lrcLines.toString() else null
    }

    private fun decodeId3Text(data: ByteArray, offset: Int, length: Int, encoding: Int): String {
        if (length <= 0) return ""
        return when (encoding) {
            0 -> String(data, offset, length, Charsets.ISO_8859_1)
            1 -> {
                // UTF-16 with BOM (per spec, default LE if BOM absent or handle standard UTF-16)
                if (length >= 2 && data[offset] == 0xFF.toByte() && data[offset + 1] == 0xFE.toByte()) {
                    String(data, offset + 2, length - 2, Charsets.UTF_16LE)
                } else if (length >= 2 && data[offset] == 0xFE.toByte() && data[offset + 1] == 0xFF.toByte()) {
                    String(data, offset + 2, length - 2, Charsets.UTF_16BE)
                } else {
                    String(data, offset, length, Charsets.UTF_16)
                }
            }
            2 -> String(data, offset, length, Charsets.UTF_16BE)
            3 -> String(data, offset, length, Charsets.UTF_8)
            else -> ""
        }
    }

    private fun terminatorWidth(encoding: Int): Int = if (encoding == 1 || encoding == 2) 2 else 1

    private fun findNullTerminator(data: ByteArray, start: Int, end: Int, encoding: Int): Int? {
        if (encoding == 1 || encoding == 2) {
            var i = start
            while (i + 1 < end) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i
                i += 2
            }
            return null
        } else {
            for (i in start until end) {
                if (data[i] == 0.toByte()) return i
            }
            return null
        }
    }

    private fun removeUnsynchronisation(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(bytes.size)
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i]
            out.write(b.toInt() and 0xFF)
            if (b == 0xFF.toByte() && i + 1 < bytes.size && bytes[i + 1] == 0x00.toByte()) {
                i += 2
            } else {
                i += 1
            }
        }
        return out.toByteArray()
    }

    private fun isValidFrameId(id: String): Boolean =
        id.length == 4 && id.all { (it in 'A'..'Z') || (it in '0'..'9') }

    // =========================================================================
    // FLAC (VORBIS_COMMENT block type 4)
    // =========================================================================

    private suspend fun readFlacLyrics(uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.use { rawStream ->
            val stream = BufferedInputStream(rawStream, 8192)
            val sig = readExactOrNull(stream, 4) ?: return@use null
            if (!isFlac(sig)) return@use null

            var totalMetaRead = 4
            while (totalMetaRead < MAX_TAG_BYTES) {
                currentCoroutineContext().ensureActive()
                val blockHeader = readExactOrNull(stream, 4) ?: break
                totalMetaRead += 4
                val headerByte = blockHeader[0].toInt() and 0xFF
                val isLast = (headerByte and 0x80) != 0
                val blockType = headerByte and 0x7F
                val blockLen = readUInt24BE(blockHeader, 1)

                if (blockLen < 0 || totalMetaRead + blockLen > MAX_TAG_BYTES) {
                    if (blockType != 4) {
                        if (!skipStreamBytes(stream, blockLen.toLong())) break
                        totalMetaRead += blockLen
                        if (isLast) break
                        continue
                    } else {
                        break
                    }
                }

                if (blockType == 4) { // VORBIS_COMMENT
                    val commentBytes = readExactOrNull(stream, blockLen) ?: break
                    return@use parseVorbisComments(commentBytes, 0, commentBytes.size)
                } else {
                    if (!skipStreamBytes(stream, blockLen.toLong())) break
                    totalMetaRead += blockLen
                }
                if (isLast) break
            }
            null
        }
    }

    // =========================================================================
    // OGG (Vorbis 0x03vorbis & Opus OpusTags)
    // =========================================================================

    private suspend fun readOggLyrics(uri: Uri): String? {
        return contentResolver.openInputStream(uri)?.use { rawStream ->
            val stream = BufferedInputStream(rawStream, 8192)
            val packetBuffer = ByteArrayOutputStream()
            var totalBytesRead = 0
            var targetSerial: Int? = null
            var packetIndex = 0

            while (totalBytesRead < MAX_TAG_BYTES && packetIndex <= 2) {
                currentCoroutineContext().ensureActive()
                val pageHeader = readExactOrNull(stream, 27) ?: break
                totalBytesRead += 27
                if (pageHeader[0] != 'O'.code.toByte() ||
                    pageHeader[1] != 'g'.code.toByte() ||
                    pageHeader[2] != 'g'.code.toByte() ||
                    pageHeader[3] != 'S'.code.toByte()
                ) {
                    break
                }

                val pageSerial = ByteBuffer.wrap(pageHeader, 14, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                val segmentCount = pageHeader[26].toInt() and 0xFF
                val segmentTable = readExactOrNull(stream, segmentCount) ?: break
                totalBytesRead += segmentCount

                val pagePayloadSize = segmentTable.sumOf { it.toInt() and 0xFF }
                if (totalBytesRead + pagePayloadSize > MAX_TAG_BYTES) break

                if (targetSerial == null) {
                    targetSerial = pageSerial
                }
                if (pageSerial != targetSerial) {
                    if (!skipStreamBytes(stream, pagePayloadSize.toLong())) break
                    totalBytesRead += pagePayloadSize
                    continue
                }

                val pagePayload = readExactOrNull(stream, pagePayloadSize) ?: break
                totalBytesRead += pagePayloadSize

                var offset = 0
                for (segIdx in 0 until segmentCount) {
                    val segLen = segmentTable[segIdx].toInt() and 0xFF
                    if (segLen > 0) {
                        if (packetBuffer.size() + segLen > MAX_TAG_BYTES) return@use null
                        packetBuffer.write(pagePayload, offset, segLen)
                        offset += segLen
                    }
                    if (segLen < 255) {
                        // Packet completed
                        val packet = packetBuffer.toByteArray()
                        packetBuffer.reset()
                        if (packetIndex == 1) {
                            extractOggCommentPacket(packet)?.let { return@use it }
                        }
                        packetIndex++
                        if (packetIndex > 1) return@use null
                    }
                }
            }
            null
        }
    }

    private fun extractOggCommentPacket(packet: ByteArray): String? {
        if (packet.size >= 7 &&
            packet[0] == 0x03.toByte() &&
            String(packet, 1, 6, Charsets.ISO_8859_1) == "vorbis"
        ) {
            return parseVorbisComments(packet, 7, packet.size)
        }
        if (packet.size >= 8 && String(packet, 0, 8, Charsets.ISO_8859_1) == "OpusTags") {
            return parseVorbisComments(packet, 8, packet.size)
        }
        return null
    }

    private fun parseVorbisComments(data: ByteArray, start: Int, end: Int): String? {
        if (start + 8 > end) return null
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(start)

        val vendorLen = buf.int
        if (vendorLen < 0 || vendorLen > end - buf.position() - 4) return null
        buf.position(buf.position() + vendorLen)

        val count = buf.int
        if (count <= 0 || count > 2048) return null

        val foundByKey = mutableMapOf<String, String>()
        for (i in 0 until count) {
            if (buf.remaining() < 4) break
            val commentLen = buf.int
            if (commentLen <= 0 || commentLen > buf.remaining()) break
            val commentStr = String(data, buf.position(), commentLen, Charsets.UTF_8)
            buf.position(buf.position() + commentLen)

            val eqIndex = commentStr.indexOf('=')
            if (eqIndex > 0) {
                val key = commentStr.substring(0, eqIndex).uppercase(Locale.ROOT)
                if (key in SUPPORTED_LYRICS_KEYS) {
                    val value = commentStr.substring(eqIndex + 1)
                    if (value.isNotBlank() && !foundByKey.containsKey(key)) {
                        foundByKey[key] = value
                    }
                }
            }
        }

        // Prefer synced lyrics if present in any key; otherwise LYRICS -> SYNCEDLYRICS -> UNSYNCEDLYRICS
        for (key in listOf("SYNCEDLYRICS", "LYRICS", "UNSYNCEDLYRICS")) {
            val candidate = foundByKey[key] ?: continue
            if (LyricsParser.parse(candidate, LyricsSource.EMBEDDED).synced) {
                return candidate
            }
        }
        return foundByKey["LYRICS"] ?: foundByKey["UNSYNCEDLYRICS"] ?: foundByKey["SYNCEDLYRICS"]
    }

    // =========================================================================
    // MP4 / M4A (moov -> udta -> meta -> ilst -> ©lyr -> data)
    // =========================================================================

    private interface RandomAccessBoxReader {
        val totalLength: Long?
        fun position(): Long
        fun seek(pos: Long): Boolean
        fun readExact(dest: ByteArray, len: Int): Boolean
        fun skip(bytes: Long): Boolean
    }

    private suspend fun readMp4Lyrics(uri: Uri): String? {
        // Prefer ParcelFileDescriptor so we can jump across multi-MB mdat boxes in O(1) seek.
        val pfd = try {
            contentResolver.openFileDescriptor(uri, "r")
        } catch (_: Exception) {
            null
        }
        if (pfd != null) {
            pfd.use {
                FileInputStream(it.fileDescriptor).use { fis ->
                    val channel = fis.channel
                    val fileSize = try { channel.size() } catch (_: Exception) { null }
                    if (fileSize != null && fileSize > 0L) {
                        val reader = object : RandomAccessBoxReader {
                            override val totalLength: Long = fileSize
                            override fun position(): Long = channel.position()
                            override fun seek(pos: Long): Boolean {
                                return if (pos in 0L..fileSize) {
                                    channel.position(pos)
                                    true
                                } else false
                            }

                            override fun readExact(dest: ByteArray, len: Int): Boolean {
                                val bb = ByteBuffer.wrap(dest, 0, len)
                                while (bb.hasRemaining()) {
                                    val n = channel.read(bb)
                                    if (n <= 0) return false
                                }
                                return true
                            }

                            override fun skip(bytes: Long): Boolean = seek(position() + bytes)
                        }
                        return parseMp4Boxes(reader)
                    }
                }
            }
        }

        // Fallback sequential stream reader if descriptor isn't seekable
        return contentResolver.openInputStream(uri)?.use { raw ->
            val stream = BufferedInputStream(raw, 8192)
            var currentPos = 0L
            val reader = object : RandomAccessBoxReader {
                override val totalLength: Long? = null
                override fun position(): Long = currentPos
                override fun seek(pos: Long): Boolean {
                    val delta = pos - currentPos
                    return if (delta >= 0L) skip(delta) else false
                }

                override fun readExact(dest: ByteArray, len: Int): Boolean {
                    val ok = readExactBool(stream, dest, len)
                    if (ok) currentPos += len
                    return ok
                }

                override fun skip(bytes: Long): Boolean {
                    val ok = skipStreamBytes(stream, bytes)
                    if (ok) currentPos += bytes
                    return ok
                }
            }
            parseMp4Boxes(reader)
        }
    }

    private suspend fun parseMp4Boxes(reader: RandomAccessBoxReader): String? {
        val moovRange = findBox(reader, 0L, reader.totalLength, byteArrayOf('m'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte()))
            ?: return null
        currentCoroutineContext().ensureActive()

        val udtaRange = findBox(reader, moovRange.first, moovRange.second, byteArrayOf('u'.code.toByte(), 'd'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            ?: return null
        val metaRange = findBox(reader, udtaRange.first, udtaRange.second, byteArrayOf('m'.code.toByte(), 'e'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            ?: return null

        // ISO BMFF 'meta' is a FullBox (4 bytes version + flags before child boxes)
        val metaChildStart = metaRange.first + 4L
        if (metaChildStart >= metaRange.second) return null

        val ilstRange = findBox(reader, metaChildStart, metaRange.second, byteArrayOf('i'.code.toByte(), 'l'.code.toByte(), 's'.code.toByte(), 't'.code.toByte()))
            ?: return null
        val lyrRange = findBox(reader, ilstRange.first, ilstRange.second, byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte()))
            ?: return null
        val dataRange = findBox(reader, lyrRange.first, lyrRange.second, byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
            ?: return null

        val payloadSize = dataRange.second - dataRange.first
        if (payloadSize <= 8L || payloadSize > MAX_TAG_BYTES) return null

        if (!reader.seek(dataRange.first)) return null
        val dataBytes = ByteArray(payloadSize.toInt())
        if (!reader.readExact(dataBytes, dataBytes.size)) return null

        // 'data' box payload: 4-byte type indicator (1 = UTF-8, 2 = UTF-16BE), 4-byte locale, then text bytes
        val dataType = readIntBE(dataBytes, 0) and 0xFFFFFF
        val textOffset = 8
        val textLen = dataBytes.size - textOffset
        if (textLen <= 0) return null

        val charset: Charset = when (dataType) {
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(dataBytes, textOffset, textLen, charset).takeIf { it.isNotBlank() }
    }

    /**
     * Scans sibling MP4 boxes from [startOffset] up to [endOffset] looking for [targetType] (4 bytes).
     * Returns Pair(payloadStartOffset, boxEndOffset) or null.
     */
    private suspend fun findBox(
        reader: RandomAccessBoxReader,
        startOffset: Long,
        endOffset: Long?,
        targetType: ByteArray
    ): Pair<Long, Long>? {
        if (!reader.seek(startOffset)) return null
        val header8 = ByteArray(8)
        val size64Buf = ByteArray(8)
        var boxCount = 0

        while ((endOffset == null || reader.position() + 8 <= endOffset) && boxCount < 4096) {
            currentCoroutineContext().ensureActive()
            boxCount++
            val boxStart = reader.position()
            if (!reader.readExact(header8, 8)) return null

            val size32 = readUInt32BE(header8, 0)
            var headerLen = 8L
            val boxSize: Long = when (size32) {
                0L -> {
                    // Box extends to EOF / container end
                    if (endOffset != null) (endOffset - boxStart) else return null
                }
                1L -> {
                    if (!reader.readExact(size64Buf, 8)) return null
                    headerLen = 16L
                    ByteBuffer.wrap(size64Buf).order(ByteOrder.BIG_ENDIAN).long
                }
                else -> size32
            }

            if (boxSize < headerLen) return null
            val boxEnd = boxStart + boxSize
            if (endOffset != null && boxEnd > endOffset) return null

            val matches = header8[4] == targetType[0] &&
                header8[5] == targetType[1] &&
                header8[6] == targetType[2] &&
                header8[7] == targetType[3]

            if (matches) {
                return (boxStart + headerLen) to boxEnd
            }

            val remaining = boxEnd - reader.position()
            if (remaining < 0L) return null
            if (remaining > 0L && !reader.seek(boxEnd)) return null
        }
        return null
    }

    // =========================================================================
    // Byte & Stream Utilities
    // =========================================================================

    private fun readSynchsafeInt(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0x7F) shl 21) or
            ((buf[offset + 1].toInt() and 0x7F) shl 14) or
            ((buf[offset + 2].toInt() and 0x7F) shl 7) or
            (buf[offset + 3].toInt() and 0x7F)
    }

    private fun readIntBE(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)
    }

    private fun readUInt32BE(buf: ByteArray, offset: Int): Long {
        return ((buf[offset].toLong() and 0xFFL) shl 24) or
            ((buf[offset + 1].toLong() and 0xFFL) shl 16) or
            ((buf[offset + 2].toLong() and 0xFFL) shl 8) or
            (buf[offset + 3].toLong() and 0xFFL)
    }

    private fun readUInt24BE(buf: ByteArray, offset: Int): Int {
        return ((buf[offset].toInt() and 0xFF) shl 16) or
            ((buf[offset + 1].toInt() and 0xFF) shl 8) or
            (buf[offset + 2].toInt() and 0xFF)
    }

    private fun readUpTo(stream: InputStream, dest: ByteArray, maxLen: Int): Int {
        var total = 0
        while (total < maxLen) {
            val n = stream.read(dest, total, maxLen - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    private fun readExactOrNull(stream: InputStream, len: Int): ByteArray? {
        val buf = ByteArray(len)
        return if (readExactBool(stream, buf, len)) buf else null
    }

    private fun readExactBool(stream: InputStream, dest: ByteArray, len: Int): Boolean {
        var total = 0
        while (total < len) {
            val n = stream.read(dest, total, len - total)
            if (n < 0) return false
            total += n
        }
        return true
    }

    private fun readExactUpTo(stream: InputStream, maxLen: Int): ByteArray {
        val buf = ByteArray(maxLen)
        val total = readUpTo(stream, buf, maxLen)
        return if (total == maxLen) buf else buf.copyOf(total)
    }

    private fun skipStreamBytes(stream: InputStream, bytesToSkip: Long): Boolean {
        var remaining = bytesToSkip
        val scratch = ByteArray(4096)
        while (remaining > 0L) {
            val skipped = stream.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = stream.read(scratch, 0, min(scratch.size.toLong(), remaining).toInt())
                if (read < 0) return false
                remaining -= read
            }
        }
        return true
    }
}
