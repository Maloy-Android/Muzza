package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.filterExplicit
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.Artist
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.get
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewReleaseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    private val _newReleaseAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val newReleaseAlbums = _newReleaseAlbums.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private fun load() {
        viewModelScope.launch {
            YouTube.newReleaseAlbums().onSuccess { albums ->
                val artists: Set<String>
                val favouriteArtists: Set<String>
                database.artistsByCreateDateAsc().first().let { list ->
                    artists = list.map(Artist::id).toHashSet()
                    favouriteArtists = list
                        .filter { it.artist.bookmarkedAt != null }
                        .map { it.id }
                        .toHashSet()
                }
                _newReleaseAlbums.value = albums
                    .sortedBy { album ->
                        if (album.artists.orEmpty().any { it.id in favouriteArtists }) 0
                        else if (album.artists.orEmpty().any { it.id in artists }) 1
                        else 2
                    }
                    .filterExplicit(context.dataStore.get(HideExplicitKey, false))
            }.onFailure {
                reportException(it)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            try {
                load()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh new releases page"
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
