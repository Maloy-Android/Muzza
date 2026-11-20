package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.pages.ArtistPage
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.ArtistEntity
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

class ArtistViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    val artist = MutableStateFlow<ArtistEntity?>(null)
    val artistPage = MutableStateFlow<ArtistPage?>(null)
    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun init(artistId: String) {
        viewModelScope.launch(ioDispatcher) {
            isLoading.value = true
            repository.getArtistById(artistId).collect { artist.value = it }
            YouTube.artist(artistId).onSuccess { artistPage.value = it }
                .onFailure { error.value = it.message }
            isLoading.value = false
        }
    }

    fun toggleBookmark(artistId: String) {
        val current = artist.value ?: return
        val newBookmark = if (current.bookmarkedAt != null) null
        else kotlin.time.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).toString()
        viewModelScope.launch(ioDispatcher) {
            repository.toggleArtistBookmark(artistId, newBookmark)
        }
    }
}