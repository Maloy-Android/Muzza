package com.dreamify.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamify.innertube.YouTube
import com.dreamify.innertube.models.AlbumItem
import com.dreamify.innertube.models.filterExplicit
import com.dreamify.app.constants.HideExplicitKey
import com.dreamify.app.db.MusicDatabase
import com.dreamify.app.db.entities.Artist
import com.dreamify.app.utils.dataStore
import com.dreamify.app.utils.get
import com.dreamify.app.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NewReleaseViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
) : ViewModel() {
    private val _newReleaseAlbums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val newReleaseAlbums = _newReleaseAlbums.asStateFlow()

    init {
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
}
