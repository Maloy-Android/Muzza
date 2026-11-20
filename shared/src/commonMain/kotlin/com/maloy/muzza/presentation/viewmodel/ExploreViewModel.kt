package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.pages.ExplorePage
import com.maloy.innertube.pages.MoodAndGenres
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel : ViewModel() {
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val moodAndGenres = MutableStateFlow<MoodAndGenres?>(null)
    val isLoading = MutableStateFlow(false)

    init {
        viewModelScope.launch(ioDispatcher) { load() }
    }

    private suspend fun load() {
        isLoading.value = true
        YouTube.explore().onSuccess { explorePage.value = it }
        YouTube.moodAndGenres().onSuccess { moodAndGenres.value = it }
        isLoading.value = false
    }

    fun refresh() {
        viewModelScope.launch(ioDispatcher) { load() }
    }
}
