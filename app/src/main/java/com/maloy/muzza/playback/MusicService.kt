package com.maloy.muzza.playback

import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Binder
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.MainActivity
import com.maloy.muzza.R
import com.maloy.muzza.constants.AddingPlayedSongsToYTMHistoryKey
import com.maloy.muzza.constants.AudioNormalizationKey
import com.maloy.muzza.constants.AudioOffload
import com.maloy.muzza.constants.AudioQuality
import com.maloy.muzza.constants.AudioQualityKey
import com.maloy.muzza.constants.AutoLoadMoreKey
import com.maloy.muzza.constants.AutoPlaySongWhenBluetoothDeviceConnectedKey
import com.maloy.muzza.constants.AutoSkipNextOnErrorKey
import com.maloy.muzza.constants.DiscordTokenKey
import com.maloy.muzza.constants.EnableDiscordRPCKey
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.constants.KeepAliveKey
import com.maloy.muzza.constants.MediaSessionConstants.CommandToggleLibrary
import com.maloy.muzza.constants.MediaSessionConstants.CommandToggleLike
import com.maloy.muzza.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.maloy.muzza.constants.MediaSessionConstants.CommandToggleShuffle
import com.maloy.muzza.constants.MediaSessionConstants.CommandToggleStartRadio
import com.maloy.muzza.constants.PauseListenHistoryKey
import com.maloy.muzza.constants.PersistentQueueKey
import com.maloy.muzza.constants.PlayerVolumeKey
import com.maloy.muzza.constants.RepeatModeKey
import com.maloy.muzza.constants.ShowLyricsKey
import com.maloy.muzza.constants.SkipSilenceKey
import com.maloy.muzza.constants.StopPlayingSongWhenMinimumVolumeKey
import com.maloy.muzza.constants.minPlaybackDurKey
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.Event
import com.maloy.muzza.db.entities.FormatEntity
import com.maloy.muzza.db.entities.LyricsEntity
import com.maloy.muzza.db.entities.RelatedSongMap
import com.maloy.muzza.di.DownloadCache
import com.maloy.muzza.di.PlayerCache
import com.maloy.muzza.extensions.SilentHandler
import com.maloy.muzza.extensions.collect
import com.maloy.muzza.extensions.collectLatest
import com.maloy.muzza.extensions.currentMetadata
import com.maloy.muzza.extensions.findNextMediaItemById
import com.maloy.muzza.extensions.mediaItems
import com.maloy.muzza.extensions.metadata
import com.maloy.muzza.extensions.setOffloadEnabled
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.lyrics.LyricsHelper
import com.maloy.muzza.models.PersistQueue
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.queues.EmptyQueue
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.playback.queues.Queue
import com.maloy.muzza.playback.queues.YouTubeQueue
import com.maloy.muzza.playback.queues.filterExplicit
import com.maloy.muzza.utils.CoilBitmapLoader
import com.maloy.muzza.utils.DiscordRPC
import com.maloy.muzza.utils.YTPlayerUtils
import com.maloy.muzza.utils.dataStore
import com.maloy.muzza.utils.enumPreference
import com.maloy.muzza.utils.get
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.reportException
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds


