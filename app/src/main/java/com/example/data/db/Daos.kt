package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getSongsForPlaylist(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(song: PlaylistSongEntity): Long

    @Query("DELETE FROM playlist_songs WHERE id = :songId")
    suspend fun deletePlaylistSong(songId: Long)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId AND audioUri = :audioUri LIMIT 1")
    suspend fun getPlaylistSongByUri(playlistId: Long, audioUri: String): PlaylistSongEntity?

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND audioUri = :audioUri")
    suspend fun removeSongFromPlaylist(playlistId: Long, audioUri: String): Int

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: Long)

    @Query("DELETE FROM playlist_songs WHERE audioUri = :audioUri")
    suspend fun removeSongFromAllPlaylists(audioUri: String): Int

    @Query("UPDATE playlist_songs SET audioUri = :newUri, title = :newTitle WHERE audioUri = :oldUri")
    suspend fun updateSongInAllPlaylists(oldUri: String, newUri: String, newTitle: String): Int

    @Transaction
    suspend fun migrateSongInAllPlaylists(oldUri: String, newUri: String, newTitle: String) {
        updateSongInAllPlaylists(oldUri, newUri, newTitle)
    }
}

@Dao
interface VideoProgressDao {
    @Query("SELECT * FROM video_playback_positions WHERE videoUri = :videoUri LIMIT 1")
    suspend fun getPosition(videoUri: String): VideoPlaybackPositionEntity?

    @Query("SELECT * FROM video_playback_positions WHERE title = :title LIMIT 1")
    suspend fun getPositionByTitle(title: String): VideoPlaybackPositionEntity?

    @Query("SELECT * FROM video_playback_positions WHERE videoUri = :videoUri LIMIT 1")
    fun getPositionFlow(videoUri: String): Flow<VideoPlaybackPositionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePosition(entity: VideoPlaybackPositionEntity)

    @Query("DELETE FROM video_playback_positions WHERE videoUri = :videoUri")
    suspend fun deletePosition(videoUri: String)

    @Query("DELETE FROM video_playback_positions WHERE title = :title")
    suspend fun deletePositionByTitle(title: String)

    @Query("SELECT * FROM video_playback_positions WHERE videoUri = :videoUri OR title = :title LIMIT 1")
    suspend fun getPositionByUriOrTitle(videoUri: String, title: String): VideoPlaybackPositionEntity?

    @Transaction
    suspend fun migrateProgress(oldUri: String, oldTitle: String, newUri: String, newTitle: String) {
        val existing = getPositionByUriOrTitle(oldUri, oldTitle) ?: return
        deletePosition(oldUri)
        if (existing.videoUri != oldUri) {
            deletePosition(existing.videoUri)
        }
        deletePositionByTitle(oldTitle)
        savePosition(
            existing.copy(
                videoUri = newUri,
                title = newTitle,
                lastUpdated = System.currentTimeMillis()
            )
        )
    }
}
