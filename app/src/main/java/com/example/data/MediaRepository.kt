package com.example.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AlbumItem
import com.example.data.model.ArtistItem
import com.example.data.model.AudioTrack
import com.example.data.model.FolderItem
import com.example.data.model.SongExtraInfo
import com.example.data.model.SubtitleTrackInfo
import com.example.data.model.VideoExtraInfo
import com.example.data.model.VideoItem
import com.example.playback.AudioFormatDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

sealed class DeleteMediaResult {
    object Success : DeleteMediaResult()
    data class RequiresIntentSender(val intentSender: IntentSender) : DeleteMediaResult()
    data class RequiresPermission(val permission: String) : DeleteMediaResult()
    data class Error(val message: String) : DeleteMediaResult()
}

sealed class RenameMediaResult {
    object Success : RenameMediaResult()
    data class RequiresIntentSender(val intentSender: IntentSender) : RenameMediaResult()
    data class RequiresPermission(val permission: String) : RenameMediaResult()
    data class Error(val message: String) : RenameMediaResult()
}
typealias RenameVideoResult = RenameMediaResult

sealed class DeleteTrackResult {
    object Success : DeleteTrackResult()
    data class RequiresIntentSender(val intentSender: IntentSender, val track: AudioTrack) : DeleteTrackResult()
    data class RequiresPermission(val permission: String, val track: AudioTrack) : DeleteTrackResult()
    data class Error(val message: String) : DeleteTrackResult()
}

class MediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    suspend fun getAudioTracks(): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projectionList = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Audio.Media.RELATIVE_PATH)
        }
        val projection = projectionList.toTypedArray()
        // Filter out very short ringtones or notification sounds (< 5 seconds)
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} >= 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dispCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val addedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol) ?: "<unknown>"
                    val album = cursor.getString(albumCol) ?: "<unknown>"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val mime = cursor.getString(mimeCol)
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L
                    val displayName = if (dispCol >= 0 && !cursor.isNull(dispCol)) cursor.getString(dispCol) ?: "" else ""
                    val dateAdded = if (addedCol >= 0 && !cursor.isNull(addedCol)) cursor.getLong(addedCol) else 0L
                    val dateModified = if (modCol >= 0 && !cursor.isNull(modCol)) cursor.getLong(modCol) else 0L
                    val relativePath = if (relCol >= 0 && !cursor.isNull(relCol)) cursor.getString(relCol) ?: "" else ""

                    val fileName = if (path.isNotEmpty()) File(path).nameWithoutExtension else "Track $id"
                    val title = if (!rawTitle.isNullOrBlank() && rawTitle != "<unknown>") rawTitle else fileName

                    val folderName = if (path.isNotEmpty()) {
                        File(path).parentFile?.name ?: context.getString(R.string.default_storage_folder)
                    } else context.getString(R.string.default_storage_folder)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val albumArtUri = if (albumId > 0) "content://media/external/audio/albumart/$albumId" else contentUri

                    tracks.add(
                        AudioTrack(
                            id = id,
                            uri = contentUri,
                            title = title,
                            artist = if (artist == "<unknown>") context.getString(R.string.unknown_artist) else artist,
                            album = if (album == "<unknown>") context.getString(R.string.unknown_album) else album,
                            albumId = albumId,
                            durationMs = duration,
                            path = path,
                            folderName = folderName,
                            mimeType = mime,
                            albumArtUri = albumArtUri,
                            size = size,
                            displayName = displayName,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            relativePath = relativePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tracks
    }

    suspend fun getVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projectionList = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projectionList.add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        val projection = projectionList.toTypedArray()
        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val dispCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val duration = cursor.getLong(durationCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val displayName = if (dispCol >= 0 && !cursor.isNull(dispCol)) cursor.getString(dispCol) ?: "" else ""
                    val mimeType = if (mimeCol >= 0 && !cursor.isNull(mimeCol)) cursor.getString(mimeCol) ?: "" else ""
                    val dateAdded = if (addedCol >= 0 && !cursor.isNull(addedCol)) cursor.getLong(addedCol) else 0L
                    val relativePath = if (relCol >= 0 && !cursor.isNull(relCol)) cursor.getString(relCol) ?: "" else ""

                    val fileName = if (path.isNotEmpty()) File(path).nameWithoutExtension else "Video $id"
                    val title = if (!rawTitle.isNullOrBlank()) rawTitle else fileName

                    val folderName = if (path.isNotEmpty()) {
                        File(path).parentFile?.name ?: context.getString(R.string.default_video_folder)
                    } else context.getString(R.string.default_video_folder)

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id).toString()

                    videos.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = title,
                            durationMs = duration,
                            width = width,
                            height = height,
                            size = size,
                            dateModified = dateModified,
                            path = path,
                            folderName = folderName,
                            thumbnailUri = contentUri,
                            displayName = displayName,
                            mimeType = mimeType,
                            dateAdded = dateAdded,
                            relativePath = relativePath
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videos
    }

    suspend fun parseTrackFromUri(uri: Uri, displayName: String? = null, size: Long = 0L): AudioTrack = withContext(Dispatchers.IO) {
        val fallbackTitle = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.default_song_title)

        val resolvedSize = if (size > 0L) {
            size
        } else if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0 && !cursor.isNull(idx)) cursor.getLong(idx) else 0L
                    } else 0L
                } ?: 0L
            } catch (_: Exception) {
                0L
            }
        } else if (uri.scheme == "file") {
            try {
                File(uri.path ?: "").takeIf { it.exists() }?.length() ?: 0L
            } catch (_: Exception) {
                0L
            }
        } else {
            0L
        }

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val title = if (!metaTitle.isNullOrBlank()) metaTitle else fallbackTitle
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_artist)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: context.getString(R.string.unknown_album)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val hasArt = retriever.embeddedPicture != null
            retriever.release()

            AudioTrack(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                path = if (uri.scheme == "file") (uri.path ?: "") else "",
                folderName = context.getString(R.string.subtitle_external_file),
                mimeType = mime,
                albumArtUri = if (hasArt) uri.toString() else null,
                size = resolvedSize,
                displayName = displayName ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "")
            )
        } catch (e: Exception) {
            AudioTrack(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = fallbackTitle,
                artist = context.getString(R.string.unknown_artist),
                album = context.getString(R.string.unknown_album),
                durationMs = 0L,
                path = if (uri.scheme == "file") (uri.path ?: "") else "",
                folderName = context.getString(R.string.subtitle_external_file),
                mimeType = null,
                albumArtUri = null,
                size = resolvedSize,
                displayName = displayName ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "")
            )
        }
    }

    suspend fun parseVideoFromUri(uri: Uri, displayName: String? = null, size: Long = 0L): VideoItem = withContext(Dispatchers.IO) {
        val fallbackTitle = displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.default_video_folder)

        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
            retriever.release()

            VideoItem(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = fallbackTitle,
                durationMs = duration,
                width = width,
                height = height,
                size = size,
                path = if (uri.scheme == "file") (uri.path ?: "") else "",
                folderName = context.getString(R.string.subtitle_external_file),
                thumbnailUri = uri.toString(),
                displayName = displayName ?: (uri.lastPathSegment?.substringAfterLast('/') ?: ""),
                mimeType = mime
            )
        } catch (e: Exception) {
            VideoItem(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = fallbackTitle,
                durationMs = 0L,
                size = size,
                path = if (uri.scheme == "file") (uri.path ?: "") else "",
                folderName = context.getString(R.string.subtitle_external_file),
                thumbnailUri = uri.toString(),
                displayName = displayName ?: (uri.lastPathSegment?.substringAfterLast('/') ?: "")
            )
        }
    }

    suspend fun findMatchingTrack(uri: Uri, displayName: String?, size: Long): AudioTrack? = withContext(Dispatchers.IO) {
        val tracks = getAudioTracks()
        val uriStr = uri.toString()

        tracks.find { it.uri == uriStr }?.let { return@withContext it }

        val uriId = try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == "media") {
                ContentUris.parseId(uri)
            } else null
        } catch (_: Exception) { null }
        if (uriId != null) {
            tracks.find { it.id == uriId }?.let { return@withContext it }
        }

        if (!displayName.isNullOrBlank()) {
            val nameWithoutExt = displayName.substringBeforeLast('.')
            tracks.find { t ->
                val matchName = t.title.equals(displayName, ignoreCase = true) ||
                        t.title.equals(nameWithoutExt, ignoreCase = true) ||
                        (t.path.isNotEmpty() && File(t.path).name.equals(displayName, ignoreCase = true))
                val matchSize = size <= 0L || (t.path.isNotEmpty() && File(t.path).length() == size)
                matchName && matchSize
            }?.let { return@withContext it }
        }

        null
    }

    suspend fun findMatchingVideo(uri: Uri, displayName: String?, size: Long): VideoItem? = withContext(Dispatchers.IO) {
        val videos = getVideos()
        val uriStr = uri.toString()

        videos.find { it.uri == uriStr }?.let { return@withContext it }

        val uriId = try {
            if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == "media") {
                ContentUris.parseId(uri)
            } else null
        } catch (_: Exception) { null }
        if (uriId != null) {
            videos.find { it.id == uriId }?.let { return@withContext it }
        }

        if (!displayName.isNullOrBlank()) {
            val nameWithoutExt = displayName.substringBeforeLast('.')
            videos.find { v ->
                val matchName = v.title.equals(displayName, ignoreCase = true) ||
                        v.title.equals(nameWithoutExt, ignoreCase = true) ||
                        (v.path.isNotEmpty() && File(v.path).name.equals(displayName, ignoreCase = true))
                val matchSize = size <= 0L || v.size == size || (v.path.isNotEmpty() && File(v.path).length() == size)
                matchName && matchSize
            }?.let { return@withContext it }
        }

        null
    }

    suspend fun findMatchingSubtitles(videoPath: String?): List<SubtitleTrackInfo> = withContext(Dispatchers.IO) {
        val subtitles = mutableListOf<SubtitleTrackInfo>()
        if (videoPath.isNullOrBlank()) return@withContext subtitles

        try {
            val videoFile = File(videoPath)
            val parentDir = videoFile.parentFile
            if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
                val baseName = videoFile.nameWithoutExtension.lowercase()
                val validExtensions = listOf("srt", "vtt", "ass", "ssa")

                val matchedFiles = parentDir.listFiles { file ->
                    if (!file.isFile) return@listFiles false
                    val ext = file.extension.lowercase()
                    if (ext !in validExtensions) return@listFiles false
                    val fname = file.nameWithoutExtension.lowercase()
                    fname.startsWith(baseName)
                }

                matchedFiles?.forEach { subFile ->
                    val ext = subFile.extension.lowercase()
                    val mime = when (ext) {
                        "srt" -> "application/x-subrip"
                        "vtt" -> "text/vtt"
                        "ass", "ssa" -> "text/x-ssa"
                        else -> "application/x-subrip"
                    }
                    val langPart = subFile.nameWithoutExtension.substringAfter(videoFile.nameWithoutExtension, "")
                        .replace(".", "").trim()
                    val langLabel = if (langPart.isNotEmpty()) {
                        langPart.uppercase()
                    } else {
                        context.getString(R.string.subtitle_external_ext, ext)
                    }

                    subtitles.add(
                        SubtitleTrackInfo(
                            id = subFile.absolutePath,
                            label = "${subFile.name} ($langLabel)",
                            language = langPart.ifEmpty { "auto" },
                            uri = Uri.fromFile(subFile).toString(),
                            mimeType = mime,
                            isExternal = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        subtitles
    }

    fun groupIntoAlbums(tracks: List<AudioTrack>): List<AlbumItem> {
        return tracks.groupBy { it.album }
            .map { (albumTitle, songList) ->
                val first = songList.first()
                AlbumItem(
                    id = first.albumId,
                    title = albumTitle,
                    artist = first.artist,
                    trackCount = songList.size,
                    albumArtUri = first.albumArtUri
                )
            }.sortedBy { it.title.lowercase() }
    }

    fun groupIntoArtists(tracks: List<AudioTrack>): List<ArtistItem> {
        return tracks.groupBy { it.artist }
            .map { (artistName, songList) ->
                ArtistItem(
                    name = artistName,
                    trackCount = songList.size
                )
            }.sortedBy { it.name.lowercase() }
    }

    fun groupIntoFolders(tracks: List<AudioTrack>): List<FolderItem> {
        return tracks.groupBy { it.folderName }
            .map { (folderName, songList) ->
                val samplePath = songList.firstOrNull()?.path ?: ""
                val parentPath = if (samplePath.isNotEmpty()) File(samplePath).parent ?: "" else ""
                FolderItem(
                    name = folderName,
                    path = parentPath,
                    trackCount = songList.size,
                    isVideo = false
                )
            }.sortedBy { it.name.lowercase() }
    }

    suspend fun deleteMediaUri(uri: Uri, path: String? = null): DeleteMediaResult = withContext(Dispatchers.IO) {
        // 1. SAF Documents Provider URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            return@withContext try {
                val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
                if (deleted) DeleteMediaResult.Success
                else DeleteMediaResult.Error(context.getString(R.string.delete_error_doc_readonly))
            } catch (se: SecurityException) {
                DeleteMediaResult.Error(context.getString(R.string.delete_error_readonly_permission))
            } catch (e: Exception) {
                DeleteMediaResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_doc_generic))
            }
        }

        // Check if underlying file already doesn't exist on disk
        if (!path.isNullOrEmpty()) {
            val file = File(path)
            if (!file.exists()) {
                try {
                    contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
                return@withContext DeleteMediaResult.Success
            }
        }

        // 1b. Direct file:// URI
        if (uri.scheme == "file") {
            val targetPath = path ?: uri.path
            val file = if (!targetPath.isNullOrEmpty()) File(targetPath) else null
            return@withContext try {
                if (file != null && (!file.exists() || file.delete())) {
                    DeleteMediaResult.Success
                } else {
                    DeleteMediaResult.Error(context.getString(R.string.delete_error_generic))
                }
            } catch (e: Exception) {
                DeleteMediaResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_generic))
            }
        }

        // 2. Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                DeleteMediaResult.RequiresIntentSender(pendingIntent.intentSender)
            } catch (e: Exception) {
                try {
                    val count = contentResolver.delete(uri, null, null)
                    if (count > 0) DeleteMediaResult.Success
                    else DeleteMediaResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_request_failed))
                } catch (ex: Exception) {
                    DeleteMediaResult.Error(ex.localizedMessage ?: context.getString(R.string.delete_error_generic))
                }
            }
        }

        // 3. Android 10 (API 29)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            return@withContext try {
                val count = contentResolver.delete(uri, null, null)
                if (count > 0) DeleteMediaResult.Success
                else DeleteMediaResult.Error(context.getString(R.string.delete_error_file_not_found))
            } catch (securityEx: SecurityException) {
                if (securityEx is android.app.RecoverableSecurityException) {
                    DeleteMediaResult.RequiresIntentSender(securityEx.userAction.actionIntent.intentSender)
                } else {
                    DeleteMediaResult.Error(securityEx.localizedMessage ?: context.getString(R.string.delete_error_permission_denied))
                }
            } catch (e: Exception) {
                DeleteMediaResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_generic))
            }
        }

        // 4. Android 8-9 (API 26-28)
        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasWritePermission) {
            return@withContext DeleteMediaResult.RequiresPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        return@withContext try {
            if (!path.isNullOrEmpty()) {
                val f = File(path)
                if (f.exists()) f.delete()
            }
            contentResolver.delete(uri, null, null)
            DeleteMediaResult.Success
        } catch (e: Exception) {
            DeleteMediaResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_generic))
        }
    }

    suspend fun deleteTrack(track: AudioTrack): DeleteTrackResult = withContext(Dispatchers.IO) {
        val uri = try {
            Uri.parse(track.uri)
        } catch (e: Exception) {
            return@withContext DeleteTrackResult.Error(context.getString(R.string.delete_error_invalid_uri))
        }
        when (val res = deleteMediaUri(uri, track.path)) {
            is DeleteMediaResult.Success -> DeleteTrackResult.Success
            is DeleteMediaResult.RequiresIntentSender -> DeleteTrackResult.RequiresIntentSender(res.intentSender, track)
            is DeleteMediaResult.RequiresPermission -> DeleteTrackResult.RequiresPermission(res.permission, track)
            is DeleteMediaResult.Error -> DeleteTrackResult.Error(res.message)
        }
    }

    suspend fun deleteVideo(video: VideoItem): DeleteMediaResult = withContext(Dispatchers.IO) {
        val uri = try {
            Uri.parse(video.uri)
        } catch (e: Exception) {
            return@withContext DeleteMediaResult.Error(context.getString(R.string.delete_error_invalid_uri))
        }
        deleteMediaUri(uri, video.path)
    }

    suspend fun deleteMediaAfterSecurityApproval(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val count = contentResolver.delete(uri, null, null)
            count > 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteTrackAfterSecurityApproval(track: AudioTrack): Boolean =
        deleteMediaAfterSecurityApproval(Uri.parse(track.uri))

    suspend fun deleteVideoAfterSecurityApproval(video: VideoItem): Boolean =
        deleteMediaAfterSecurityApproval(Uri.parse(video.uri))

    suspend fun checkMediaNameExists(
        path: String,
        relativePath: String,
        newFullName: String,
        isAudio: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (path.isNotEmpty()) {
            val file = File(path)
            val parent = file.parentFile
            if (parent != null && File(parent, newFullName).exists()) {
                return@withContext true
            }
        }
        val uri = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val dispCol = if (isAudio) MediaStore.Audio.Media.DISPLAY_NAME else MediaStore.Video.Media.DISPLAY_NAME
        val relCol = if (isAudio) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Video.Media.RELATIVE_PATH
        val dataCol = if (isAudio) MediaStore.Audio.Media.DATA else MediaStore.Video.Media.DATA
        val idCol = if (isAudio) MediaStore.Audio.Media._ID else MediaStore.Video.Media._ID

        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && relativePath.isNotEmpty()) {
            selection = "$dispCol = ? AND $relCol = ?"
            selectionArgs = arrayOf(newFullName, relativePath)
        } else if (path.isNotEmpty()) {
            val parent = File(path).parent ?: ""
            val targetPath = if (parent.isNotEmpty()) "$parent/$newFullName" else ""
            selection = "$dispCol = ? OR $dataCol = ?"
            selectionArgs = arrayOf(newFullName, targetPath)
        } else {
            selection = "$dispCol = ?"
            selectionArgs = arrayOf(newFullName)
        }
        try {
            contentResolver.query(uri, arrayOf(idCol), selection, selectionArgs, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun checkVideoNameExists(video: VideoItem, newFullName: String): Boolean =
        checkMediaNameExists(video.path, video.relativePath, newFullName, isAudio = false)

    suspend fun checkTrackNameExists(track: AudioTrack, newFullName: String): Boolean =
        checkMediaNameExists(track.path, track.relativePath, newFullName, isAudio = true)

    suspend fun renameMediaUri(
        uri: Uri,
        path: String,
        relativePath: String,
        newFullName: String,
        isAudio: Boolean
    ): RenameMediaResult = withContext(Dispatchers.IO) {
        // Check if name already exists
        if (checkMediaNameExists(path, relativePath, newFullName, isAudio)) {
            return@withContext RenameMediaResult.Error(context.getString(R.string.rename_error_already_exists))
        }

        // 1. SAF Documents Provider URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            return@withContext try {
                val newUri = DocumentsContract.renameDocument(contentResolver, uri, newFullName)
                if (newUri != null) RenameMediaResult.Success
                else RenameMediaResult.Error(context.getString(R.string.rename_error_doc_failed))
            } catch (se: SecurityException) {
                RenameMediaResult.Error(context.getString(R.string.delete_error_readonly_permission))
            } catch (e: Exception) {
                RenameMediaResult.Error(e.localizedMessage ?: context.getString(R.string.rename_failed))
            }
        }

        // 1b. Direct file:// URI
        if (uri.scheme == "file") {
            val targetPath = path.ifEmpty { uri.path ?: "" }
            return@withContext try {
                val oldFile = File(targetPath)
                val parent = oldFile.parentFile
                if (targetPath.isNotEmpty() && oldFile.exists() && parent != null) {
                    val newFile = File(parent, newFullName)
                    if (oldFile.renameTo(newFile)) {
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(oldFile.absolutePath, newFile.absolutePath),
                            null,
                            null
                        )
                        RenameMediaResult.Success
                    } else {
                        RenameMediaResult.Error(context.getString(R.string.rename_failed))
                    }
                } else {
                    RenameMediaResult.Error(context.getString(R.string.delete_error_file_not_found))
                }
            } catch (e: Exception) {
                RenameMediaResult.Error(e.localizedMessage ?: context.getString(R.string.rename_failed))
            }
        }

        // 2. Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val pendingIntent = MediaStore.createWriteRequest(contentResolver, listOf(uri))
                RenameMediaResult.RequiresIntentSender(pendingIntent.intentSender)
            } catch (e: Exception) {
                val updated = performMediaRenameUpdate(uri, newFullName, isAudio)
                if (updated) RenameMediaResult.Success
                else RenameMediaResult.Error(e.localizedMessage ?: context.getString(R.string.rename_failed))
            }
        }

        // 3. Android 10 (API 29)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            return@withContext try {
                val values = ContentValues().apply {
                    val dispCol = if (isAudio) MediaStore.Audio.Media.DISPLAY_NAME else MediaStore.Video.Media.DISPLAY_NAME
                    put(dispCol, newFullName)
                    if (!isAudio) {
                        put(MediaStore.Video.Media.TITLE, newFullName.substringBeforeLast('.'))
                    }
                }
                val count = contentResolver.update(uri, values, null, null)
                if (count > 0) RenameMediaResult.Success
                else RenameMediaResult.Error(context.getString(R.string.rename_failed))
            } catch (securityEx: SecurityException) {
                if (securityEx is android.app.RecoverableSecurityException) {
                    RenameMediaResult.RequiresIntentSender(securityEx.userAction.actionIntent.intentSender)
                } else {
                    RenameMediaResult.Error(securityEx.localizedMessage ?: context.getString(R.string.delete_error_permission_denied))
                }
            } catch (e: Exception) {
                RenameMediaResult.Error(e.localizedMessage ?: context.getString(R.string.rename_failed))
            }
        }

        // 4. Android 8-9 (API 26-28)
        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasWritePermission) {
            return@withContext RenameMediaResult.RequiresPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

        return@withContext try {
            var updated = false
            if (path.isNotEmpty()) {
                val oldFile = File(path)
                if (oldFile.exists()) {
                    val newFile = File(oldFile.parentFile, newFullName)
                    if (oldFile.renameTo(newFile)) {
                        updated = true
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(newFile.absolutePath),
                            null,
                            null
                        )
                    }
                }
            }
            val values = ContentValues().apply {
                val dispCol = if (isAudio) MediaStore.Audio.Media.DISPLAY_NAME else MediaStore.Video.Media.DISPLAY_NAME
                put(dispCol, newFullName)
                if (!isAudio) {
                    put(MediaStore.Video.Media.TITLE, newFullName.substringBeforeLast('.'))
                }
            }
            val count = contentResolver.update(uri, values, null, null)
            if (count > 0 || updated) RenameMediaResult.Success
            else RenameMediaResult.Error(context.getString(R.string.rename_failed))
        } catch (e: Exception) {
            RenameMediaResult.Error(e.localizedMessage ?: context.getString(R.string.rename_failed))
        }
    }

    suspend fun performMediaRenameUpdate(uri: Uri, newFullName: String, isAudio: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                val dispCol = if (isAudio) MediaStore.Audio.Media.DISPLAY_NAME else MediaStore.Video.Media.DISPLAY_NAME
                put(dispCol, newFullName)
                if (!isAudio) {
                    put(MediaStore.Video.Media.TITLE, newFullName.substringBeforeLast('.'))
                }
            }
            val count = contentResolver.update(uri, values, null, null)
            count > 0
        } catch (e: Exception) {
            false
        }
    }

    suspend fun renameVideo(video: VideoItem, newFullName: String): RenameMediaResult = withContext(Dispatchers.IO) {
        val uri = try {
            Uri.parse(video.uri)
        } catch (e: Exception) {
            return@withContext RenameMediaResult.Error(context.getString(R.string.delete_error_invalid_uri))
        }
        renameMediaUri(uri, video.path, video.relativePath, newFullName, isAudio = false)
    }

    suspend fun renameTrack(track: AudioTrack, newFullName: String): RenameMediaResult = withContext(Dispatchers.IO) {
        val uri = try {
            Uri.parse(track.uri)
        } catch (e: Exception) {
            return@withContext RenameMediaResult.Error(context.getString(R.string.delete_error_invalid_uri))
        }
        renameMediaUri(uri, track.path, track.relativePath, newFullName, isAudio = true)
    }

    suspend fun performRenameUpdate(video: VideoItem, newFullName: String): Boolean =
        performMediaRenameUpdate(Uri.parse(video.uri), newFullName, isAudio = false)

    suspend fun performTrackRenameUpdate(track: AudioTrack, newFullName: String): Boolean =
        performMediaRenameUpdate(Uri.parse(track.uri), newFullName, isAudio = true)

    suspend fun loadSongExtraInfo(track: AudioTrack): SongExtraInfo = withContext(Dispatchers.IO) {
        var codecStr = "Unknown"
        var bitrateStr = "Unknown"
        var channelsStr = "Unknown"

        var retriever: MediaMetadataRetriever? = null
        var extractor: MediaExtractor? = null

        val uri = try { Uri.parse(track.uri) } catch (_: Exception) { null }

        try {
            if (uri != null) {
                try {
                    retriever = MediaMetadataRetriever().apply {
                        setDataSource(context, uri)
                    }
                } catch (_: Exception) {
                    if (track.path.isNotEmpty() && File(track.path).exists()) {
                        retriever = MediaMetadataRetriever().apply {
                            setDataSource(track.path)
                        }
                    }
                }
            }

            retriever?.let { r ->
                val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                if (br != null && br > 0) {
                    bitrateStr = if (br >= 1_000_000) {
                        String.format(Locale.ROOT, "%.2f Mbps", br / 1_000_000.0)
                    } else {
                        String.format(Locale.ROOT, "%d kbps", br / 1000)
                    }
                }
            }

            if (uri != null) {
                try {
                    extractor = MediaExtractor().apply {
                        setDataSource(context, uri, null)
                    }
                } catch (_: Exception) {
                    if (track.path.isNotEmpty() && File(track.path).exists()) {
                        extractor = MediaExtractor().apply {
                            setDataSource(track.path)
                        }
                    }
                }
            }

            extractor?.let { ext ->
                for (i in 0 until ext.trackCount) {
                    val format = ext.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        codecStr = resolveCodecFriendlyName(mime)

                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            val ch = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            channelsStr = when (ch) {
                                1 -> "1 (Mono)"
                                2 -> "2 (Stereo)"
                                else -> "$ch Channels"
                            }
                        }

                        if (bitrateStr == "Unknown" && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            val br = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                            if (br > 0) {
                                bitrateStr = if (br >= 1_000_000) {
                                    String.format(Locale.ROOT, "%.2f Mbps", br / 1_000_000.0)
                                } else {
                                    String.format(Locale.ROOT, "%d kbps", br / 1000)
                                }
                            }
                        }
                        break
                    }
                }
            }
        } catch (_: Exception) {
            // Keep "Unknown" fallbacks
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }

        if (codecStr == "Unknown" && !track.mimeType.isNullOrBlank()) {
            val detected = AudioFormatDetector.detectFromMime(track.mimeType)
            if (detected.codec != "UNKNOWN" && detected.codec != "AUDIO" && detected.codec.isNotBlank()) {
                codecStr = detected.codec
            }
        }
        if (codecStr == "Unknown" && track.extension.isNotBlank()) {
            codecStr = when (track.extension.lowercase(Locale.ROOT)) {
                "flac" -> "FLAC"
                "alac" -> "ALAC"
                "wav" -> "WAV"
                "aiff", "aif" -> "AIFF"
                "mp3" -> "MP3"
                "aac", "m4a" -> "AAC"
                "ogg", "oga" -> "OGG Vorbis"
                "opus" -> "Opus"
                "wma" -> "WMA"
                else -> track.extension.uppercase(Locale.ROOT)
            }
        }

        SongExtraInfo(
            codec = codecStr,
            bitrate = bitrateStr,
            channels = channelsStr
        )
    }

    suspend fun loadVideoExtraInfo(video: VideoItem): VideoExtraInfo = withContext(Dispatchers.IO) {
        var vCodec = "Unknown"
        var aCodec = "Unknown"
        var frameRateStr = "Unknown"
        var bitrateStr = "Unknown"

        var retriever: MediaMetadataRetriever? = null
        var extractor: MediaExtractor? = null

        val uri = try { Uri.parse(video.uri) } catch (_: Exception) { null }

        try {
            if (uri != null) {
                try {
                    retriever = MediaMetadataRetriever().apply {
                        setDataSource(context, uri)
                    }
                } catch (_: Exception) {
                    if (video.path.isNotEmpty() && File(video.path).exists()) {
                        retriever = MediaMetadataRetriever().apply {
                            setDataSource(video.path)
                        }
                    }
                }
            }

            retriever?.let { r ->
                val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
                if (br != null && br > 0) {
                    bitrateStr = if (br >= 1_000_000) {
                        String.format(Locale.ROOT, "%.2f Mbps", br / 1_000_000.0)
                    } else {
                        String.format(Locale.ROOT, "%d kbps", br / 1000)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val captureFps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull()
                    if (captureFps != null && captureFps > 0) {
                        frameRateStr = if (captureFps % 1.0f == 0f) "${captureFps.toInt()} fps" else String.format(Locale.ROOT, "%.2f fps", captureFps)
                    }
                }
            }

            if (uri != null) {
                try {
                    extractor = MediaExtractor().apply {
                        setDataSource(context, uri, null)
                    }
                } catch (_: Exception) {
                    if (video.path.isNotEmpty() && File(video.path).exists()) {
                        extractor = MediaExtractor().apply {
                            setDataSource(video.path)
                        }
                    }
                }
            }

            extractor?.let { ext ->
                for (i in 0 until ext.trackCount) {
                    val format = ext.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && vCodec == "Unknown") {
                        vCodec = resolveCodecFriendlyName(mime)
                        if (frameRateStr == "Unknown" && format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            val fps = try {
                                format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                            } catch (_: Exception) {
                                try { format.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 0f }
                            }
                            if (fps > 0) {
                                frameRateStr = if (fps % 1.0f == 0f) "${fps.toInt()} fps" else String.format(Locale.ROOT, "%.2f fps", fps)
                            }
                        }
                        if (bitrateStr == "Unknown" && format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            val br = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                            if (br > 0) {
                                bitrateStr = if (br >= 1_000_000) {
                                    String.format(Locale.ROOT, "%.2f Mbps", br / 1_000_000.0)
                                } else {
                                    String.format(Locale.ROOT, "%d kbps", br / 1000)
                                }
                            }
                        }
                    } else if (mime.startsWith("audio/") && aCodec == "Unknown") {
                        aCodec = resolveCodecFriendlyName(mime)
                    }
                }
            }
        } catch (_: Exception) {
            // Keep "Unknown" fallbacks
        } finally {
            try { retriever?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
        }

        VideoExtraInfo(
            videoCodec = vCodec,
            audioCodec = aCodec,
            frameRate = frameRateStr,
            bitrate = bitrateStr
        )
    }

    private fun resolveCodecFriendlyName(mime: String): String {
        val lower = mime.lowercase(Locale.ROOT)
        return when {
            lower.contains("avc") || lower.contains("h264") -> "H.264 (AVC)"
            lower.contains("hevc") || lower.contains("h265") -> "H.265 (HEVC)"
            lower.contains("vp9") -> "VP9"
            lower.contains("vp8") -> "VP8"
            lower.contains("av01") || lower.contains("av1") -> "AV1"
            lower.contains("mp4v") -> "MPEG-4"
            lower.contains("mp4a") || lower.contains("aac") -> "AAC"
            lower.contains("mpeg") || lower.contains("mp3") -> "MP3"
            lower.contains("eac3") -> "E-AC-3"
            lower.contains("ac3") -> "AC-3"
            lower.contains("opus") -> "Opus"
            lower.contains("flac") -> "FLAC"
            lower.contains("vorbis") -> "Vorbis"
            lower.contains("raw") -> "PCM"
            else -> lower.substringAfter('/').uppercase(Locale.ROOT)
        }
    }
}
