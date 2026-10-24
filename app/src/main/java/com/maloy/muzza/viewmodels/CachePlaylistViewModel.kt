package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject
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
    @OptIn(ExperimentalCoroutinesApi::class)
    val cachedSongs = context.dataStore.data.map {
        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey]
            ?: true)
    }.distinctUntilChanged().flatMapLatest { (sortType, descending) ->
        database.cachedSongs(SongSortType.CREATE_DATE, true).flowOn(Dispatchers.IO)
            .map { songs ->
                when (sortType) {
                    SongSortType.CREATE_DATE -> songs.sortedBy { it.song.isFromCache }

                    SongSortType.NAME -> songs.sortedBy { it.song.title }

                    SongSortType.ARTIST -> songs.sortedBy { song ->
                        song.artists.joinToString(separator = "") { it.name }
                    }

                    SongSortType.PLAY_TIME -> songs.sortedBy { it.song.totalPlayTime }
                        .reversed()
                }.reversed(!descending)
            }
    }.stateIn(
        scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList()
    )

    fun removeSongFromCache(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheUtils.removeSongFromCache(songId)
        }
    }
}