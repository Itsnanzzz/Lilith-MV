package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.data.LilithDatabase
import com.example.data.MediaThumbnailFetcher
import com.example.ui.common.getAppLocales
import com.example.ui.common.getAppString
import com.example.ui.common.setAppLocales

class LilithApplication : Application(), ImageLoaderFactory {

    lateinit var database: LilithDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        com.example.ui.common.logLangDebug(this, "App.onCreate.beforeDefault")
        if (getAppLocales(this).isEmpty) {
            setAppLocales(this, "id")
        }
        com.example.ui.common.logLangDebug(this, "App.onCreate.afterDefault")
        instance = this
        database = LilithDatabase.getInstance(this)
        createNotificationChannel()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(MediaThumbnailFetcher.Factory(this@LilithApplication))
                add(VideoFrameDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("media_thumbnails"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_PLAYBACK,
                getAppString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getAppString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_PLAYBACK = "lilith_media_playback"
        lateinit var instance: LilithApplication
            private set
    }
}
