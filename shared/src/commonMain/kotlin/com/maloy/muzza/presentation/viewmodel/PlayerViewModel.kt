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
import com.maloy.muzza.playback.MusicPlayer
import com.maloy.innertube.YouTube
import com.metrolist.innertubex.models.YouTubeClient

class PlayerViewModel(
    private val repository: MusicRepository,
    private val settings: AppSettings,
    private val player: MusicPlayer,
) : ViewModel() {

    val currentSong = MutableStateFlow<SongEntity?>(null)
    val isPlaying = MutableStateFlow(false)
    val progress = MutableStateFlow(0f)
    val duration = MutableStateFlow(0L)
    val repeatMode = MutableStateFlow(settings.repeatMode)
    val volume = MutableStateFlow(settings.playerVolume)
    val showLyrics = MutableStateFlow(settings.showLyrics)
    val queue = MutableStateFlow<List<SongEntity>>(emptyList())

    fun playSong(videoId: String) {
        viewModelScope.launch {
            YouTube.player(videoId, client = YouTubeClient.WEB_REMIX)
                .onSuccess { response ->
                    val streamUrl = response.streamingData?.adaptiveFormats
                        ?.firstOrNull { it.isAudio }
                        ?.url
                    if (streamUrl != null) {
                        player.play(streamUrl)
                        isPlaying.value = true
                    }
                }
        }
    }

    fun togglePlayback() {
        if (player.isPlaying.value) player.pause() else player.resume()
        isPlaying.value = player.isPlaying.value
    }

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
