package com.maloy.muzza.playback

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.maloy.muzza.MusicWidget.Companion.ACTION_STATE_CHANGED
import com.maloy.muzza.constants.TranslateLyricsKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.maloy.muzza.extensions.currentMetadata
import com.maloy.muzza.extensions.getCurrentQueueIndex
import com.maloy.muzza.extensions.getQueueWindows
import com.maloy.muzza.extensions.metadata
import com.maloy.muzza.playback.MusicService.MusicBinder
import com.maloy.muzza.playback.queues.Queue
import com.maloy.muzza.utils.TranslationHelper
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    val service = binder.service

    val player: ExoPlayer
        get() = service.player

    val playbackState = MutableStateFlow(service.player.playbackState)
    private val playWhenReady = MutableStateFlow(service.player.playWhenReady)
    val isPlaying = combine(playbackState, playWhenReady) { playbackState, playWhenReady ->
        playWhenReady && playbackState != STATE_ENDED
    }.stateIn(scope, SharingStarted.Lazily, service.player.playWhenReady && service.player.playbackState != STATE_ENDED)
    val mediaMetadata = MutableStateFlow(service.player.currentMetadata)
    val currentSong = mediaMetadata.flatMapLatest {
        database.song(it?.id)
    }
    internal val translating = MutableStateFlow(false)
    val currentLyrics = combine(
        context.dataStore.data.map {
            it[TranslateLyricsKey] ?: false
        }.distinctUntilChanged(),
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.lyrics(mediaMetadata?.id)
        }
    ) { translateEnabled, lyrics ->
        if (!translateEnabled || lyrics == null || lyrics.lyrics == LYRICS_NOT_FOUND) return@combine lyrics
        translating.value = true
        try {
            TranslationHelper.translate(lyrics)
        } catch (e: Exception) {
            reportException(e)
            lyrics
        }.also {
            translating.value = false
        }
    }.stateIn(scope, SharingStarted.Lazily, null)
    val currentFormat = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    private val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val error = MutableStateFlow<PlaybackException?>(null)

    val isMuted = service.isMuted
    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    var shouldBlockPlaybackChanges: (() -> Boolean)? = null

    @Volatile
    var allowInternalSync: Boolean = false

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null
    private var attachedPlayer: Player? = null
    private var playerCollectionJob: kotlinx.coroutines.Job? = null

    init {
        playerCollectionJob = scope.launch {
            service.playerFlow.collect { newPlayer ->
                if (newPlayer != null && newPlayer != attachedPlayer) {
                    updateAttachedPlayer(newPlayer)
                }
            }
        }
        if (attachedPlayer == null && service.isPlayerReady.value) {
            updateAttachedPlayer(service.player)
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        mediaMetadata.value = newPlayer.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        updateCanSkipPreviousAndNext()
    }

    fun playQueue(queue: Queue) {
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            return
        }
        service.playQueue(queue)
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))
    fun playNext(items: List<MediaItem>) {
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            return
        }
        service.playNext(items)
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))
    fun addToQueue(items: List<MediaItem>) {
        if (!allowInternalSync && shouldBlockPlaybackChanges?.invoke() == true) {
            return
        }
        service.addToQueue(items)
    }

    fun toggleLike() {
        service.toggleLike()
    }

    fun seekToNext() {
        player.seekToNext()
        if (player.playbackState == Player.STATE_IDLE || player.playbackState == STATE_ENDED) {
            player.prepare()
        }
        player.playWhenReady = true
        onSkipNext?.invoke()
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        if (player.currentPosition > 3000 || !player.hasPreviousMediaItem()) {
            player.seekTo(0)
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onRestartSong?.invoke()
        } else {
            player.seekToPreviousMediaItem()
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipPrevious?.invoke()
        }
    }

    fun play() {
        if (player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.playWhenReady = true
    }

    fun pause() {
        player.playWhenReady = false
    }

    fun seekTo(position: Long) {
        player.seekTo(position)
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(newPlayWhenReady: Boolean, reason: Int) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    || !window.isLive
                    || player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive && window.isDynamic
                    || player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    fun toggleShuffle() {
        player.shuffleModeEnabled = !player.shuffleModeEnabled
    }

    fun toggleReplayMode() {
        player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE)
            REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
    }

    fun dispose() {
        instance = null
        playerCollectionJob?.cancel()
        playerCollectionJob = null
        attachedPlayer?.removeListener(this)
        attachedPlayer = null
    }

    companion object {
        @Volatile
        var instance: PlayerConnection? = null
            private set
    }

    init {
        instance = this
        val currentPlayer = player
        val broadcastListener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION
                    )) {
                    sendStateChangedBroadcast(context)
                }
            }
        }

        scope.launch {
            service.playerFlow.collect { newPlayer ->
                currentPlayer.removeListener(broadcastListener)
                newPlayer?.addListener(broadcastListener)
            }
        }
        currentPlayer.addListener(broadcastListener)
    }

    private fun sendStateChangedBroadcast(context: Context) {
        context.sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            setPackage(context.packageName)
        })
    }
}