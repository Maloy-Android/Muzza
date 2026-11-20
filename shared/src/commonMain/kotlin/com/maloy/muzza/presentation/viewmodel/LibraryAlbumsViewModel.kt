package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.AlbumEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryAlbumsViewModel(repository: MusicRepository) : ViewModel() {
    val albums: StateFlow<List<AlbumEntity>> = repository.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val bookmarkedAlbums: StateFlow<List<AlbumEntity>> = repository.getBookmarkedAlbums()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
