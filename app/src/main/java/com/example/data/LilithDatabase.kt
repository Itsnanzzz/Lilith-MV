package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.db.PlaylistDao
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistSongEntity
import com.example.data.db.VideoPlaybackPositionEntity
import com.example.data.db.VideoProgressDao

@Database(
    entities = [
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        VideoPlaybackPositionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class LilithDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun videoProgressDao(): VideoProgressDao

    companion object {
        @Volatile
        private var INSTANCE: LilithDatabase? = null

        fun getInstance(context: Context): LilithDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LilithDatabase::class.java,
                    "lilith_mv.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
