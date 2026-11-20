package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.pages.PlaylistPage
import com.maloy.innertube.utils.completed
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OnlinePlaylistViewModel : ViewModel() {
    val playlistPage = MutableStateFlow<PlaylistPage?>(null)
    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun load(playlistId: String) {
        viewModelScope.launch(ioDispatcher) {
            isLoading.value = true
            YouTube.playlist(playlistId).completed()
                .onSuccess { playlistPage.value = it }
                .onFailure { error.value = it.message }
            isLoading.value = false
        }
    }
}
