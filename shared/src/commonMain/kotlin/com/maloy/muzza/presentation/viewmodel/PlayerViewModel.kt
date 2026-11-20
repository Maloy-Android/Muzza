package com.maloy.muzza.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.domain.model.SongEntity
import com.maloy.muzza.util.AppSettings
import com.maloy.muzza.util.ioDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val repository: MusicRepository,
    private val settings: AppSettings,
) : ViewModel() {

    val currentSong = MutableStateFlow<SongEntity?>(null)
    val isPlaying = MutableStateFlow(false)
    val progress = MutableStateFlow(0f)
    val duration = MutableStateFlow(0L)
    val repeatMode = MutableStateFlow(settings.repeatMode)
    val volume = MutableStateFlow(settings.playerVolume)
    val showLyrics = MutableStateFlow(settings.showLyrics)
    val queue = MutableStateFlow<List<SongEntity>>(emptyList())

    fun toggleLike() {
        val song = currentSong.value ?: return
        viewModelScope.launch(ioDispatcher) {
            repository.toggleLike(song.id, !song.liked)
        }
    }

    fun recordPlayEvent(songId: String, playTimeMs: Long) {
        viewModelScope.launch(ioDispatcher) {
            repository.incrementPlayTime(songId, playTimeMs)
        }
    }

    fun setVolume(v: Float) {
        volume.value = v
        settings.playerVolume = v
    }

    fun toggleShowLyrics() {
        val next = !showLyrics.value
        showLyrics.value = next
        settings.showLyrics = next
    }

    fun cycleRepeatMode() {
        val next = (repeatMode.value + 1) % 3
        repeatMode.value = next
        settings.repeatMode = next
    }
}
