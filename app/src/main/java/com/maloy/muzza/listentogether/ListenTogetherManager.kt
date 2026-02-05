package com.maloy.muzza.listentogether

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.extensions.currentMetadata
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.playback.PlayerConnection
import com.maloy.muzza.playback.queues.YouTubeQueue
import com.maloy.muzza.extensions.metadata
import com.maloy.muzza.models.MediaMetadata.Artist
import com.maloy.muzza.models.MediaMetadata.Album
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class ListenTogetherManager @Inject constructor(
    private val client: ListenTogetherClient
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var playerConnection: PlayerConnection? = null
    private var eventCollectorJob: Job? = null
    private var queueObserverJob: Job? = null
    private var playerListenerRegistered = false

    @Volatile
    private var isSyncing = false

    private var lastSyncedIsPlaying: Boolean? = null
    private var lastSyncedTrackId: String? = null

    private var bufferingTrackId: String? = null

    private var activeSyncJob: Job? = null

    private var pendingSyncState: SyncStatePayload? = null

    private var bufferCompleteReceivedForTrack: String? = null

    val connectionState = client.connectionState
    val roomState = client.roomState
    val role = client.role
    val pendingJoinRequests = client.pendingJoinRequests
    val bufferingUsers = client.bufferingUsers
    val events = client.events
    val pendingSuggestions = client.pendingSuggestions

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (isSyncing || !isHost || !isInRoom) return
            val currentTrackId = playerConnection?.player?.currentMediaItem?.mediaId
            if (currentTrackId != null && currentTrackId != lastSyncedTrackId) {
                playerConnection?.player?.currentMetadata?.let { metadata ->
                    sendTrackChangeInternal(metadata)
                    lastSyncedTrackId = currentTrackId
                    lastSyncedIsPlaying = false
                }
                if (playWhenReady) {
                    lastSyncedIsPlaying = true
                    val position = playerConnection?.player?.currentPosition ?: 0
                    client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                }
                return
            }
            sendPlayState(playWhenReady)
        }

        private fun sendPlayState(playWhenReady: Boolean) {
            val position = playerConnection?.player?.currentPosition ?: 0

            if (playWhenReady) {
                client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                lastSyncedIsPlaying = true
            } else if (lastSyncedIsPlaying == true) {
                client.sendPlaybackAction(PlaybackActions.PAUSE, position = position)
                lastSyncedIsPlaying = false
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (isSyncing || !isHost || !isInRoom) return
            if (mediaItem == null) return
            val trackId = mediaItem.mediaId
            if (trackId == lastSyncedTrackId) return
            lastSyncedTrackId = trackId
            lastSyncedIsPlaying = false
            playerConnection?.player?.currentMetadata?.let { metadata ->
                sendTrackChange(metadata)
                val isPlaying = playerConnection?.player?.playWhenReady == true
                if (isPlaying) {
                    lastSyncedIsPlaying = true
                    val position = playerConnection?.player?.currentPosition ?: 0
                    client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (isSyncing || !isHost || !isInRoom) return
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                client.sendPlaybackAction(PlaybackActions.SEEK, position = newPosition.positionMs)
            }
        }
    }

    fun setPlayerConnection(connection: PlayerConnection?) {
        if (playerListenerRegistered && playerConnection != null) {
            playerConnection?.player?.removeListener(playerListener)
            playerListenerRegistered = false
        }
        playerConnection?.shouldBlockPlaybackChanges = null
        playerConnection?.onSkipPrevious = null
        playerConnection?.onSkipNext = null
        playerConnection?.onRestartSong = null

        playerConnection = connection
        connection?.shouldBlockPlaybackChanges = {
            isInRoom && !isHost
        }
        if (connection != null && isInRoom) {
            connection.player.addListener(playerListener)
            playerListenerRegistered = true
            connection.onSkipPrevious = {
                if (isHost && !isSyncing) {
                    client.sendPlaybackAction(PlaybackActions.SKIP_PREV)
                }
            }
            connection.onSkipNext = {
                if (isHost && !isSyncing) {
                    client.sendPlaybackAction(PlaybackActions.SKIP_NEXT)
                }
            }
            connection.onRestartSong = {
                if (isHost && !isSyncing) {
                    client.sendPlaybackAction(PlaybackActions.SEEK, position = 1L)
                }
            }
        }
        if (connection != null && isInRoom && isHost) {
            startQueueSyncObservation()
            startHeartbeat()
        } else {
            stopQueueSyncObservation()
            stopHeartbeat()
        }
    }
    fun initialize() {
        eventCollectorJob?.cancel()
        eventCollectorJob = scope.launch {
            client.events.collect { event ->
                handleEvent(event)
            }
        }
        scope.launch {
            role.collect { newRole ->
                val wasHost = isHost
                if (newRole == RoomRole.HOST && !wasHost && playerConnection != null) {
                    startQueueSyncObservation()
                    startHeartbeat()
                    if (!playerListenerRegistered) {
                        playerConnection!!.player.addListener(playerListener)
                        playerListenerRegistered = true
                    }
                } else if (newRole != RoomRole.HOST && wasHost) {
                    stopQueueSyncObservation()
                    stopHeartbeat()
                }
            }
        }
    }

    private fun handleEvent(event: ListenTogetherEvent) {
        when (event) {
            is ListenTogetherEvent.RoomCreated -> {
                playerConnection?.player?.let { player ->
                    if (!playerListenerRegistered) {
                        player.addListener(playerListener)
                        playerListenerRegistered = true
                    }
                }
                lastSyncedIsPlaying = playerConnection?.player?.playWhenReady
                lastSyncedTrackId = playerConnection?.player?.currentMediaItem?.mediaId
                playerConnection?.player?.currentMetadata?.let { metadata ->
                    sendTrackChangeInternal(metadata)
                    val isPlaying = playerConnection?.player?.playWhenReady == true
                    if (isPlaying) {
                        lastSyncedIsPlaying = true
                        val position = playerConnection?.player?.currentPosition ?: 0
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                    }
                }
                startQueueSyncObservation()
                startHeartbeat()
            }

            is ListenTogetherEvent.JoinApproved -> {
                applyPlaybackState(
                    currentTrack = event.state.currentTrack,
                    isPlaying = event.state.isPlaying,
                    position = event.state.position,
                    queue = event.state.queue
                )
            }

            is ListenTogetherEvent.PlaybackSync -> {
                val actionType = event.action.action
                val isQueueOp = actionType == PlaybackActions.QUEUE_ADD ||
                        actionType == PlaybackActions.QUEUE_REMOVE ||
                        actionType == PlaybackActions.QUEUE_CLEAR
                if (!isHost || isQueueOp) {
                    handlePlaybackSync(event.action)
                }
            }

            is ListenTogetherEvent.UserJoined -> {
                if (isHost) {
                    playerConnection?.player?.currentMetadata?.let { metadata ->
                        sendTrackChangeInternal(metadata)
                        if (playerConnection?.player?.playWhenReady == true) {
                            val pos = playerConnection?.player?.currentPosition ?: 0
                            client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                        }
                    }
                }
            }

            is ListenTogetherEvent.BufferComplete -> {
                if (!isHost && bufferingTrackId == event.trackId) {
                    bufferCompleteReceivedForTrack = event.trackId
                    applyPendingSyncIfReady()
                }
            }

            is ListenTogetherEvent.SyncStateReceived -> {
                if (!isHost) {
                    handleSyncState(event.state)
                }
            }

            is ListenTogetherEvent.Kicked -> {
                cleanup()
            }

            is ListenTogetherEvent.Reconnecting -> {
            }

            is ListenTogetherEvent.Reconnected -> {
                playerConnection?.player?.let { player ->
                    if (!playerListenerRegistered) {
                        player.addListener(playerListener)
                        playerListenerRegistered = true
                    }
                }
                if (event.isHost) {
                    lastSyncedIsPlaying = playerConnection?.player?.playWhenReady
                    lastSyncedTrackId = playerConnection?.player?.currentMediaItem?.mediaId

                    val currentMetadata = playerConnection?.player?.currentMetadata
                    if (currentMetadata != null) {
                        val serverTrackId = event.state.currentTrack?.id
                        if (serverTrackId != currentMetadata.id) {
                            sendTrackChangeInternal(currentMetadata)
                        }
                        scope.launch {
                            delay(500)
                            if (playerConnection?.player?.playWhenReady == true) {
                                val pos = playerConnection?.player?.currentPosition ?: 0
                                client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                            }
                        }
                    }
                } else {
                    applyPlaybackState(
                        currentTrack = event.state.currentTrack,
                        isPlaying = event.state.isPlaying,
                        position = event.state.position,
                        queue = event.state.queue,
                        bypassBuffer = true
                    )
                    scope.launch {
                        delay(1000)
                        if (isInRoom && !isHost) {
                            requestSync()
                        }
                    }
                }
            }

            is ListenTogetherEvent.ConnectionError -> {
                cleanup()
            }

            else -> {}
        }
    }

    private fun cleanup() {
        if (playerListenerRegistered) {
            playerConnection?.player?.removeListener(playerListener)
            playerListenerRegistered = false
        }
        stopQueueSyncObservation()
        stopHeartbeat()
        lastSyncedIsPlaying = null
        lastSyncedTrackId = null
        bufferingTrackId = null
        isSyncing = false
        bufferCompleteReceivedForTrack = null
    }

    private fun applyPendingSyncIfReady() {
        val pending = pendingSyncState ?: return
        val pendingTrackId = pending.currentTrack?.id ?: bufferingTrackId ?: return
        val completeForTrack = bufferCompleteReceivedForTrack

        if (completeForTrack != pendingTrackId) return

        val player = playerConnection?.player ?: return

        isSyncing = true

        val targetPos = pending.position
        if (kotlin.math.abs(player.currentPosition - targetPos) > 100) {
            playerConnection?.seekTo(targetPos)
        }

        if (pending.isPlaying) {
            playerConnection?.play()
        } else {
            playerConnection?.pause()
        }

        scope.launch {
            delay(200)
            isSyncing = false
        }

        bufferingTrackId = null
        pendingSyncState = null
        bufferCompleteReceivedForTrack = null
    }

    private fun handlePlaybackSync(action: PlaybackActionPayload) {
        val player = playerConnection?.player ?: return

        isSyncing = true

        try {
            when (action.action) {
                PlaybackActions.PLAY -> {
                    val pos = action.position ?: 0L
                    if (kotlin.math.abs(player.currentPosition - pos) > 100) {
                        playerConnection?.seekTo(pos)
                    }
                    if (bufferingTrackId == null) {
                        playerConnection?.play()
                    }
                }

                PlaybackActions.PAUSE -> {
                    val pos = action.position ?: 0L
                    playerConnection?.pause()
                    if (kotlin.math.abs(player.currentPosition - pos) > 100) {
                        playerConnection?.seekTo(pos)
                    }
                }

                PlaybackActions.SEEK -> {
                    val pos = action.position ?: 0L
                    playerConnection?.seekTo(pos)
                }

                PlaybackActions.CHANGE_TRACK -> {
                    action.trackInfo?.let { track ->
                        if (action.queue != null && action.queue.isNotEmpty()) {
                            val queueTitle = action.queueTitle
                            applyPlaybackState(
                                currentTrack = track,
                                isPlaying = false,
                                position = 0,
                                queue = action.queue,
                                queueTitle = queueTitle
                            )
                        } else {
                            bufferingTrackId = track.id
                            syncToTrack(track, false, 0)
                        }
                    }
                }

                PlaybackActions.SKIP_NEXT -> {
                    playerConnection?.seekToNext()
                }

                PlaybackActions.SKIP_PREV -> {
                    playerConnection?.seekToPrevious()
                }

                PlaybackActions.QUEUE_ADD -> {
                    val track = action.trackInfo
                    if (track != null) {
                        scope.launch(Dispatchers.IO) {
                            YouTube.queue(listOf(track.id)).onSuccess { list ->
                                val mediaItem = list.firstOrNull()?.toMediaMetadata()?.copy(
                                    suggestedBy = track.suggestedBy
                                )?.toMediaItem()
                                if (mediaItem != null) {
                                    launch(Dispatchers.Main) {
                                        playerConnection?.allowInternalSync = true
                                        if (action.insertNext == true) {
                                            playerConnection?.playNext(mediaItem)
                                        } else {
                                            playerConnection?.addToQueue(mediaItem)
                                        }
                                        playerConnection?.allowInternalSync = false
                                    }
                                }
                            }
                        }
                    }
                }

                PlaybackActions.QUEUE_REMOVE -> {
                    val removeId = action.trackId
                    if (!removeId.isNullOrEmpty()) {
                        val player = playerConnection?.player
                        if (player != null) {
                            val startIndex = player.currentMediaItemIndex + 1
                            var removeIndex = -1
                            val total = player.mediaItemCount
                            for (i in startIndex until total) {
                                val id = player.getMediaItemAt(i).mediaId
                                if (id == removeId) { removeIndex = i; break }
                            }
                            if (removeIndex >= 0) {
                                player.removeMediaItem(removeIndex)
                            }
                        }
                    }
                }

                PlaybackActions.QUEUE_CLEAR -> {
                    val player = playerConnection?.player
                    if (player != null) {
                        val currentIndex = player.currentMediaItemIndex
                        val count = player.mediaItemCount
                        val itemsAfter = count - (currentIndex + 1)
                        if (itemsAfter > 0) {
                            player.removeMediaItems(currentIndex + 1, count - (currentIndex + 1))
                        }
                    }
                }

                PlaybackActions.SYNC_QUEUE -> {
                    val queue = action.queue
                    val queueTitle = action.queueTitle
                    if (queue != null) {
                        activeSyncJob?.cancel()

                        scope.launch(Dispatchers.Main) {
                            val player = playerConnection?.player ?: return@launch

                            val mediaItems = queue.map { track ->
                                track.toMediaMetadata().toMediaItem()
                            }

                            val currentId = player.currentMediaItem?.mediaId
                            var newIndex = -1
                            if (currentId != null) {
                                newIndex = mediaItems.indexOfFirst { it.mediaId == currentId }
                            }

                            val currentPos = player.currentPosition
                            val wasPlaying = player.isPlaying

                            playerConnection?.allowInternalSync = true
                            if (newIndex != -1) {
                                player.setMediaItems(mediaItems, newIndex, currentPos)
                            } else {
                                player.setMediaItems(mediaItems)
                            }
                            playerConnection?.allowInternalSync = false

                            if (wasPlaying && !player.isPlaying) {
                                playerConnection?.play()
                            }

                            playerConnection?.service?.queueTitle = queueTitle
                        }
                    }
                }
            }
        } finally {
            scope.launch {
                delay(200)
                isSyncing = false
            }
        }
    }

    private fun handleSyncState(state: SyncStatePayload) {
        applyPlaybackState(
            currentTrack = state.currentTrack,
            isPlaying = state.isPlaying,
            position = state.position,
            queue = state.queue,
            bypassBuffer = true
        )
    }

    private fun applyPlaybackState(
        currentTrack: TrackInfo?,
        isPlaying: Boolean,
        position: Long,
        queue: List<TrackInfo>?,
        queueTitle: String? = null,
        bypassBuffer: Boolean = false
    ) {
        val player = playerConnection?.player ?: return
        activeSyncJob?.cancel()
        if (currentTrack == null) {
            scope.launch(Dispatchers.Main) {
                isSyncing = true
                playerConnection?.allowInternalSync = true
                if (queue != null && queue.isNotEmpty()) {
                    val mediaItems = queue.map { it.toMediaMetadata().toMediaItem() }
                    player.setMediaItems(mediaItems)
                } else if (queue != null) {
                    player.clearMediaItems()
                }
                playerConnection?.pause()
                playerConnection?.service?.queueTitle = queueTitle
                playerConnection?.allowInternalSync = false
                isSyncing = false
            }
            return
        }

        bufferingTrackId = currentTrack.id

        scope.launch(Dispatchers.Main) {
            isSyncing = true
            playerConnection?.allowInternalSync = true
            try {
                if (queue != null && queue.isNotEmpty()) {
                    val mediaItems = queue.map { it.toMediaMetadata().toMediaItem() }
                    val startIndex = mediaItems.indexOfFirst { it.mediaId == currentTrack.id }
                    if (startIndex == -1) {
                        val singleItem = currentTrack.toMediaMetadata().toMediaItem()
                        player.setMediaItems(listOf(singleItem), 0, position)
                    } else {
                        player.setMediaItems(mediaItems, startIndex, position)
                    }
                } else {
                    val item = currentTrack.toMediaMetadata().toMediaItem()
                    player.setMediaItems(listOf(item), 0, position)
                }

                playerConnection?.seekTo(position)
                playerConnection?.service?.queueTitle = queueTitle ?: "Listen Together"

                if (bypassBuffer) {
                    var attempts = 0
                    while (player.playbackState != Player.STATE_READY && attempts < 100) {
                        delay(50)
                        attempts++
                    }
                    if (player.playbackState == Player.STATE_READY) {
                        player.seekTo(position)
                        if (isPlaying) {
                            playerConnection?.play()
                        } else {
                            playerConnection?.pause()
                        }
                    }
                    pendingSyncState = null
                    bufferingTrackId = null
                    bufferCompleteReceivedForTrack = null
                } else {
                    playerConnection?.pause()
                    pendingSyncState = SyncStatePayload(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        position = position,
                        lastUpdate = System.currentTimeMillis()
                    )
                    applyPendingSyncIfReady()
                    client.sendBufferReady(currentTrack.id)
                }
            } finally {
                playerConnection?.allowInternalSync = false
                delay(200)
                isSyncing = false
            }
        }
    }

    private fun syncToTrack(track: TrackInfo, shouldPlay: Boolean, position: Long) {
        bufferingTrackId = track.id
        activeSyncJob?.cancel()
        activeSyncJob = scope.launch(Dispatchers.IO) {
            try {
                YouTube.queue(listOf(track.id)).onSuccess { queue ->
                    launch(Dispatchers.Main) {
                        isSyncing = true
                        playerConnection?.allowInternalSync = true
                        playerConnection?.playQueue(
                            YouTubeQueue(
                                endpoint = WatchEndpoint(videoId = track.id),
                                preloadItem = queue.firstOrNull()?.toMediaMetadata()
                            )
                        )
                        playerConnection?.service?.queueTitle = "Listen Together"
                        playerConnection?.allowInternalSync = false
                        var waitCount = 0
                        while (waitCount < 40) {
                            val player = playerConnection?.player
                            if (player != null && player.playbackState == Player.STATE_READY) {
                                break
                            }
                            delay(50)
                            waitCount++
                        }
                        playerConnection?.pause()
                        pendingSyncState = SyncStatePayload(
                            currentTrack = track,
                            isPlaying = shouldPlay,
                            position = position,
                            lastUpdate = System.currentTimeMillis()
                        )
                        applyPendingSyncIfReady()
                        client.sendBufferReady(track.id)
                        delay(100)
                        isSyncing = false
                    }
                }.onFailure { _ ->
                    playerConnection?.allowInternalSync = false
                    isSyncing = false
                }
            } catch (_: Exception) {
                playerConnection?.allowInternalSync = false
                isSyncing = false
            }
        }
    }
    fun connect() {
        client.connect()
    }

    fun disconnect() {
        cleanup()
        client.disconnect()
    }

    fun createRoom(username: String) {
        client.createRoom(username)
    }

    fun joinRoom(roomCode: String, username: String) {
        client.joinRoom(roomCode, username)
    }

    fun leaveRoom() {
        cleanup()
        client.leaveRoom()
    }

    fun approveJoin(userId: String) = client.approveJoin(userId)

    fun rejectJoin(userId: String, reason: String? = null) = client.rejectJoin(userId, reason)

    fun kickUser(userId: String, reason: String? = null) = client.kickUser(userId, reason)

    fun sendTrackChange(metadata: MediaMetadata) {
        if (!isHost || isSyncing) return
        sendTrackChangeInternal(metadata)
    }

    private fun sendTrackChangeInternal(metadata: MediaMetadata) {
        if (!isHost) return
        val durationMs = if (metadata.duration > 0) metadata.duration.toLong() * 1000 else 180000L
        val trackInfo = TrackInfo(
            id = metadata.id,
            title = metadata.title,
            artist = metadata.artists.joinToString(", ") { it.name },
            album = metadata.album?.title,
            duration = durationMs,
            thumbnail = metadata.thumbnailUrl,
            suggestedBy = metadata.suggestedBy
        )
        val currentQueue = playerConnection?.queueWindows?.value?.map { it.toTrackInfo() }
        val currentTitle = playerConnection?.queueTitle?.value

        client.sendPlaybackAction(
            PlaybackActions.CHANGE_TRACK,
            queueTitle = currentTitle,
            trackInfo = trackInfo,
            queue = currentQueue
        )
    }

    private fun startQueueSyncObservation() {
        if (queueObserverJob?.isActive == true) return
        queueObserverJob = scope.launch {
            playerConnection?.queueWindows
                ?.map { windows ->
                    windows.map { it.toTrackInfo() }
                }
                ?.distinctUntilChanged()
                ?.collectLatest { tracks ->
                    if (!isHost || !isInRoom || isSyncing) return@collectLatest
                    delay(500)
                    client.sendPlaybackAction(
                        PlaybackActions.SYNC_QUEUE,
                        queueTitle = playerConnection?.queueTitle?.value,
                        queue = tracks
                    )
                }
        }
    }

    private fun androidx.media3.common.Timeline.Window.toTrackInfo(): TrackInfo {
        val metadata = mediaItem.metadata ?: return TrackInfo("unknown", "Unknown", "Unknown", "", 0, "")
        val durationMs = if (metadata.duration > 0) metadata.duration.toLong() * 1000 else 180000L
        return TrackInfo(
            id = metadata.id,
            title = metadata.title,
            artist = metadata.artists.joinToString(", ") { it.name },
            album = metadata.album?.title,
            duration = durationMs,
            thumbnail = metadata.thumbnailUrl,
            suggestedBy = metadata.suggestedBy
        )
    }

    private fun stopQueueSyncObservation() {
        queueObserverJob?.cancel()
        queueObserverJob = null
    }

    private fun TrackInfo.toMediaMetadata(): MediaMetadata {
        return MediaMetadata(
            id = id,
            title = title,
            artists = listOf(Artist(id = "", name = artist)),
            album = if (album != null) Album(id = "", title = album) else null,
            duration = (duration / 1000).toInt(),
            thumbnailUrl = thumbnail,
            suggestedBy = suggestedBy
        )
    }

    fun requestSync() {
        if (!isInRoom || isHost) {
            return
        }
        client.requestSync()
    }

    fun suggestTrack(track: TrackInfo) = client.suggestTrack(track)

    private var heartbeatJob: Job? = null

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (heartbeatJob?.isActive == true && isInRoom && isHost) {
                delay(15000L)
                playerConnection?.player?.let { player ->
                    if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                        val pos = player.currentPosition
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                    }
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }
}