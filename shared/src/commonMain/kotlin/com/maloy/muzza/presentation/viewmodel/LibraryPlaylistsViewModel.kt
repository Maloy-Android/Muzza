package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.PlaylistEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryPlaylistsViewModel(repository: MusicRepository) : ViewModel() {
    val playlists: StateFlow<List<PlaylistEntity>> = repository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val bookmarked: StateFlow<List<PlaylistEntity>> = repository.getBookmarkedPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
