package com.maloy.muzza.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface MusicPlayer {
    val isPlaying: StateFlow<Boolean>
    val currentPositionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>

    fun play(url: String)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun stop()
}

expect fun createMusicPlayer(): MusicPlayer
