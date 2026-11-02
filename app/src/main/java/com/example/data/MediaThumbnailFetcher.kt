package com.example.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Custom Coil Fetcher that natively resolves video frame thumbnails and audio album art
 * across Android versions using ContentResolver.loadThumbnail and MediaMetadataRetriever.
 */
class MediaThumbnailFetcher(
    private val data: Uri,
    private val options: Options,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val uriStr = data.toString()
        val resolver = context.contentResolver

        val isVideo = uriStr.contains("video", ignoreCase = true) ||
                listOf(".mp4", ".mkv", ".mov", ".webm", ".avi", ".3gp", ".ts")
                    .any { uriStr.endsWith(it, ignoreCase = true) }

        if (isVideo) {
            // 1. Try ContentResolver.loadThumbnail for MediaStore video (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val bitmap = resolver.loadThumbnail(data, Size(480, 270), null)
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                } catch (_: Throwable) {
                    // Fall through to retriever
                }
            }

            // 2. Try MediaMetadataRetriever for video frame (universal)
            try {
                val mmr = MediaMetadataRetriever()
                var set = false
                try {
                    mmr.setDataSource(context, data)
                    set = true
                } catch (_: Throwable) {
                    val path = data.path
                    if (path != null && File(path).exists()) {
                        mmr.setDataSource(path)
                        set = true
                    }
                }

                if (set) {
                    // Try 1 second frame, fallback to closest sync frame
                    val frame = mmr.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: mmr.frameAtTime
                    mmr.release()

                    if (frame != null) {
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(context.resources, frame),
                            isSampled = false,
                            dataSource = DataSource.DISK
                        )
                    }
                }
            } catch (_: Throwable) {
                // Ignore and fall through
            }
        }

        val isAudio = uriStr.contains("audio", ignoreCase = true) ||
                uriStr.contains("albumart", ignoreCase = true) ||
                listOf(".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav", ".aiff")
                    .any { uriStr.endsWith(it, ignoreCase = true) }

        if (isAudio) {
            // If legacy albumart uri, try to load from MediaStore.Audio.Albums on Android 10+
            if (uriStr.contains("albumart")) {
                val albumId = data.lastPathSegment?.toLongOrNull()
                if (albumId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val albumUri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId)
                        val bitmap = resolver.loadThumbnail(albumUri, Size(512, 512), null)
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(context.resources, bitmap),
                            isSampled = false,
                            dataSource = DataSource.DISK
                        )
                    } catch (_: Throwable) {}
                }
            }

            // Try ContentResolver.loadThumbnail for audio file (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val bitmap = resolver.loadThumbnail(data, Size(512, 512), null)
                    return@withContext DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                } catch (_: Throwable) {}
            }

            // Try extracting embedded picture with MediaMetadataRetriever
            try {
                val mmr = MediaMetadataRetriever()
                var set = false
                try {
                    mmr.setDataSource(context, data)
                    set = true
                } catch (_: Throwable) {
                    val path = data.path
                    if (path != null && File(path).exists()) {
                        mmr.setDataSource(path)
                        set = true
                    }
                }

                if (set) {
                    val artBytes = mmr.embeddedPicture
                    mmr.release()
                    if (artBytes != null) {
                        val bitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                        if (bitmap != null) {
                            return@withContext DrawableResult(
                                drawable = BitmapDrawable(context.resources, bitmap),
                                isSampled = false,
                                dataSource = DataSource.DISK
                            )
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        null
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val s = data.toString()
            val matches = s.contains("video", ignoreCase = true) ||
                    s.contains("audio", ignoreCase = true) ||
                    s.contains("albumart", ignoreCase = true) ||
                    listOf(".mp4", ".mkv", ".mov", ".webm", ".avi", ".3gp", ".ts",
                        ".mp3", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wav")
                        .any { s.endsWith(it, ignoreCase = true) }

            return if (matches) MediaThumbnailFetcher(data, options, context) else null
        }
    }
}
