package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.ArtistEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LibraryArtistsViewModel(repository: MusicRepository) : ViewModel() {
    val artists: StateFlow<List<ArtistEntity>> = repository.getAllArtists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val bookmarked: StateFlow<List<ArtistEntity>> = repository.getBookmarkedArtists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
