package com.maloy.muzza.utils

import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.utils.completed
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.models.toMediaMetadata
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncUtils @Inject constructor(
    val database: MusicDatabase,
) {
    suspend fun syncLikedSongs() {
        YouTube.playlist("LM").completed().onSuccess { page ->
            val songs = page.songs.reversed()
            database.likedSongsByNameAsc().first()
                .filterNot { it.id in songs.map(SongItem::id) }
                .forEach { database.update(it.song.localToggleLike()) }
            songs.forEach { song ->
                val dbSong = database.song(song.id).firstOrNull()
                database.transaction {
                    when (dbSong) {
                        null -> insert(song.toMediaMetadata(), SongEntity::localToggleLike)
                        else -> if (!dbSong.song.liked) update(dbSong.song.localToggleLike())
                    }
                }
            }
        }
    }
}