@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.maloy.muzza.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds

actual fun createMusicPlayer(): MusicPlayer = IosMusicPlayer()

class IosMusicPlayer : MusicPlayer {
    private val player = AVPlayer()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    override val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs
    private var pollingJob: Job? = null

    init {
        pollingJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                if (_isPlaying.value) {
                    val position = CMTimeGetSeconds(player.currentTime())
                    if (position.isFinite()) {
                        _currentPositionMs.value = (position * 1000).toLong()
                    }
                    val duration = player.currentItem?.duration?.let { CMTimeGetSeconds(it) }
                    if (duration != null && duration.isFinite()) {
                        _durationMs.value = (duration * 1000).toLong()
                    }
                }
                delay(500)
            }
        }
    }

    override fun play(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        player.replaceCurrentItemWithPlayerItem(AVPlayerItem(nsUrl))
        player.play()
        _isPlaying.value = true
    }

    override fun pause() {
        player.pause()
        _isPlaying.value = false
    }

    override fun resume() {
        player.play()
        _isPlaying.value = true
    }

    override fun seekTo(positionMs: Long) {
        player.seekToTime(CMTimeMakeWithSeconds(positionMs / 1000.0, 600))
    }

    override fun stop() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        _isPlaying.value = false
        _currentPositionMs.value = 0
        _durationMs.value = 0
    }
}
