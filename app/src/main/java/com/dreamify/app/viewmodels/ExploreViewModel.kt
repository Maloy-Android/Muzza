package com.dreamify.app.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dreamify.innertube.YouTube
import com.dreamify.innertube.models.filterExplicit
import com.dreamify.innertube.pages.ExplorePage
import com.dreamify.app.constants.HideExplicitKey
import com.dreamify.app.db.MusicDatabase
import com.dreamify.app.db.entities.Artist
import com.dreamify.app.utils.get
import com.dreamify.app.utils.reportException
import com.dreamify.app.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
) : ViewModel() {
    val explorePage = MutableStateFlow<ExplorePage?>(null)

    private suspend fun load() {
        YouTube.explore().onSuccess { page ->
                val artists: Set<String>
                val favouriteArtists: Set<String>
                database.artistsByCreateDateAsc().first().let { list ->
                    artists = list.map(Artist::id).toHashSet()
                    favouriteArtists = list
                        .filter { it.artist.bookmarkedAt != null }
                        .map { it.id }
                        .toHashSet()
                }
                explorePage.value =
                    page.copy(
                        newReleaseAlbums =
                        page.newReleaseAlbums
                            .sortedBy { album ->
                                if (album.artists.orEmpty().any { it.id in favouriteArtists }) 0
                                else if (album.artists.orEmpty().any { it.id in artists }) 1
                                else 2
                            }
                            .filterExplicit(context.dataStore.get(HideExplicitKey, false))
                    )
            }.onFailure {
                reportException(it)
            }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }
    }
}