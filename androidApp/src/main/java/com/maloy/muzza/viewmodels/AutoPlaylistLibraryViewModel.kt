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
import com.maloy.muzza.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AutoPlaylistLibraryViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    val librarySongs =
        context.dataStore.data
            .map { prefs ->
                val sortType = prefs[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE)
                val descending = prefs[SongSortDescendingKey] ?: true
                val hideExplicit = prefs[HideExplicitKey] ?: false
                Triple(sortType, descending, hideExplicit)
            }
            .distinctUntilChanged()
            .flatMapLatest { (sortType, descending, hideExplicit) ->
            database.librarySongs(SongSortType.CREATE_DATE, true)
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
        }.stateIn(scope = viewModelScope, started = SharingStarted.Lazily, initialValue = emptyList())
}

