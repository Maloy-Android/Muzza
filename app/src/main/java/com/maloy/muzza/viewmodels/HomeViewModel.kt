package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.innertube.models.YTItem
import com.maloy.innertube.models.filterExplicit
import com.maloy.innertube.pages.ExplorePage
import com.maloy.innertube.pages.HomePage
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.Album
import com.maloy.muzza.db.entities.Artist
import com.maloy.muzza.db.entities.LocalItem
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.models.SimilarRecommendation
import com.maloy.muzza.utils.SyncUtils
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.get
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val syncUtils: SyncUtils,
    val database: MusicDatabase,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)

    val quickPicks = MutableStateFlow<List<Song>?>(null)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    private suspend fun load() {
        isLoading.value = true

        val hideExplicit = context.dataStore.get(HideExplicitKey, false)

        quickPicks.value = database.quickPicks()
            .first().shuffled().take(20)

        YouTube.libraryRecentActivity().onSuccess { page ->
            recentActivity.value = page.items.take(9).drop(1)
            recentActivity.value!!.filterIsInstance<PlaylistItem>().forEach { item ->
                val playlist = database.playlistByBrowseId(item.id).firstOrNull()
                if (playlist != null) {
                    recentPlaylistsDb.update { list ->
                        list?.plusElement(playlist) ?: listOf(playlist)
                    }
                }
            }
        }

        forgottenFavorites.value = database.forgottenFavorites()
            .first().shuffled().take(20)

        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
            .first().shuffled().take(10)
        val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
            .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
        val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp)
            .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }.shuffled().take(5)
        keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()

        allLocalItems.value = (quickPicks.value.orEmpty() + forgottenFavorites.value.orEmpty() + keepListening.value.orEmpty())
            .filter { it is Song || it is Album }


        if (YouTube.cookie != null) { // if logged in
            YouTube.likedPlaylists().onSuccess {
                accountPlaylists.value = it
            }.onFailure {
                reportException(it)
            }
        }

        // Similar to artists
        val artistRecommendations =
            database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
                .filter { it.artist.isYouTubeArtist }
                .shuffled().take(3)
                .mapNotNull {
                    val items = mutableListOf<YTItem>()
                    YouTube.artist(it.id).onSuccess { page ->
                        items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                        items += page.sections.lastOrNull()?.items.orEmpty()
                    }
                    SimilarRecommendation(
                        title = it,
                        items = items
                            .filterExplicit(hideExplicit)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }
        // Similar to songs
        val songRecommendations =
            database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
                .filter { it.album != null }
                .shuffled().take(2)
                .mapNotNull { song ->
                    val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint ?: return@mapNotNull null
                    val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                    SimilarRecommendation(
                        title = song,
                        items = (page.songs.shuffled().take(8) +
                                page.albums.shuffled().take(4) +
                                page.artists.shuffled().take(4) +
                                page.playlists.shuffled().take(4))
                            .filterExplicit(hideExplicit)
                            .shuffled()
                            .ifEmpty { return@mapNotNull null }
                    )
                }
        similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()

        YouTube.home().onSuccess { page ->
            homePage.value = page.filterExplicit(hideExplicit)
        }.onFailure {
            reportException(it)
        }

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
                    .filterExplicit(hideExplicit)
            )
        }.onFailure {
            reportException(it)
        }

        allYtItems.value = similarRecommendations.value?.flatMap { it.items }.orEmpty() +
                homePage.value?.sections?.flatMap { it.items }.orEmpty() +
                explorePage.value?.newReleaseAlbums.orEmpty()

        isLoading.value = false
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
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }
}
