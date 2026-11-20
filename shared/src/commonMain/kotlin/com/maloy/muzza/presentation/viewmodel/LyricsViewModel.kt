package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.kugou.KuGou
import com.maloy.lrclib.LrcLib
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.LyricsEntity
import com.maloy.muzza.util.AppSettings
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LyricsViewModel(
    private val repository: MusicRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val songId = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<LyricsEntity?> = songId
        .flatMapLatest { id -> if (id != null) repository.getLyrics(id) else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val isLoading = MutableStateFlow(false)
    val syncedLyrics = MutableStateFlow<String?>(null)

    fun fetchLyrics(id: String, title: String, artist: String, duration: Int) {
        songId.value = id
        viewModelScope.launch(ioDispatcher) {
            // Return early if already cached
            val cached = repository.getLyrics(id)
            if (cached != null) return@launch

            isLoading.value = true
            var synced: String? = null
            var plain: String? = null

            if (settings.enableKugou) {
                KuGou.getLyrics(title, artist, duration).getOrNull()?.let { synced = it }
            }
            if (synced == null && settings.enableLrcLib) {
                LrcLib.getLyrics(title, artist, duration)
                    .getOrNull()?.let { result ->
                        synced = result
                    }
            }

            repository.upsertLyrics(LyricsEntity(id, plain, synced))
            syncedLyrics.value = synced
            isLoading.value = false
        }
    }
}
