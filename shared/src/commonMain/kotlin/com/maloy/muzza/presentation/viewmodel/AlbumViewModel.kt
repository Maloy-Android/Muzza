package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.filterExplicit
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.AlbumEntity
import com.maloy.muzza.domain.model.SongEntity
import com.maloy.muzza.util.AppSettings
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

class AlbumViewModel(
    private val repository: MusicRepository,
    private val settings: AppSettings,
) : ViewModel() {

    var albumId: String = ""
        private set

    val album: MutableStateFlow<AlbumEntity?> = MutableStateFlow(null)
    val songs: MutableStateFlow<List<SongEntity>> = MutableStateFlow(emptyList())
    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun init(id: String) {
        albumId = id
        viewModelScope.launch(ioDispatcher) { loadAlbum() }
    }

    private suspend fun loadAlbum() {
        isLoading.value = true
        val hideExplicit = settings.hideExplicit

        // Load from local DB first
        repository.getAlbumById(albumId).collect { localAlbum ->
            album.value = localAlbum
        }

        // Then fetch from YouTube
        YouTube.album(albumId)?.onSuccess { page ->
            // Page data can be used to populate songs
        }?.onFailure {
            error.value = it.message
        }

        isLoading.value = false
    }

    fun toggleBookmark() {
        val current = album.value ?: return
        val newBookmark = if (current.bookmarkedAt != null) null else kotlin.time.Clock.System.now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).toString()
        viewModelScope.launch(ioDispatcher) {
            repository.toggleAlbumBookmark(current.id, newBookmark)
        }
    }
}