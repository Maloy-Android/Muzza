package com.maloy.muzza.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumViewModel @Inject constructor(
    private val database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
) : ViewModel() {
    val albumId = savedStateHandle.get<String>("albumId")!!
    val albumWithSongs = combine(
        database.albumWithSongs(albumId),
        context.dataStore.data
            .map { it[HideExplicitKey] ?: false }
            .distinctUntilChanged()
    ) { albumWithSongs, hideExplicit ->
        if (hideExplicit && albumWithSongs != null) {
            albumWithSongs.copy(
                songs = albumWithSongs.songs.filter { !it.song.explicit }
            )
        } else {
            albumWithSongs
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val otherVersions = MutableStateFlow<List<AlbumItem>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                YouTube.album(albumId)?.onSuccess { album ->
                    val existingAlbum = database.album(albumId).first()
                    database.transaction {
                        if (existingAlbum != null) {
                            update(existingAlbum.album, album)
                        } else {
                            insert(album)
                        }
                    }
                    otherVersions.value = album.otherVersions
                }?.onFailure {
                    reportException(it)
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    init {
        viewModelScope.launch {
            val album = database.album(albumId).first()
            YouTube.album(albumId)?.onSuccess {
                if (album == null || album.album.songCount == 0) {
                    database.transaction {
                        if (album == null) insert(it)
                        else update(album.album, it)
                    }
                }
                otherVersions.value = it.otherVersions
            }?.onFailure {
                reportException(it)
                if (it.message?.contains("NOT_FOUND") == true) {
                    database.query {
                        album?.album?.let(::delete)
                    }
                }
            }
        }
    }
}