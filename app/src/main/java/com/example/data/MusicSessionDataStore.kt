package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import org.json.JSONArray

val Context.musicSessionDataStore by preferencesDataStore(name = "lilith_music_session")

data class MusicSessionSnapshot(
    val songUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: String?,
    val durationMs: Long,
    val positionMs: Long,
    val queueUris: List<String>,
    val queueIndex: Int,
    val repeatMode: Int,
    val shuffleEnabled: Boolean
)

class MusicSessionDataStore(private val context: Context) {

    private object Keys {
        val SONG_URI = stringPreferencesKey("session_song_uri")
        val TITLE = stringPreferencesKey("session_title")
        val ARTIST = stringPreferencesKey("session_artist")
        val ALBUM = stringPreferencesKey("session_album")
        val ALBUM_ART_URI = stringPreferencesKey("session_album_art_uri")
        val DURATION_MS = longPreferencesKey("session_duration_ms")
        val POSITION_MS = longPreferencesKey("session_position_ms")
        val QUEUE_URIS_JSON = stringPreferencesKey("session_queue_uris_json")
        val QUEUE_INDEX = intPreferencesKey("session_queue_index")
        val REPEAT_MODE = intPreferencesKey("session_repeat_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("session_shuffle_enabled")
    }

    val sessionFlow: Flow<MusicSessionSnapshot?> = context.musicSessionDataStore.data.map { prefs ->
        val songUri = prefs[Keys.SONG_URI] ?: return@map null
        val title = prefs[Keys.TITLE] ?: return@map null
        val artist = prefs[Keys.ARTIST] ?: ""
        val album = prefs[Keys.ALBUM] ?: ""
        val albumArtUri = prefs[Keys.ALBUM_ART_URI]
        val durationMs = prefs[Keys.DURATION_MS] ?: 0L
        val positionMs = prefs[Keys.POSITION_MS] ?: 0L
        val queueUrisJson = prefs[Keys.QUEUE_URIS_JSON] ?: "[]"
        val queueIndex = prefs[Keys.QUEUE_INDEX] ?: 0
        val repeatMode = prefs[Keys.REPEAT_MODE] ?: 0
        val shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false

        val queueUris = mutableListOf<String>()
        try {
            val jsonArray = JSONArray(queueUrisJson)
            for (i in 0 until jsonArray.length()) {
                queueUris.add(jsonArray.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        MusicSessionSnapshot(
            songUri = songUri,
            title = title,
            artist = artist,
            album = album,
            albumArtUri = albumArtUri,
            durationMs = durationMs,
            positionMs = positionMs,
            queueUris = queueUris,
            queueIndex = queueIndex,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled
        )
    }

    suspend fun getSessionSnapshot(): MusicSessionSnapshot? {
        return sessionFlow.firstOrNull()
    }

    suspend fun saveSession(snapshot: MusicSessionSnapshot) {
        val queueJson = JSONArray(snapshot.queueUris).toString()
        context.musicSessionDataStore.edit { prefs ->
            prefs[Keys.SONG_URI] = snapshot.songUri
            prefs[Keys.TITLE] = snapshot.title
            prefs[Keys.ARTIST] = snapshot.artist
            prefs[Keys.ALBUM] = snapshot.album
            if (snapshot.albumArtUri != null) {
                prefs[Keys.ALBUM_ART_URI] = snapshot.albumArtUri
            } else {
                prefs.remove(Keys.ALBUM_ART_URI)
            }
            prefs[Keys.DURATION_MS] = snapshot.durationMs
            prefs[Keys.POSITION_MS] = snapshot.positionMs
            prefs[Keys.QUEUE_URIS_JSON] = queueJson
            prefs[Keys.QUEUE_INDEX] = snapshot.queueIndex
            prefs[Keys.REPEAT_MODE] = snapshot.repeatMode
            prefs[Keys.SHUFFLE_ENABLED] = snapshot.shuffleEnabled
        }
    }

    suspend fun clearSession() {
        context.musicSessionDataStore.edit { prefs ->
            prefs.remove(Keys.SONG_URI)
            prefs.remove(Keys.TITLE)
            prefs.remove(Keys.ARTIST)
            prefs.remove(Keys.ALBUM)
            prefs.remove(Keys.ALBUM_ART_URI)
            prefs.remove(Keys.DURATION_MS)
            prefs.remove(Keys.POSITION_MS)
            prefs.remove(Keys.QUEUE_URIS_JSON)
            prefs.remove(Keys.QUEUE_INDEX)
            prefs.remove(Keys.REPEAT_MODE)
            prefs.remove(Keys.SHUFFLE_ENABLED)
        }
    }
}
