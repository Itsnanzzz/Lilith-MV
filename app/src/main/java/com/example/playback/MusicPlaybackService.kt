package com.example.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.LilithApplication
import com.example.MainActivity
import com.example.R
import com.example.data.model.AudioFormatInfo
import com.example.ui.common.getAppString
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

@UnstableApi
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    val equalizerController = EqualizerController()

    var currentFormatInfo: AudioFormatInfo = AudioFormatInfo()
        private set

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                LilithApplication.CHANNEL_ID_PLAYBACK,
                getAppString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getAppString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                var detectedFormat: AudioFormatInfo? = null
                for (trackGroup in tracks.groups) {
                    if (trackGroup.type == C.TRACK_TYPE_AUDIO && trackGroup.isSelected) {
                        for (i in 0 until trackGroup.length) {
                            if (trackGroup.isTrackSelected(i)) {
                                val format = trackGroup.getTrackFormat(i)
                                detectedFormat = AudioFormatDetector.detectFromFormat(format)
                                break
                            }
                        }
                    }
                }
                currentFormatInfo = detectedFormat ?: AudioFormatInfo()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                super.onAudioSessionIdChanged(audioSessionId)
                if (audioSessionId > 0) {
                    equalizerController.bindAudioSession(audioSessionId)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        getAppString(R.string.error_unsupported_codec) + " " +
                                getAppString(R.string.error_skipping_next),
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Skip to next item in playlist if available
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        serviceInstance = this
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        serviceInstance = null
        equalizerController.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        var serviceInstance: MusicPlaybackService? = null
            private set
    }
}
