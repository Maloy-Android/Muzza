package com.dreamify.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamify.innertube.YouTube
import com.dreamify.innertube.models.PlaylistItem
import com.dreamify.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class YouTubePlaylistsViewModel @Inject constructor() : ViewModel() {
    val playlists = MutableStateFlow<List<PlaylistItem>?>(null)

    init {
        viewModelScope.launch {
            YouTube.likedPlaylists().onSuccess {
                playlists.value = it
            }.onFailure {
                reportException(it)
            }
        }
    }
}