@Suppress("DEPRECATION")
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@AndroidEntryPoint
class MusicService : MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    private var scope = CoroutineScope(Dispatchers.Main) + Job()
    private val binder = MusicBinder()

    private lateinit var connectivityManager: ConnectivityManager

    private val audioQuality by enumPreference(this, AudioQualityKey, AudioQuality.AUTO)

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    private var consecutivePlaybackErr = 0

    val currentMediaMetadata = MutableStateFlow<com.maloy.muzza.models.MediaMetadata?>(null)
    private val currentSong = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.song(mediaMetadata?.id)
    }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat = currentMediaMetadata.flatMapLatest { mediaMetadata ->
        database.format(mediaMetadata?.id)
    }

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var isNormalizationEnabled = false
    val playerVolume = MutableStateFlow(dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f))

    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession

    private var isAudioEffectSessionOpened = false

    private var discordRpc: DiscordRPC? = null

    private var bluetoothReceiver: BroadcastReceiver? = null

    private var wasPlayingBeforeMute = false

    private var volumeReceiver: BroadcastReceiver? = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (dataStore.get(StopPlayingSongWhenMinimumVolumeKey, true)) {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                if (currentVolume == 0 && player.isPlaying) {
                    wasPlayingBeforeMute = true
                    player.pause()
                } else if (currentVolume > 0 && !player.isPlaying && wasPlayingBeforeMute) {
                    player.play()
                    wasPlayingBeforeMute = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(volumeReceiver, IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
        })
        playerVolume
            .debounce(100)
            .collectLatest(scope) { volume ->
                when {
                    volume == 0f && player.isPlaying && dataStore.get(
                        StopPlayingSongWhenMinimumVolumeKey,
                        true
                    ) -> {
                        wasPlayingBeforeMute = true
                        player.pause()
                    }

                    volume > 0f && !player.isPlaying && wasPlayingBeforeMute && dataStore.get(
                        StopPlayingSongWhenMinimumVolumeKey,
                        true
                    ) -> {
                        player.play()
                        wasPlayingBeforeMute = false
                    }
                }
            }
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.small_icon)
                }
        )
        if (dataStore.get(KeepAliveKey, false)) {
            try {
                startService(Intent(this, KeepAlive::class.java))
            } catch (e: Exception) {
                reportException(e)
            }
        } else {
            try {
                stopService(Intent(this, KeepAlive::class.java))
            } catch (e: Exception) {
                reportException(e)
            }
        }
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(createDataSourceFactory()))
            .setRenderersFactory(createRenderersFactory())
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(), true
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
            .apply {
                addListener(this@MusicService)
                setOffloadEnabled(dataStore.get(AudioOffload, false))
                sleepTimer = SleepTimer(scope, this)
                addListener(sleepTimer)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))
            }
        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession = MediaLibrarySession.Builder(this, player, mediaLibrarySessionCallback)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setBitmapLoader(CoilBitmapLoader(this, scope))
            .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            if (song != null) {
                discordRpc?.updateSong(song)
            } else {
                discordRpc?.closeRPC()
            }
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged()
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyrics = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyrics
                        )
                    )
                }
            }
        }

        dataStore.data
            .map { it[SkipSilenceKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                player.skipSilenceEnabled = it
            }

        combine(
            playerVolume,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
            currentFormat
        ) { volume, normalizeAudio, format ->
            Triple(volume, normalizeAudio, format)
        }.collectLatest(scope) { (volume, normalizeAudio, format) ->
            player.volume = volume
            isNormalizationEnabled = normalizeAudio

            try {
                if (normalizeAudio && format?.loudnessDb != null) {
                    var gain = (-format.loudnessDb * 100).toInt()
                    gain = gain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                    loudnessEnhancer?.setTargetGain(gain)
                    loudnessEnhancer?.enabled = true
                } else {
                    loudnessEnhancer?.enabled = false
                }
            } catch (e: Exception) {
                loudnessEnhancer?.enabled = false
            }
        }

        dataStore.data
            .map { it[DiscordTokenKey] to (it[EnableDiscordRPCKey] ?: true) }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { (key, enabled) ->
                if (discordRpc?.isRpcRunning() == true) {
                    discordRpc?.closeRPC()
                }
                discordRpc = null
                if (key != null && enabled) {
                    discordRpc = DiscordRPC(this, key)
                    currentSong.value?.let {
                        discordRpc?.updateSong(it)
                    }
                }
            }

        if (dataStore.get(PersistentQueueKey, true)) {
            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).inputStream().use { fis ->
                    ObjectInputStream(fis).use { oos ->
                        oos.readObject() as PersistQueue
                    }
                }
            }.onSuccess { queue ->
                playQueue(
                    queue = ListQueue(
                        title = queue.title,
                        items = queue.items.map { it.toMediaItem() },
                        startIndex = queue.mediaItemIndex,
                        position = queue.position
                    ),
                    playWhenReady = false
                )
            }
        }

        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.get(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (dataStore.get(AutoPlaySongWhenBluetoothDeviceConnectedKey, true)) {
                    when (intent.action) {
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            if (player.playbackState == Player.STATE_READY
                                && !player.isPlaying
                                && dataStore.get(PersistentQueueKey, true)
                            ) {
                                player.play()
                            }
                        }
                    }
                }
            }
        }.apply {
            registerReceiver(this, IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            })
        }
        initializeLoudnessEnhancer()
    }

    private fun initializeLoudnessEnhancer() {
        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(player.audioSessionId)
            }
            loudnessEnhancer?.enabled = false
        } catch (e: Exception) {
            loudnessEnhancer = null
        }
    }

    private fun updateNotification() {
        mediaSession.setCustomLayout(
            listOf(
                CommandButton.Builder()
                    .setDisplayName(getString(if (currentSong.value?.song?.inLibrary != null) R.string.remove_from_library else R.string.add_to_library))
                    .setIconResId(if (currentSong.value?.song?.inLibrary != null) R.drawable.library_add_check else R.drawable.library_add)
                    .setSessionCommand(CommandToggleLibrary)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder()
                    .setDisplayName(getString(if (currentSong.value?.song?.liked == true) R.string.action_remove_like else R.string.action_like))
                    .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.favorite else R.drawable.favorite_border)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                if (currentSong.value?.song?.isLocal != true) {
                    CommandButton.Builder()
                        .setDisplayName(getString(R.string.start_radio))
                        .setIconResId(R.drawable.radio)
                        .setSessionCommand(CommandToggleStartRadio)
                        .setEnabled(currentSong.value != null)
                        .build()
                } else {
                    CommandButton.Builder()
                        .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                        .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                        .setSessionCommand(CommandToggleShuffle)
                        .build()
                    CommandButton.Builder()
                        .setDisplayName(
                            getString(
                                when (player.repeatMode) {
                                    REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                    REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                    REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                    else -> throw IllegalStateException()
                                }
                            )
                        )
                        .setIconResId(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.drawable.repeat
                                REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                REPEAT_MODE_ALL -> R.drawable.repeat_on
                                else -> throw IllegalStateException()
                            }
                        )
                        .setSessionCommand(CommandToggleRepeatMode)
                        .build()
                }
            ) as MutableList<CommandButton>
        )
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else if (song.song.duration == -1) update(song.song.copy(duration = duration))
        }
        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(queue: Queue, playWhenReady: Boolean = true) {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.Main) + Job()
        }
        currentQueue = queue
        queueTitle = null
        player.shuffleModeEnabled = false
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }

        scope.launch(SilentHandler) {
            val initialStatus = withContext(Dispatchers.IO) {
                queue.getInitialStatus().filterExplicit(dataStore.get(HideExplicitKey, false))
            }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            if (queue.preloadItem != null) {
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        initialStatus.mediaItemIndex + 1,
                        initialStatus.items.size
                    )
                )
            } else {
                player.setMediaItems(
                    initialStatus.items,
                    if (initialStatus.mediaItemIndex > 0) initialStatus.mediaItemIndex else 0,
                    initialStatus.position
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }
        }
    }

    fun startRadioSeamlessly() {
        val currentMediaMetadata = player.currentMetadata ?: return
        if (player.currentMediaItemIndex > 0) player.removeMediaItems(
            0,
            player.currentMediaItemIndex
        )
        if (player.currentMediaItemIndex < player.mediaItemCount - 1) player.removeMediaItems(
            player.currentMediaItemIndex + 1,
            player.mediaItemCount
        )
        scope.launch(SilentHandler) {
            val radioQueue =
                YouTubeQueue(endpoint = WatchEndpoint(videoId = currentMediaMetadata.id))
            val initialStatus = radioQueue.getInitialStatus()
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            player.addMediaItems(initialStatus.items.drop(1))
            currentQueue = radioQueue
        }
    }

    fun playNext(items: List<MediaItem>) {
        player.addMediaItems(
            if (player.mediaItemCount == 0) 0 else player.currentMediaItemIndex + 1,
            items
        )
        player.prepare()
    }

    fun addToQueue(items: List<MediaItem>) {
        player.addMediaItems(items)
        player.prepare()
    }

    fun toggleLibrary() {
        database.query {
            currentSong.value?.let {
                update(it.song.toggleLibrary())
            }
        }
    }

    fun toggleLike() {
        database.query {
            currentSong.value?.let {
                val song = it.song.toggleLike()
                update(song)
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        try {
            isAudioEffectSessionOpened = true
            if (isNormalizationEnabled) {
                loudnessEnhancer?.enabled = true
            }

            sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                },
            )
        } catch (e: Exception) {
            isAudioEffectSessionOpened = false
        }
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        loudnessEnhancer?.enabled = false
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private fun applyAudioNormalizationSettings() {
        scope.launch {
            val normalizeAudio = dataStore.data.first()[AudioNormalizationKey] ?: true
            val format = currentFormat.first()
            isNormalizationEnabled = normalizeAudio

            try {
                if (normalizeAudio && format?.loudnessDb != null) {
                    var gain = (-format.loudnessDb * 100).toInt()
                    gain = gain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)
                    loudnessEnhancer?.setTargetGain(gain)
                    loudnessEnhancer?.enabled = true
                } else {
                    loudnessEnhancer?.enabled = false
                }
            } catch (e: Exception) {
                loudnessEnhancer?.enabled = false
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        if (consecutivePlaybackErr > 0) {
            consecutivePlaybackErr--
        }

        if (player.isPlaying && reason == MEDIA_ITEM_TRANSITION_REASON_SEEK) {
            player.prepare()
            player.play()
        }
        if (dataStore.get(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() && player.currentMediaItemIndex < player.mediaItemCount - 1
        ) {
            scope.launch(SilentHandler) {
                val mediaItems =
                    currentQueue.nextPage().filterExplicit(dataStore.get(HideExplicitKey, false))
                if (player.playbackState != STATE_IDLE) {
                    player.addMediaItems(mediaItems.drop(1))
                }
            }
        }
    }

    override fun onPlaybackStateChanged(@Player.State playbackState: Int) {
        if (playbackState == STATE_IDLE) {
            currentQueue = EmptyQueue
            player.shuffleModeEnabled = false
            queueTitle = null
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                openAudioEffectSession()
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.contains(Player.EVENT_AUDIO_SESSION_ID)) {
            initializeLoudnessEnhancer()
            applyAudioNormalizationSettings()
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
    }


    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            val shuffledIndices = IntArray(player.mediaItemCount) { it }
            shuffledIndices.shuffle()
            shuffledIndices[shuffledIndices.indexOf(player.currentMediaItemIndex)] =
                shuffledIndices[0]
            shuffledIndices[0] = player.currentMediaItemIndex
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        if (dataStore.get(AutoSkipNextOnErrorKey, false) &&
            isInternetAvailable(this) &&
            player.hasNextMediaItem()
        ) {
            player.seekToNext()
            player.prepare()
            player.playWhenReady = true
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource.Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        DefaultDataSource.Factory(
                            this,
                            OkHttpDataSource.Factory(
                                OkHttpClient.Builder()
                                    .proxy(YouTube.proxy)
                                    .build()
                            )
                        )
                    )
            )
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    private suspend fun validateStreamUrl(url: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.connect()
                val responseCode = connection.responseCode
                val contentLength = connection.contentLength
                connection.disconnect()

                responseCode == 200 && contentLength > 1024
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        val songUrlCache = HashMap<String, Pair<String, Long>>()
        return ResolvingDataSource.Factory(createCacheDataSource()) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            if (mediaId.startsWith("1000")) {
                val songPath = runBlocking(Dispatchers.IO) {
                    database.song(mediaId).firstOrNull()?.song?.localPath
                }
                return@Factory dataSpec.withUri(Uri.fromFile(songPath?.let { File(it) }))
            }

            if (downloadCache.isCached(
                    mediaId,
                    dataSpec.position,
                    if (dataSpec.length >= 0) dataSpec.length else 1
                ) ||
                playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)
            ) {
                scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
                return@Factory dataSpec
            }
            songUrlCache[mediaId]?.takeIf { it.second > System.currentTimeMillis() }
                ?.let { cached ->
                    return@let runBlocking(Dispatchers.IO) {
                        if (validateStreamUrl(cached.first)) {
                            recoverSong(mediaId)
                            dataSpec.withUri(cached.first.toUri())
                        } else {
                            songUrlCache.remove(mediaId)
                            null
                        }
                    }
                }

            val playbackData = runBlocking(Dispatchers.IO) {
                YTPlayerUtils.playerResponseForPlayback(
                    mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                )
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable
                    is ConnectException, is UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                        )
                    }

                    is SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR
                    )
                }
            }
            val isValidUrl = runBlocking(Dispatchers.IO) {
                validateStreamUrl(playbackData.streamUrl)
            }

            if (!isValidUrl) {
                throw PlaybackException(
                    "Invalid stream URL",
                    null,
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                )
            }
            val format = playbackData.format

            database.query {
                upsert(
                    FormatEntity(
                        id = mediaId,
                        itag = format.itag,
                        mimeType = format.mimeType.split(";")[0],
                        codecs = format.mimeType.split("codecs=")[1].removeSurrounding("\""),
                        bitrate = format.bitrate,
                        sampleRate = format.audioSampleRate,
                        contentLength = format.contentLength!!,
                        loudnessDb = playbackData.audioConfig?.loudnessDb,
                        playbackUrl = playbackData.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                    )
                )
            }
            scope.launch(Dispatchers.IO) { recoverSong(mediaId, playbackData) }
            val streamUrl = playbackData.streamUrl

            songUrlCache[mediaId] =
                streamUrl to System.currentTimeMillis() + (playbackData.streamExpiresInSeconds * 1000L)
            dataSpec.withUri(streamUrl.toUri()).subrange(dataSpec.uriPositionOffset, CHUNK_LENGTH)
        }
    }

    private fun createRenderersFactory() =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink.Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        emptyArray(),
                        SilenceSkippingAudioProcessor(2_000_000, 0.01f, 2_000_000, 0, 256),
                        SonicAudioProcessor()
                    )
                )
                .build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats
    ) {
        val mediaItem =
            eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val minPlaybackDur = (dataStore.get(minPlaybackDurKey, 30) / 100)
        if (playbackStats.totalPlayTimeMs.toFloat() / ((mediaItem.metadata?.duration?.times(1000))
                ?: -1) >= minPlaybackDur
            && !dataStore.get(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs
                        )
                    )
                } catch (_: SQLException) {
                }
                if (dataStore.get(AddingPlayedSongsToYTMHistoryKey, true)) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val playbackUrl = database.format(mediaItem.mediaId).first()?.playbackUrl
                            ?: YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                                .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        playbackUrl?.let {
                            YouTube.registerPlayback(null, playbackUrl)
                                .onFailure {
                                    reportException(it)
                                }
                        }
                    }
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.playbackState == STATE_IDLE) {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
            return
        }
        val persistQueue = PersistQueue(
            title = queueTitle,
            items = player.mediaItems.mapNotNull { it.metadata },
            mediaItemIndex = player.currentMediaItemIndex,
            position = player.currentPosition
        )
        runCatching {
            filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(persistQueue)
                }
            }
        }.onFailure {
            reportException(it)
        }
    }

    override fun onDestroy() {
        volumeReceiver?.let { unregisterReceiver(it) }
        volumeReceiver = null
        if (dataStore.get(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        if (discordRpc?.isRpcRunning() == true) {
            discordRpc?.closeRPC()
        }
        discordRpc = null
        loudnessEnhancer?.enabled = false
        loudnessEnhancer?.release()
        loudnessEnhancer = null
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        player.release()
        bluetoothReceiver?.let {
            unregisterReceiver(it)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        private const val MAX_GAIN_MB = 800
        private const val MIN_GAIN_MB = -800
    }
}
