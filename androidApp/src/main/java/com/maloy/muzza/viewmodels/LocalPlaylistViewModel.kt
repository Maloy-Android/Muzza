package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.PlaylistItem
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.constants.PlaylistSongSortDescendingKey
import com.maloy.muzza.constants.PlaylistSongSortType
import com.maloy.muzza.constants.PlaylistSongSortTypeKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.extensions.reversed
import com.maloy.muzza.extensions.toEnum
import com.maloy.muzza.utils.SyncUtils
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalPlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!

    val playlist = database.playlist(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val similarPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())

    private var similarPlaylistsJob: Job? = null

    val playlistSongs = combine(
        database.playlistSongs(playlistId),
        context.dataStore.data
            .map {
                Triple(
                    it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                    it[PlaylistSongSortDescendingKey] ?: true,
                    it[HideExplicitKey] ?: false
                )
            }
            .distinctUntilChanged()
    ) { songs, (sortType, sortDescending, hideExplicit) ->
        songs
            .filter { song -> !hideExplicit || !song.song.song.explicit }
            .let { filtered ->
                when (sortType) {
                    PlaylistSongSortType.CUSTOM -> filtered
                    PlaylistSongSortType.CREATE_DATE -> filtered.sortedBy { sortDescending }
                    PlaylistSongSortType.NAME -> filtered.sortedBy { it.song.song.title.lowercase() }
                    PlaylistSongSortType.ARTIST -> filtered.sortedBy { song ->
                        song.song.artists.joinToString { it.name }.lowercase()
                    }
                    PlaylistSongSortType.PLAY_TIME -> filtered.sortedBy { it.song.song.totalPlayTime }
                }
            }
            .reversed(!sortDescending && sortType != PlaylistSongSortType.CUSTOM)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                playlist.value?.playlist?.browseId?.let { browseId ->
                    syncUtils.syncPlaylist(browseId, playlistId)
                }
                loadSimilarPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh playlist"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun loadSimilarPlaylists() {
        val browseId = playlist.value?.playlist?.browseId ?: return
        if (similarPlaylistsJob?.isActive == true) return

        similarPlaylistsJob = viewModelScope.launch(Dispatchers.IO) {
            YouTube.playlist(browseId, setLogin = false)
                .onSuccess { playlistPage ->
                    val continuation = playlistPage.continuation ?: return@onSuccess

                    YouTube.playlistContinuation(continuation, setLogin = false)
                        .onSuccess { page ->
                            if (page.similarPlaylists.isNotEmpty()) {
                                similarPlaylists.value = page.similarPlaylists.distinctBy { it.id }
                            }
                        }
                        .onFailure { reportException(it) }
                }
                .onFailure { reportException(it) }
        }
    }

    init {
        viewModelScope.launch {
            val sortedSongs = playlistSongs.first().sortedWith(compareBy({ it.map.position }, { it.map.id }))
            database.transaction {
                sortedSongs.forEachIndexed { index, song ->
                    if (song.map.position != index) {
                        update(song.map.copy(position = index))
                    }
                }
            }
            playlist.filterNotNull().first()
            loadSimilarPlaylists()
        }
    }
}