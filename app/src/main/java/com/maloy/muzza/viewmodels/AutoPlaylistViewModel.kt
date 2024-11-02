package com.maloy.muzza.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.maloy.muzza.constants.SongSortType
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.playback.DownloadUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
@HiltViewModel
class AutoPlaylistViewModel  @Inject constructor(
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlist = savedStateHandle.get<String>("playlist")!!
    @OptIn(ExperimentalCoroutinesApi::class)
    val likedSongs =
        if (playlist == "liked") {
            database.likedSongs(SongSortType.CREATE_DATE, true)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        } else {
            downloadUtil.downloads.flatMapLatest { downloads ->
                database.allSongs()
                    .flowOn(Dispatchers.IO)
                    .map { songs ->
                        songs.filter {
                            downloads[it.id]?.state == Download.STATE_COMPLETED
                        }
                    }
            }
        }
}