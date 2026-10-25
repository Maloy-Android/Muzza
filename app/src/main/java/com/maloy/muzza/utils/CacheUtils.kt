package com.maloy.muzza.utils

import androidx.media3.datasource.cache.SimpleCache
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.di.DownloadCache
import com.maloy.muzza.di.PlayerCache
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheUtils @Inject constructor(
    val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache,
) {
    suspend fun syncCachedSongs() {
        val cachedIds = playerCache.keys.map { it }.toSet()
        val downloadedIds = downloadCache.keys.map { it }.toSet()
        val pureCacheIds = cachedIds.subtract(downloadedIds)

        val songs = if (pureCacheIds.isNotEmpty()) {
            database.getSongsByIds(pureCacheIds.toList())
        } else {
            emptyList()
        }
        val completeSongs = songs.filter {
            val contentLength = it.format?.contentLength
            contentLength != null && playerCache.isCached(it.song.id, 0, contentLength)
        }
        val completeIds = completeSongs.map { it.song.id }.toSet()

        val currentlyCached = database.cachedSongs(SongSortType.CREATE_DATE, true).first()

        database.transaction {
            completeSongs.forEach {
                if (it.song.dateDownload == null) {
                    update(it.song.copy(dateDownload = LocalDateTime.now()))
                }
                update(it.song.copy(isFromCache = true, ))
            }

            currentlyCached
                .filter { it.song.id !in completeIds }
                .forEach { update(it.song.copy(isFromCache = false)) }
        }
    }
    suspend fun removeSongFromCache(songId: String) {
        playerCache.removeResource(songId)
        val song = database.song(songId).firstOrNull()
        if (song != null) {
            database.update(song.song.copy(isFromCache = false))
        }
    }
}