package com.zionhuang.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.pages.ExplorePage
import com.zionhuang.music.constants.ArtistFilter
import com.zionhuang.music.constants.ArtistFilterKey
import com.zionhuang.music.constants.ArtistSortDescendingKey
import com.zionhuang.music.constants.ArtistSortType
import com.zionhuang.music.constants.ArtistSortTypeKey
import com.zionhuang.music.constants.MoodAndGenresEnabled
import com.zionhuang.music.constants.NewReleasesEnabled
import com.zionhuang.music.constants.QuickPicksEnabled
import com.zionhuang.music.constants.SongFilter
import com.zionhuang.music.constants.SongFilterKey
import com.zionhuang.music.constants.SongSortDescendingKey
import com.zionhuang.music.constants.SongSortType
import com.zionhuang.music.constants.SongSortTypeKey
import com.zionhuang.music.db.MusicDatabase
import com.zionhuang.music.db.entities.Artist
import com.zionhuang.music.db.entities.Song
import com.zionhuang.music.extensions.toEnum
import com.zionhuang.music.utils.dataStore
import com.zionhuang.music.utils.get
import com.zionhuang.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    val database: MusicDatabase,
    @ApplicationContext val context: Context,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)


    val homeScreenState = context.dataStore.data
        .map {
            HomeScreenState(
                it[QuickPicksEnabled] ?: true,
                it[MoodAndGenresEnabled] ?: true,
                it[NewReleasesEnabled] ?: true,
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            HomeScreenState(
            context.dataStore[QuickPicksEnabled] ?: true,
            context.dataStore[MoodAndGenresEnabled] ?: true,
            context.dataStore[NewReleasesEnabled] ?: true,
        ))

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)

    private suspend fun load() {
        if (homeScreenState.value.quickPickEnabled)
            quickPicks.value = database.quickPicks().first().shuffled().take(20)
        if (homeScreenState.value.moodAndGenresEnabled || homeScreenState.value.newReleasesEnabled) {
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
                explorePage.value = page.copy(
                    newReleaseAlbums = page.newReleaseAlbums
                        .sortedBy { album ->
                            if (album.artists.orEmpty().any { it.id in favouriteArtists }) 0
                            else if (album.artists.orEmpty().any { it.id in artists }) 1
                            else 2
                        }
                )
            }.onFailure {
                reportException(it)
            }
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    init {
       viewModelScope.launch(Dispatchers.IO) {
            load()
        }
        viewModelScope.launch {
            homeScreenState.collectLatest {
                load()
            }
        }
    }

}

data class HomeScreenState(
    val quickPickEnabled: Boolean = true,
    val moodAndGenresEnabled: Boolean = true,
    val newReleasesEnabled: Boolean = true
)
