@file:OptIn(ExperimentalCoroutinesApi::class)

package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.LikedMusicPlaylistFragments
import com.maloy.muzza.constants.AlbumFilter
import com.maloy.muzza.constants.AlbumFilterKey
import com.maloy.muzza.constants.AlbumSortDescendingKey
import com.maloy.muzza.constants.AlbumSortType
import com.maloy.muzza.constants.AlbumSortTypeKey
import com.maloy.muzza.constants.ArtistFilter
import com.maloy.muzza.constants.ArtistFilterKey
import com.maloy.muzza.constants.ArtistSongSortDescendingKey
import com.maloy.muzza.constants.ArtistSongSortType
import com.maloy.muzza.constants.ArtistSongSortTypeKey
import com.maloy.muzza.constants.ArtistSortDescendingKey
import com.maloy.muzza.constants.ArtistSortType
import com.maloy.muzza.constants.ArtistSortTypeKey
import com.maloy.muzza.constants.PlaylistSortDescendingKey
import com.maloy.muzza.constants.PlaylistSortType
import com.maloy.muzza.constants.PlaylistSortTypeKey
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.constants.likedMusicThumbnailKey
import com.maloy.muzza.constants.likedMusicTitleKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.di.DownloadCache
import com.maloy.muzza.di.PlayerCache
import com.maloy.muzza.extensions.toEnum
import com.maloy.muzza.playback.DownloadUtil
import com.maloy.muzza.utils.SyncUtils
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibraryArtistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    val allArtists = context.dataStore.data
        .map {
            Triple(
                it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                it[ArtistSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            when (filter) {
                ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    fun sync() { viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() } }

    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                syncUtils.syncArtistsSubscriptions()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh artists"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    ?.map { it.artist }
                    ?.filter {
                        it.thumbnailUrl == null || Duration.between(it.lastUpdateTime, LocalDateTime.now()) > Duration.ofDays(10)
                    }
                    ?.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    val allAlbums = context.dataStore.data
        .map {
            Triple(
                it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                it[AlbumSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            when (filter) {
                AlbumFilter.LIBRARY -> database.albums(sortType, descending)
                AlbumFilter.LIKED -> database.albumsLiked(sortType, descending)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    fun sync() { viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() } }

    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                syncUtils.syncLikedAlbums()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh albums"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    ?.filter {
                        it.album.songCount == 0
                    }
                    ?.forEach { album ->
                        YouTube.album(album.id).onSuccess { albumPage ->
                            database.query {
                                update(album.album, albumPage)
                            }
                        }.onFailure {
                            reportException(it)
                            if (it.message?.contains("NOT_FOUND") == true) {
                                database.query {
                                    delete(album.album)
                                }
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryPlaylistsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    val allPlaylists = context.dataStore.data
        .map {
            it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[PlaylistSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.playlists(sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun sync() { viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() } }

    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                syncUtils.syncSavedPlaylists()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh playlists"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}

@HiltViewModel
class ArtistSongsViewModel @Inject constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist = database.artist(artistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs = context.dataStore.data
        .map {
            it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey] ?: true)
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            database.artistSongs(artistId, sortType, descending)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel @Inject constructor(
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
    @PlayerCache private val playerCache: SimpleCache,
    @DownloadCache private val downloadCache: SimpleCache,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _cachedSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _playlistInfo = MutableStateFlow<LikedMusicPlaylistFragments?>(null)
    val cachedSongs: StateFlow<List<Song>> = _cachedSongs

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val savedLikedMusicThumbnail = context.dataStore.data
        .map { it[likedMusicThumbnailKey] ?: "" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    private val savedLikedMusicTitle = context.dataStore.data
        .map { it[likedMusicTitleKey] ?: "" }
        .stateIn(viewModelScope, SharingStarted.Lazily, "")

    init {
        viewModelScope.launch {
            while (true) {
                val cachedIds = playerCache.keys.map { it }.toSet()
                val downloadedIds = downloadCache.keys.map { it }.toSet()
                val pureCacheIds = cachedIds.subtract(downloadedIds)

                val songs = if (pureCacheIds.isNotEmpty()) {
                    database.getSongsByIds(pureCacheIds.toList())
                } else {
                    emptyList()
                }

                val completeSongs = songs.filter {
                    val contentLength = it.format?.contentLength
                    contentLength != null && playerCache.isCached(it.song.id, 0, contentLength)
                }

                if (completeSongs.isNotEmpty()) {
                    database.query {
                        completeSongs.forEach {
                            if (it.song.dateDownload == null) {
                                update(it.song.copy(dateDownload = LocalDateTime.now()))
                            }
                        }
                    }
                }

                _cachedSongs.value = completeSongs
                    .filter { it.song.dateDownload != null }
                    .sortedByDescending { it.song.dateDownload }

                delay(1000)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val currentThumbnail = savedLikedMusicThumbnail.first()
            val currentTitle = savedLikedMusicTitle.first()

            if (currentThumbnail.isEmpty() || currentTitle.isEmpty()) {
                try {
                    val fragments = syncUtils.getLikedMusicPlaylistFragments()
                    fragments?.let {
                        _playlistInfo.value = it
                        saveLikedMusicInfo(it.likedMusicThumbnail, it.likedMusicTitle)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                _playlistInfo.value = LikedMusicPlaylistFragments(
                    likedMusicThumbnail = currentThumbnail,
                    likedMusicTitle = currentTitle
                )
            }
        }
    }

    private suspend fun saveLikedMusicInfo(thumbnail: String?, title: String?) {
        context.dataStore.edit { preferences ->
            thumbnail?.let { preferences[likedMusicThumbnailKey] = it }
            title?.let { preferences[likedMusicTitleKey] = it }
        }
    }
    val syncAllLibrary = {
        viewModelScope.launch(Dispatchers.IO) {
            syncUtils.syncLikedSongs()
            syncUtils.syncLibrarySongs()
            syncUtils.syncArtistsSubscriptions()
            syncUtils.syncLikedAlbums()
            syncUtils.syncSavedPlaylists()
        }
    }
    fun refresh() {
        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                syncAllLibrary
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Failed to refresh library"
            } finally {
                _isRefreshing.value = false
            }
        }
    }
    val allArtists = context.dataStore.data
        .map {
            Triple(
                it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                it[ArtistSortDescendingKey] ?: true
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (filter, sortType, descending) ->
            when (filter) {
                ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    var albums = database.albumsLiked(AlbumSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val likedSongs = database.likedSongs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val librarySongs = database.librarySongs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val libraryLikedLibrarySongs = database.allSongs()
        .map { songs -> songs.filter { it.song.liked || it.song.inLibrary != null } }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val localSongs = database.localSongs(SongSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val downloadSongs =
        downloadUtil.downloads.flatMapLatest { downloads ->
            database.allSongs()
                .flowOn(Dispatchers.IO)
                .map { songs ->
                    songs.filter {
                        downloads[it.id]?.state == Download.STATE_COMPLETED
                    }
                }
        }
    val topSongs = database.mostPlayedSongs(0, 100)
}