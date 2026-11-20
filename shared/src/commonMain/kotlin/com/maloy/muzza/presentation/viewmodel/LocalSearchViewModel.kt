package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.AlbumEntity
import com.maloy.muzza.domain.model.ArtistEntity
import com.maloy.muzza.domain.model.PlaylistEntity
import com.maloy.muzza.domain.model.SearchHistoryEntry
import com.maloy.muzza.domain.model.SongEntity
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalSearchViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val history: StateFlow<List<SearchHistoryEntry>> = repository.getSearchHistory()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val songs = MutableStateFlow<List<SongEntity>>(emptyList())
    val albums = MutableStateFlow<List<AlbumEntity>>(emptyList())
    val artists = MutableStateFlow<List<ArtistEntity>>(emptyList())
    val playlists = MutableStateFlow<List<PlaylistEntity>>(emptyList())

    fun search(q: String) {
        query.value = q
        viewModelScope.launch(ioDispatcher) {
            val lq = q.lowercase()
            repository.getAllSongs().collect { all ->
                songs.value = all.filter { it.title.lowercase().contains(lq) }
            }
            repository.getAllAlbums().collect { all ->
                albums.value = all.filter { it.title.lowercase().contains(lq) }
            }
            repository.getAllArtists().collect { all ->
                artists.value = all.filter { it.name.lowercase().contains(lq) }
            }
            repository.getAllPlaylists().collect { all ->
                playlists.value = all.filter { it.name.lowercase().contains(lq) }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch(ioDispatcher) { repository.clearSearchHistory() }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch(ioDispatcher) { repository.deleteSearchHistory(id) }
    }
}
