package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("playlistId")]
)
data class PlaylistSongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val audioId: Long,
    val audioUri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val orderIndex: Int
)

@Entity(tableName = "video_playback_positions")
data class VideoPlaybackPositionEntity(
    @PrimaryKey
    val videoUri: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastUpdated: Long = System.currentTimeMillis()
)
