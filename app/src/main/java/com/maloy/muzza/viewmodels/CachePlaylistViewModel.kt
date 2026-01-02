package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.maloy.muzza.di.PlayerCache
import com.maloy.muzza.di.DownloadCache
import androidx.media3.datasource.cache.SimpleCache
import com.maloy.muzza.constants.SongSortDescendingKey
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.constants.SongSortTypeKey
import com.maloy.muzza.extensions.reversed
import com.maloy.muzza.extensions.toEnum
import com.maloy.muzza.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDateTime

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    private val database: MusicDatabase,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache,
    @ApplicationContext context: Context
) : ViewModel() {
    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    @OptIn(ExperimentalCoroutinesApi::class)
    val cachedSongs = context.dataStore.data
        .map { preferences ->
            Pair(
                preferences[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                (preferences[SongSortDescendingKey] ?: true)
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            _cachedSongs
                .map { songs ->
                    when (sortType) {
                        SongSortType.CREATE_DATE ->
                            songs.sortedBy { descending }

                        SongSortType.NAME ->
                            songs.sortedBy { it.song.title }

                        SongSortType.ARTIST ->
                            songs.sortedBy { song ->
                                song.artists.joinToString(separator = "") { it.name }
                            }

                        SongSortType.PLAY_TIME ->
                            songs.sortedBy { it.song.totalPlayTime }
                    }.reversed(!descending)
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            while (true) {
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

                if (completeSongs.isNotEmpty()) {
                    database.query {
                        completeSongs.forEach {
                            if (it.song.dateDownload == null) {
                                update(it.song.copy(dateDownload = LocalDateTime.now()))
                            }
                        }
                    }
                }

                _cachedSongs.value = completeSongs
                    .filter { it.song.dateDownload != null }
                    .sortedByDescending { it.song.dateDownload }

                delay(1000)
            }
        }
    }

    fun removeSongFromCache(songId: String) {
        playerCache.removeResource(songId)
    }
}