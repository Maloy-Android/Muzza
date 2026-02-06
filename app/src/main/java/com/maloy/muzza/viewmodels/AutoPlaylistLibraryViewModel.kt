package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutoPlaylistLibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
    private val syncUtils: SyncUtils
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    @OptIn(ExperimentalCoroutinesApi::class)
    val librarySongs =
        context.dataStore.data
            .map {
                it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE) to (it[SongSortDescendingKey]
                    ?: true)
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
            database.librarySongsByNameAsc()
                .flowOn(Dispatchers.IO)
                .map { songs ->
                    when (sortType) {
                        SongSortType.CREATE_DATE ->
                            songs.sortedBy { it.song.inLibrary }

                        SongSortType.NAME ->
                            songs.sortedBy { it.song.title }

                        SongSortType.ARTIST ->
                            songs.sortedBy { song ->
                                song.artists.joinToString(separator = "") { it.name }
                            }

                        SongSortType.PLAY_TIME ->
                            songs.sortedBy { it.song.totalPlayTime }
                    }.reversed(descending)
                }
        }.stateIn(scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList())

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                viewModelScope.launch(
                    Dispatchers.IO
                ) {
                    syncUtils.syncLibrarySongs()
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

