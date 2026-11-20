package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.constants.SongSortDescendingKey
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.constants.SongSortTypeKey
import com.maloy.muzza.extensions.reversed
import com.maloy.muzza.extensions.toEnum
import com.maloy.muzza.utils.CacheUtils
import com.maloy.muzza.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

@HiltViewModel
class CachePlaylistViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val cacheUtils: CacheUtils,
    @ApplicationContext context: Context
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val cachedSongs = context.dataStore.data.map {
        Triple(
            it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
            it[SongSortDescendingKey] ?: true,
            it[HideExplicitKey] ?: false
        )
    }.distinctUntilChanged().flatMapLatest { (sortType, descending, hideExplicit) ->
        database.cachedSongsByCreateDateAsc().flowOn(Dispatchers.IO)
            .map { songs ->
                songs
                    .filter { song -> !hideExplicit || !song.song.explicit}
                    .let { filtered ->
                        when (sortType) {
                            SongSortType.CREATE_DATE -> filtered.sortedBy { it.song.dateDownload }

                            SongSortType.NAME -> filtered.sortedBy { it.song.title }

                            SongSortType.ARTIST -> filtered.sortedBy { song ->
                                song.artists.joinToString(separator = "") { it.name }
                            }

                            SongSortType.PLAY_TIME -> filtered.sortedBy { it.song.totalPlayTime }
                                .reversed()
                        }
                    }
                    .reversed(descending)
            }
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList()
    )

    fun removeSongFromCache(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheUtils.removeSongFromCache(songId)
        }
    }

    fun syncCachedSongs() {
        viewModelScope.launch(Dispatchers.IO) { cacheUtils.syncCachedSongs() }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                viewModelScope.launch(
                    Dispatchers.IO
                ) {
                    cacheUtils.syncCachedSongs()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh playlist"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}