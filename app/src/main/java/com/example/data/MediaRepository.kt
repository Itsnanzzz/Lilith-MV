package com.example.data

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.model.AlbumItem
import com.example.data.model.ArtistItem
import com.example.data.model.AudioTrack
import com.example.data.model.FolderItem
import com.example.data.model.SubtitleTrackInfo
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE
        )
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

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol) ?: "<unknown>"
                    val album = cursor.getString(albumCol) ?: "<unknown>"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val mime = cursor.getString(mimeCol)

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
                            albumArtUri = albumArtUri
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
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA
        )
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

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val duration = cursor.getLong(durationCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val size = cursor.getLong(sizeCol)
                    val dateModified = cursor.getLong(dateCol)
                    val path = cursor.getString(dataCol) ?: ""

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
                            thumbnailUri = contentUri
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videos
    }

    suspend fun parseTrackFromUri(uri: Uri): AudioTrack? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: context.getString(R.string.default_audio_track)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: context.getString(R.string.unknown_artist)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: context.getString(R.string.unknown_album)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            retriever.release()

            AudioTrack(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = title,
                artist = artist,
                album = album,
                durationMs = duration,
                path = uri.path ?: "",
                folderName = context.getString(R.string.subtitle_external_file),
                mimeType = mime,
                albumArtUri = uri.toString()
            )
        } catch (e: Exception) {
            AudioTrack(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = uri.lastPathSegment?.substringAfterLast('/') ?: context.getString(R.string.default_audio_track),
                artist = context.getString(R.string.unknown_artist),
                album = context.getString(R.string.default_album_title),
                durationMs = 0L
            )
        }
    }

    suspend fun parseVideoFromUri(uri: Uri): VideoItem? = withContext(Dispatchers.IO) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val title = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')
                ?: context.getString(R.string.default_video_folder)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            retriever.release()

            VideoItem(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = title,
                durationMs = duration,
                width = width,
                height = height,
                path = uri.path ?: "",
                folderName = context.getString(R.string.subtitle_external_file),
                thumbnailUri = uri.toString()
            )
        } catch (e: Exception) {
            VideoItem(
                id = System.currentTimeMillis(),
                uri = uri.toString(),
                title = uri.lastPathSegment?.substringAfterLast('/') ?: context.getString(R.string.default_video_folder),
                durationMs = 0L
            )
        }
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

    suspend fun deleteTrack(track: AudioTrack): DeleteTrackResult = withContext(Dispatchers.IO) {
        val uri = try {
            Uri.parse(track.uri)
        } catch (e: Exception) {
            return@withContext DeleteTrackResult.Error(context.getString(R.string.delete_error_invalid_uri))
        }

        // 1. SAF Documents Provider URI
        if (DocumentsContract.isDocumentUri(context, uri)) {
            return@withContext try {
                val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
                if (deleted) DeleteTrackResult.Success
                else DeleteTrackResult.Error(context.getString(R.string.delete_error_doc_readonly))
            } catch (se: SecurityException) {
                DeleteTrackResult.Error(context.getString(R.string.delete_error_readonly_permission))
            } catch (e: Exception) {
                DeleteTrackResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_doc_generic))
            }
        }

        // Check if underlying file already doesn't exist on disk
        if (track.path.isNotEmpty()) {
            val file = File(track.path)
            if (!file.exists()) {
                try {
                    contentResolver.delete(uri, null, null)
                } catch (_: Exception) {}
                return@withContext DeleteTrackResult.Success
            }
        }

        // 2. Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@withContext try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, listOf(uri))
                DeleteTrackResult.RequiresIntentSender(pendingIntent.intentSender, track)
            } catch (e: Exception) {
                try {
                    val count = contentResolver.delete(uri, null, null)
                    if (count > 0) DeleteTrackResult.Success
                    else DeleteTrackResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_request_failed))
                } catch (ex: Exception) {
                    DeleteTrackResult.Error(ex.localizedMessage ?: context.getString(R.string.delete_error_generic))
                }
            }
        }

        // 3. Android 10 (API 29)
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            return@withContext try {
                val count = contentResolver.delete(uri, null, null)
                if (count > 0) DeleteTrackResult.Success
                else DeleteTrackResult.Error(context.getString(R.string.delete_error_file_not_found))
            } catch (securityEx: SecurityException) {
                if (securityEx is android.app.RecoverableSecurityException) {
                    DeleteTrackResult.RequiresIntentSender(securityEx.userAction.actionIntent.intentSender, track)
                } else {
                    DeleteTrackResult.Error(securityEx.localizedMessage ?: context.getString(R.string.delete_error_permission_denied))
                }
            } catch (e: Exception) {
                DeleteTrackResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_generic))
            }
        }

        // 4. Android 8-9 (API 26-28)
        val hasWritePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasWritePermission) {
            return@withContext DeleteTrackResult.RequiresPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                track
            )
        }

        return@withContext try {
            if (track.path.isNotEmpty()) {
                val f = File(track.path)
                if (f.exists()) f.delete()
            }
            contentResolver.delete(uri, null, null)
            DeleteTrackResult.Success
        } catch (e: Exception) {
            DeleteTrackResult.Error(e.localizedMessage ?: context.getString(R.string.delete_error_generic))
        }
    }

    suspend fun deleteTrackAfterSecurityApproval(track: AudioTrack): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(track.uri)
            val count = contentResolver.delete(uri, null, null)
            count > 0
        } catch (e: Exception) {
            false
        }
    }
}
