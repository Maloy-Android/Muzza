package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.constants.SongSortDescendingKey
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.constants.SongSortTypeKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.extensions.reversed
import com.maloy.muzza.extensions.toEnum
import com.maloy.muzza.utils.SyncUtils
import com.maloy.muzza.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AutoPlaylistLikedViewModel  @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs =
        context.dataStore.data
            .map { prefs ->
                val sortType = prefs[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE)
                val descending = prefs[SongSortDescendingKey] ?: true
                val hideExplicit = prefs[HideExplicitKey] ?: false
                Triple(sortType, descending, hideExplicit)
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortType, descending, hideExplicit) ->
                database.likedSongsByCreateDateAsc()
                    .flowOn(Dispatchers.IO)
                    .map { songs ->
                        songs
                            .filter { song ->
                                !hideExplicit || !song.song.explicit
                            }
                            .sortedWith(
                                when (sortType) {
                                    SongSortType.CREATE_DATE -> compareBy { it.song.liked }
                                    SongSortType.NAME -> compareBy { it.song.title }
                                    SongSortType.ARTIST -> compareBy { song ->
                                        song.artists.joinToString(separator = "") { it.name }
                                    }
                                    SongSortType.PLAY_TIME -> compareBy { it.song.totalPlayTime }
                                }
                            )
                            .let { if (descending) it.reversed() else it }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                viewModelScope.launch(
                    Dispatchers.IO
                ) {
                    syncUtils.syncLikedSongs()
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