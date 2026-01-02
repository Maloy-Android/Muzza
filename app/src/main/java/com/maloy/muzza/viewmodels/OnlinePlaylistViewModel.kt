package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.constants.SongSortDescendingKey
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.constants.SongSortTypeKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.extensions.reversed
import com.maloy.muzza.extensions.toEnum
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
    database: MusicDatabase
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    val playlist = MutableStateFlow<PlaylistItem?>(null)

    private val _playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val playlistSongs = context.dataStore.data
        .map { preferences ->
            Pair(
                preferences[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                (preferences[SongSortDescendingKey] ?: true)
            )
        }
        .distinctUntilChanged()
        .flatMapLatest { (sortType, descending) ->
            _playlistSongs.map { songs ->
                when (sortType) {
                 SongSortType.CREATE_DATE ->
                     songs.sortedBy { it.id }

                 SongSortType.NAME ->
                     songs.sortedBy { it.title }

                 SongSortType.ARTIST ->
                     songs.sortedBy { song ->
                         song.artists.joinToString(separator = "") { it.name }
                     }

                 SongSortType.PLAY_TIME ->
                     songs.sortedBy { it.duration }
             }.reversed(descending)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyList()
        )

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set

    private var proactiveLoadJob: Job? = null

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            continuation = null
            proactiveLoadJob?.cancel()

            YouTube.playlist(playlistId)
                .onSuccess { playlistPage ->
                    playlist.value = playlistPage.playlist
                    _playlistSongs.value = playlistPage.songs.distinctBy { it.id }
                    continuation = playlistPage.songsContinuation
                    _isLoading.value = false
                    if (continuation != null) {
                        startProactiveBackgroundLoading()
                    }
                }.onFailure { throwable ->
                    _error.value = throwable.message ?: "Failed to load playlist"
                    _isLoading.value = false
                    reportException(throwable)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            _error.value = null
            continuation = null
            proactiveLoadJob?.cancel()
            try {
                YouTube.playlist(playlistId)
                    .onSuccess { playlistPage ->
                        playlist.value = playlistPage.playlist
                        _playlistSongs.value = playlistPage.songs.distinctBy { it.id }
                        continuation = playlistPage.songsContinuation
                        if (continuation != null) {
                            startProactiveBackgroundLoading()
                        }
                    }.onFailure { throwable ->
                        _error.value = throwable.message ?: "Failed to refresh playlist"
                        reportException(throwable)
                    }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    private fun startProactiveBackgroundLoading() {
        proactiveLoadJob?.cancel()
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var currentProactiveToken = continuation
            while (currentProactiveToken != null && isActive) {
                if (_isLoadingMore.value) {
                    break
                }

                YouTube.playlistContinuation(currentProactiveToken)
                    .onSuccess { playlistContinuationPage ->
                        val currentSongs = _playlistSongs.value.toMutableList()
                        currentSongs.addAll(playlistContinuationPage.songs)
                        _playlistSongs.value = currentSongs.distinctBy { it.id }
                        currentProactiveToken = playlistContinuationPage.continuation
                        this@OnlinePlaylistViewModel.continuation = currentProactiveToken
                    }.onFailure { throwable ->
                        reportException(throwable)
                        currentProactiveToken = null
                    }
            }
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return

        val tokenForManualLoad = continuation ?: return

        proactiveLoadJob?.cancel()
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.playlistContinuation(tokenForManualLoad)
                .onSuccess { playlistContinuationPage ->
                    val currentSongs = _playlistSongs.value.toMutableList()
                    currentSongs.addAll(playlistContinuationPage.songs)
                    _playlistSongs.value = currentSongs.distinctBy { it.id }
                    continuation = playlistContinuationPage.continuation
                }.onFailure { throwable ->
                    reportException(throwable)
                }.also {
                    _isLoadingMore.value = false
                    if (continuation != null && isActive) {
                        startProactiveBackgroundLoading()
                    }
                }
        }
    }


    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData()
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }
}
