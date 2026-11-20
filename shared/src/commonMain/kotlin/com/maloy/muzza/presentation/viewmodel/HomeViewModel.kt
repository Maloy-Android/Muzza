package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.models.filterExplicit
import com.maloy.innertube.pages.ExplorePage
import com.maloy.innertube.pages.HomePage
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.SongEntity
import com.maloy.muzza.util.AppSettings
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MusicRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    val quickPicks = MutableStateFlow<List<SongEntity>?>(null)
    val forgottenFavorites = MutableStateFlow<List<SongEntity>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)

    val recentActivity: StateFlow<List<Any>?> = MutableStateFlow<List<Any>?>(null)

    init {
        viewModelScope.launch(ioDispatcher) { load() }
    }

    suspend fun load() {
        isLoading.value = true
        val hideExplicit = settings.hideExplicit

        quickPicks.value = repository.quickPicks().shuffled().take(20)
        forgottenFavorites.value = repository.forgottenFavorites().shuffled().take(20)

        YouTube.home().onSuccess { page ->
            homePage.value = page.filterExplicit(hideExplicit)
        }

        YouTube.explore().onSuccess { page ->
            explorePage.value = page
        }

        isLoading.value = false
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(ioDispatcher) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        viewModelScope.launch(ioDispatcher) {
            val params = chip?.endpoint?.params
            val nextSections = YouTube.home(params = params).getOrNull() ?: return@launch
            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections
            )
            selectedChip.value = chip
        }
    }

    fun loadMoreItems(continuation: String?) {
        if (continuation == null) return
        viewModelScope.launch(ioDispatcher) {
            val next = YouTube.home(continuation).getOrNull() ?: return@launch
            homePage.value = next.copy(
                chips = homePage.value?.chips,
                sections = homePage.value?.sections.orEmpty() + next.sections
            )
        }
    }
}
