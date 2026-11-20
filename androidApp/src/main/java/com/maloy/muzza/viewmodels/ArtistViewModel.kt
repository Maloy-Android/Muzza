package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.BrowseEndpoint
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.filterExplicit
import com.maloy.innertube.pages.ArtistPage
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.get
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val artistId = savedStateHandle.get<String>("artistId")!!
    var artistPage by mutableStateOf<ArtistPage?>(null)

    private val _expandedSections = MutableStateFlow<Map<String, List<SongItem>>>(emptyMap())
    val expandedSections = _expandedSections.asStateFlow()

    val libraryArtist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val librarySongs = database.artistSongsPreview(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private suspend fun load() {
        YouTube.artist(artistId)
            .onSuccess { page ->
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val filteredPage = page.filterExplicit(hideExplicit)
                val jobs = filteredPage.sections.mapNotNull { section ->
                    section.moreEndpoint?.let { endpoint ->
                        if (section.items.firstOrNull() is SongItem) {
                            endpoint
                        } else null
                    }
                }
                if (jobs.isNotEmpty()) {
                    val expandedResults = mutableMapOf<String, List<SongItem>>()
                    kotlinx.coroutines.coroutineScope {
                        jobs.map { endpoint ->
                            launch(Dispatchers.IO) {
                                YouTube.artistItems(BrowseEndpoint(endpoint.browseId, endpoint.params))
                                    .onSuccess { artistItemsPage ->
                                        val songs = artistItemsPage.items
                                            .filterIsInstance<SongItem>()
                                            .filterExplicit(hideExplicit)

                                        synchronized(expandedResults) {
                                            expandedResults[endpoint.browseId] = songs
                                        }
                                    }
                            }
                        }.joinAll()
                    }
                    _expandedSections.update { it + expandedResults }
                }
                artistPage = filteredPage

            }.onFailure {
                reportException(it)
            }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                load()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh artist page"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            load()
        }
    }
}