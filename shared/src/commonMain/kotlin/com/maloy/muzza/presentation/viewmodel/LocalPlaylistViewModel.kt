package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.PlaylistEntity
import com.maloy.muzza.domain.model.PlaylistSongMap
import com.maloy.muzza.domain.model.SongEntity
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LocalPlaylistViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    val playlistId = MutableStateFlow<String?>(null)
    val playlist = MutableStateFlow<PlaylistEntity?>(null)
    val songs = MutableStateFlow<List<PlaylistSongMap>>(emptyList())

    fun init(id: String) {
        playlistId.value = id
        viewModelScope.launch(ioDispatcher) {
            repository.getPlaylistById(id).collect { playlist.value = it }
        }
        viewModelScope.launch(ioDispatcher) {
            repository.getPlaylistSongs(id).collect { songs.value = it }
        }
    }

    fun addSong(songId: String) {
        val id = playlistId.value ?: return
        viewModelScope.launch(ioDispatcher) {
            repository.addSongToPlaylist(id, songId, songs.value.size)
        }
    }

    fun removeSong(songId: String) {
        val id = playlistId.value ?: return
        viewModelScope.launch(ioDispatcher) {
            repository.removeSongFromPlaylist(id, songId)
        }
    }

    fun deletePlaylist() {
        val id = playlistId.value ?: return
        viewModelScope.launch(ioDispatcher) {
            repository.deletePlaylist(id)
        }
    }
}
