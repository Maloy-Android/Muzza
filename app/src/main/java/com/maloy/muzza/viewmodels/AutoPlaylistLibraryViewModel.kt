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
    val librarySongs = context.dataStore.data
        .map { preferences ->
            Pair(
                preferences[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                (preferences[SongSortDescendingKey] ?: true)
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.allSongs()
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
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )
}

