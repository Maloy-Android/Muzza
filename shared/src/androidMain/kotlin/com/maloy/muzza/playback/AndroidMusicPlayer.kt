package com.maloy.muzza.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createMusicPlayer(): MusicPlayer = AndroidMusicPlayer()

class AndroidMusicPlayer : MusicPlayer {
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _position = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _position
    private val _duration = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _duration

    override fun play(url: String) {
        _isPlaying.value = true
    }

    override fun pause() {
        _isPlaying.value = false
    }

    override fun resume() {
        _isPlaying.value = true
    }

    override fun seekTo(positionMs: Long) {
        _position.value = positionMs
    }

    override fun stop() {
        _isPlaying.value = false
        _position.value = 0
        _duration.value = 0
    }
}
