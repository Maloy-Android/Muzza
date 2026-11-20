package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.pages.SearchPage
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class OnlineSearchViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    val query = MutableStateFlow("")
    val searchPage = MutableStateFlow<SearchPage?>(null)
    val suggestions = MutableStateFlow<List<String>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private var searchJob: Job? = null

    fun search(q: String, filter: String? = null) {
        query.value = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch(ioDispatcher) {
            isLoading.value = true
            error.value = null
            YouTube.search(q, filter).onSuccess { searchPage.value = it }
                .onFailure { error.value = it.message }
            isLoading.value = false
        }
    }

    fun loadSuggestions(input: String) {
        viewModelScope.launch(ioDispatcher) {
            YouTube.searchSuggestions(input).onSuccess { page ->
                suggestions.value = page.queries
            }
        }
    }

    fun saveQuery(q: String) {
        viewModelScope.launch(ioDispatcher) { repository.addSearchHistory(q) }
    }
}
