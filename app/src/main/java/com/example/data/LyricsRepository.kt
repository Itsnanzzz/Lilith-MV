package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import com.example.data.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

sealed interface LyricsLoadResult {
    data class Found(
        val lyrics: Lyrics,
        val canImport: Boolean,
        val hasImportedLyrics: Boolean
    ) : LyricsLoadResult

    data class NotFound(
        val canImport: Boolean
    ) : LyricsLoadResult

    data class Error(
        val canImport: Boolean
    ) : LyricsLoadResult
}

sealed interface LyricsImportResult {
    data class Success(val lyrics: Lyrics) : LyricsImportResult
    data object InvalidFile : LyricsImportResult
}

class LyricsRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val embeddedReader = EmbeddedLyricsReader(appContext)
    private val lyricsDir = File(appContext.filesDir, "lyrics/songs")

    private data class CacheKey(
        val parserVersion: Int,
        val trackId: Long,
        val uri: String,
        val mediaStoreId: Long?,
        val importedExists: Boolean,
        val importedLastModified: Long,
        val importedLength: Long,
        val audioSize: Long,
        val audioDateModified: Long
    )

    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<CacheKey, LyricsLoadResult>(CACHE_MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, LyricsLoadResult>?): Boolean {
            return size > CACHE_MAX_ENTRIES
        }
    }

    companion object {
        const val MAX_IMPORT_BYTES = 1_048_576 // 1 MB
        private const val CACHE_MAX_ENTRIES = 20
    }

    /**
     * Returns the MediaStore audio _ID only when the track comes from the MediaStore content provider.
     * Returns null for "Open with" / external document provider / file URIs so imported lyrics are not
     * keyed to synthetic timestamps.
     */
    fun resolveMediaStoreId(track: AudioTrack): Long? {
        val uri = try {
            Uri.parse(track.uri)
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority != "media") {
            return null
        }
        return try {
            ContentUris.parseId(uri).takeIf { it > 0L }
        } catch (_: Exception) {
            null
        }
    }

    fun hasImportedLyrics(mediaStoreId: Long?): Boolean {
        if (mediaStoreId == null || mediaStoreId <= 0L) return false
        return importedFileForId(mediaStoreId).isFile
    }

    private fun importedFileForId(mediaStoreId: Long): File {
        return File(lyricsDir, "$mediaStoreId.lrc")
    }

    private fun buildCacheKey(track: AudioTrack, mediaStoreId: Long?): CacheKey {
        val file = mediaStoreId?.let { importedFileForId(it) }
        val exists = file?.isFile == true
        return CacheKey(
            parserVersion = LyricsParser.PARSER_VERSION,
            trackId = track.id,
            uri = track.uri,
            mediaStoreId = mediaStoreId,
            importedExists = exists,
            importedLastModified = if (exists) file!!.lastModified() else 0L,
            importedLength = if (exists) file!!.length() else 0L,
            audioSize = track.size,
            audioDateModified = track.dateModified
        )
    }

    private fun invalidateCacheForId(mediaStoreId: Long) {
        synchronized(cacheLock) {
            val iterator = cache.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.mediaStoreId == mediaStoreId) {
                    iterator.remove()
                }
            }
        }
    }

    suspend fun loadLyrics(track: AudioTrack): LyricsLoadResult = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        val mediaStoreId = resolveMediaStoreId(track)
        val canImport = mediaStoreId != null
        val key = buildCacheKey(track, mediaStoreId)

        synchronized(cacheLock) {
            cache[key]?.let { return@withContext it }
        }

        // 1. Check imported lyrics file for MediaStore songs
        if (mediaStoreId != null) {
            val importedFile = importedFileForId(mediaStoreId)
            if (importedFile.isFile) {
                try {
                    currentCoroutineContext().ensureActive()
                    val fileLen = importedFile.length()
                    if (fileLen in 1L..MAX_IMPORT_BYTES.toLong()) {
                        val bytes = importedFile.readBytes()
                        currentCoroutineContext().ensureActive()
                        val decoded = LyricsParser.decodeLyricsBytes(bytes)
                        val parsed = LyricsParser.parse(decoded, LyricsSource.IMPORTED)
                        if (parsed.lines.isNotEmpty()) {
                            val found = LyricsLoadResult.Found(
                                lyrics = parsed,
                                canImport = true,
                                hasImportedLyrics = true
                            )
                            synchronized(cacheLock) {
                                cache[key] = found
                            }
                            return@withContext found
                        }
                    }
                } catch (_: SecurityException) {
                    return@withContext LyricsLoadResult.Error(canImport = true)
                } catch (_: Exception) {
                    return@withContext LyricsLoadResult.Error(canImport = true)
                }
            }
        }

        // 2. Fall back to embedded tags in the audio file
        currentCoroutineContext().ensureActive()
        val uriAccessible = try {
            contentResolver.openInputStream(Uri.parse(track.uri))?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
        if (!uriAccessible) {
            return@withContext LyricsLoadResult.Error(canImport = canImport)
        }

        val embedded = embeddedReader.readEmbeddedLyrics(track.uri)
        currentCoroutineContext().ensureActive()

        val result = if (embedded != null && embedded.lines.isNotEmpty()) {
            LyricsLoadResult.Found(
                lyrics = embedded,
                canImport = canImport,
                hasImportedLyrics = false
            )
        } else {
            LyricsLoadResult.NotFound(canImport = canImport)
        }

        synchronized(cacheLock) {
            cache[key] = result
        }
        result
    }

    suspend fun importLyrics(mediaStoreId: Long, sourceUri: Uri): LyricsImportResult = withContext(Dispatchers.IO) {
        if (mediaStoreId <= 0L) return@withContext LyricsImportResult.InvalidFile

        val bytes = try {
            contentResolver.openInputStream(sourceUri)?.use { stream ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = stream.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) {
                        return@withContext LyricsImportResult.InvalidFile
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: return@withContext LyricsImportResult.InvalidFile
        } catch (_: Exception) {
            return@withContext LyricsImportResult.InvalidFile
        }

        if (bytes.isEmpty()) return@withContext LyricsImportResult.InvalidFile

        val decoded = LyricsParser.decodeLyricsBytes(bytes)
        if (decoded.isBlank() || looksLikeBinaryText(decoded)) {
            return@withContext LyricsImportResult.InvalidFile
        }

        val parsed = LyricsParser.parse(decoded, LyricsSource.IMPORTED)
        if (parsed.lines.isEmpty()) {
            return@withContext LyricsImportResult.InvalidFile
        }

        try {
            if (!lyricsDir.exists() && !lyricsDir.mkdirs() && !lyricsDir.isDirectory) {
                return@withContext LyricsImportResult.InvalidFile
            }
            val destFile = importedFileForId(mediaStoreId)
            val tempFile = File(lyricsDir, "$mediaStoreId.lrc.tmp")
            tempFile.writeText(decoded, Charsets.UTF_8)
            if (destFile.exists()) {
                destFile.delete()
            }
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }
            invalidateCacheForId(mediaStoreId)
            LyricsImportResult.Success(parsed)
        } catch (_: Exception) {
            LyricsImportResult.InvalidFile
        }
    }

    suspend fun deleteImportedLyrics(mediaStoreId: Long): Boolean = withContext(Dispatchers.IO) {
        if (mediaStoreId <= 0L) return@withContext false
        val file = importedFileForId(mediaStoreId)
        val deleted = !file.exists() || file.delete()
        invalidateCacheForId(mediaStoreId)
        deleted
    }

    suspend fun deleteImportedLyricsForTrack(track: AudioTrack) {
        val id = resolveMediaStoreId(track) ?: return
        deleteImportedLyrics(id)
    }

    private fun looksLikeBinaryText(text: String): Boolean {
        if (text.isEmpty()) return true
        var controlOrNullCount = 0
        val sampleLen = minOf(text.length, 4096)
        for (i in 0 until sampleLen) {
            val ch = text[i]
            if (ch == '\u0000' || (ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t')) {
                controlOrNullCount++
            }
        }
        return controlOrNullCount > 0 && (controlOrNullCount * 100 / sampleLen) >= 2
    }
}
