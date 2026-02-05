@file:Suppress("NAME_SHADOWING")

package com.maloy.muzza.ui.menu


import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.maloy.innertube.YouTube
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalListenTogetherManager
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.constants.SliderStyle
import com.maloy.muzza.constants.SliderStyleKey
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.ui.component.BottomSheetState
import com.maloy.muzza.ui.component.DownloadListMenu
import com.maloy.muzza.ui.component.ListMenuItem
import com.maloy.muzza.ui.component.ListDialog
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.PlayerSliderTrack
import com.maloy.muzza.ui.component.SleepTimerListMenu
import com.maloy.muzza.utils.rememberEnumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.saket.squiggles.SquigglySlider
import java.time.LocalDateTime
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenu(
    mediaMetadata: MediaMetadata?,
    navController: NavController,
    bottomSheetState: BottomSheetState,
    onShowDetailsDialog: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    mediaMetadata ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val playerVolume = playerConnection.service.playerVolume.collectAsState()
    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    val librarySong by database.song(mediaMetadata.id).collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()
    val download by LocalDownloadUtil.current.getDownload(mediaMetadata.id)
        .collectAsState(initial = null)

    val artists = remember(mediaMetadata.artists) {
        mediaMetadata.artists.filter { it.id != null }
    }
    val listenTogetherManager = LocalListenTogetherManager.current

    val sleepTimerEnabled = remember(
        playerConnection.service.sleepTimer.triggerTime,
        playerConnection.service.sleepTimer.pauseWhenSongEnd
    ) {
        playerConnection.service.sleepTimer.isActive
    }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft = if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                    playerConnection.player.duration - playerConnection.player.currentPosition
                } else {
                    playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                }
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)

    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge
                    )

                    when (sliderStyle) {
                        SliderStyle.DEFAULT -> {
                            Slider(
                                value = sleepTimerValue,
                                onValueChange = { sleepTimerValue = it },
                                valueRange = 5f..120f,
                                steps = (120 - 5) / 5 - 1,
                                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                track = { sliderState ->
                                    PlayerSliderTrack(
                                        sliderState = sliderState,
                                        colors = SliderDefaults.colors()
                                    )
                                },
                            )
                        }

                        SliderStyle.SQUIGGLY -> {
                            SquigglySlider(
                                value = sleepTimerValue,
                                onValueChange = { sleepTimerValue = it },
                                valueRange = 5f..120f,
                            )
                        }

                        SliderStyle.COMPOSE -> {
                            Slider(
                                value = sleepTimerValue,
                                onValueChange = { sleepTimerValue = it },
                                valueRange = 5f..120f,
                                steps = (120 - 5) / 5 - 1,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        }
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            }
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            database.transaction {
                insert(mediaMetadata)
            }

            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { YouTube.addToPlaylist(it, mediaMetadata.id) }
            }

            listOf(mediaMetadata.id)
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false }
        ) {
            items(artists) { artist ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            bottomSheetState.collapseSoft()
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    var showTempoPitchDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showTempoPitchDialog) {
        TempoPitchDialog(
            onDismiss = { showTempoPitchDialog = false }
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp, bottom = 6.dp)
    ) {
        Icon(
            imageVector = if (playerVolume.value == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        when (sliderStyle) {
            SliderStyle.DEFAULT -> {
                Slider(
                    value = playerVolume.value,
                    onValueChange = { playerConnection.service.playerVolume.value = it },
                    valueRange = 0f..1f,
                    thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                    track = { sliderState ->
                        PlayerSliderTrack(
                            sliderState = sliderState,
                            colors = SliderDefaults.colors()
                        )
                    }
                )
            }
            SliderStyle.SQUIGGLY -> {
                SquigglySlider(
                    value = playerVolume.value,
                    onValueChange = { playerConnection.service.playerVolume.value = it },
                    valueRange = 0f..1f,
                )
            }
            SliderStyle.COMPOSE -> {
                Slider(
                    value = playerVolume.value,
                    onValueChange = { playerConnection.service.playerVolume.value = it },
                    valueRange = 0f..1f,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    HorizontalDivider()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!mediaMetadata.isLocal) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        playerConnection.service.startRadioSeamlessly()
                        onDismiss()
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.radio),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.start_radio),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        } else {
            if (librarySong?.song?.inLibrary != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            database.query {
                                inLibrary(mediaMetadata.id, null)
                            }
                        }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.library_add_check),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.remove_from_library),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .basicMarquee()
                            .padding(top = 4.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            database.transaction {
                                insert(mediaMetadata)
                                inLibrary(mediaMetadata.id, LocalDateTime.now())
                            }
                        }
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.library_add),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.add_to_library),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .basicMarquee()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    showChoosePlaylistDialog = true
                }
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.playlist_add),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = stringResource(R.string.add_to_playlist),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .basicMarquee()
                    .padding(top = 4.dp),
            )
        }
        if (!mediaMetadata.isLocal) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                        onDismiss()
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onShowDetailsDialog()
                        onDismiss()
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.details),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        if (!mediaMetadata.isLocal) {
            if (listenTogetherManager != null && listenTogetherManager.isInRoom && !listenTogetherManager.isHost) {
                ListMenuItem(
                    icon = R.drawable.queue_music,
                    title = R.string.suggest_to_host
                ) {
                    val durationMs = if (mediaMetadata.duration > 0) mediaMetadata.duration.toLong() * 1000 else 180000L
                    val trackInfo = com.maloy.muzza.listentogether.TrackInfo(
                        id = mediaMetadata.id,
                        title = mediaMetadata.title,
                        artist = mediaMetadata.artists.joinToString(", ") { it.name },
                        album = mediaMetadata.album?.id,
                        duration = durationMs,
                        thumbnail = mediaMetadata.thumbnailUrl
                    )
                    listenTogetherManager.suggestTrack(trackInfo)
                    onDismiss()
                }
                item {
                    HorizontalDivider()
                }
            }
            if (listenTogetherManager != null && listenTogetherManager.isInRoom) {
                ListMenuItem(
                    icon = R.drawable.logout,
                    title = R.string.listen_together_leave_room
                ) {
                    listenTogetherManager.leaveRoom()
                    onDismiss()
                }
                item {
                    HorizontalDivider()
                }
            }
            DownloadListMenu(
                state = download?.state,
                onDownload = {
                    database.transaction {
                        insert(mediaMetadata)
                    }
                    val downloadRequest =
                        DownloadRequest.Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                            .setCustomCacheKey(mediaMetadata.id)
                            .setData(mediaMetadata.title.toByteArray())
                            .build()
                    DownloadService.sendAddDownload(
                        context,
                        ExoDownloadService::class.java,
                        downloadRequest,
                        false
                    )
                },
                onRemoveDownload = {
                    DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        mediaMetadata.id,
                        false
                    )
                }
            )
            item {
                HorizontalDivider()
            }
        }
        if (!mediaMetadata.isLocal) {
            if (librarySong?.song?.inLibrary != null) {
                ListMenuItem(
                    icon = R.drawable.library_add_check,
                    title = R.string.remove_from_library,
                ) {
                    database.query {
                        inLibrary(mediaMetadata.id, null)
                    }
                }
                item {
                    HorizontalDivider()
                }
            } else {
                ListMenuItem(
                    icon = R.drawable.library_add,
                    title = R.string.add_to_library,
                ) {
                    database.transaction {
                        insert(mediaMetadata)
                        inLibrary(mediaMetadata.id, LocalDateTime.now())
                    }
                }
                item {
                    HorizontalDivider()
                }
            }
            if (artists.isNotEmpty()) {
                ListMenuItem(
                    icon = if (artists.size == 1) R.drawable.artist else R.drawable.artists,
                    title = R.string.view_artist
                ) {
                    if (artists.size == 1) {
                        navController.navigate("artist/${artists[0].id}")
                        bottomSheetState.collapseSoft()
                        onDismiss()
                    } else {
                        showSelectArtistDialog = true
                    }
                }
                item {
                    HorizontalDivider()
                }
            }
            if (mediaMetadata.album != null) {
                ListMenuItem(
                    icon = R.drawable.album,
                    title = R.string.view_album
                ) {
                    navController.navigate("album/${mediaMetadata.album.id}")
                    bottomSheetState.collapseSoft()
                    onDismiss()
                }
                item {
                    HorizontalDivider()
                }
            }
            ListMenuItem(
                icon = R.drawable.music_note,
                title = R.string.listen_youtube_music
            ) {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://music.youtube.com/watch?v=${mediaMetadata.id}".toUri()
                )
                context.startActivity(intent)
            }
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = R.drawable.info,
                title = R.string.details
            ) {
                onShowDetailsDialog()
                onDismiss()
            }
            item {
                HorizontalDivider()
            }
        }
        ListMenuItem(
            icon = R.drawable.equalizer,
            title = R.string.equalizer
        ) {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, playerConnection.player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            }
            ListMenuItem(
                icon = R.drawable.equalizer,
                title = R.string.equalizer
            ) {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(
                        AudioEffect.EXTRA_AUDIO_SESSION,
                        playerConnection.player.audioSessionId
                    )
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    activityResultLauncher.launch(intent)
                }
                onDismiss()
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                activityResultLauncher.launch(intent)
            }
            onDismiss()
        }
        item {
            HorizontalDivider()
        }
        SleepTimerListMenu(
            sleepTimerTimeLeft = sleepTimerTimeLeft,
            enabled = sleepTimerEnabled,
        ) {
            if (sleepTimerEnabled) playerConnection.service.sleepTimer.clear()
            else showSleepTimerDialog = true
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.speed,
            title = R.string.tempo_and_pitch
        ) {
            showTempoPitchDialog = true
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TempoPitchDialog(
    onDismiss: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    var tempo by remember {
        mutableFloatStateOf(playerConnection.player.playbackParameters.speed)
    }
    var transposeValue by remember {
        mutableIntStateOf(round(12 * log2(playerConnection.player.playbackParameters.pitch)).toInt())
    }
    val updatePlaybackParameters = {
        playerConnection.player.playbackParameters =
            PlaybackParameters(tempo, 2f.pow(transposeValue.toFloat() / 12))
    }
    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.DEFAULT)

    AlertDialog(
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.tempo_and_pitch))
        },
        dismissButton = {
            TextButton(
                onClick = {
                    tempo = 1f
                    transposeValue = 0
                    updatePlaybackParameters()
                }
            ) {
                Text(stringResource(R.string.reset))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        text = {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.speed),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    when (sliderStyle) {
                        SliderStyle.DEFAULT -> {
                            Slider(
                                value = tempo,
                                onValueChange = {
                                    tempo = it
                                    updatePlaybackParameters()
                                },
                                valueRange = 0.25f..2.0f,
                                steps = 34,
                                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                track = { sliderState ->
                                    PlayerSliderTrack(
                                        sliderState = sliderState,
                                        colors = SliderDefaults.colors()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SliderStyle.SQUIGGLY -> {
                            SquigglySlider(
                                value = tempo,
                                onValueChange = {
                                    tempo = it
                                    updatePlaybackParameters()
                                },
                                valueRange = 0.25f..2.0f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SliderStyle.COMPOSE -> {
                            Slider(
                                value = tempo,
                                onValueChange = {
                                    tempo = it
                                    updatePlaybackParameters()
                                },
                                valueRange = 0.25f..2.0f,
                                steps = 34,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "x${"%.2f".format(tempo)}",
                        modifier = Modifier.width(50.dp),
                        textAlign = TextAlign.End
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(R.drawable.discover_tune),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    when (sliderStyle) {
                        SliderStyle.DEFAULT -> {
                            Slider(
                                value = transposeValue.toFloat(),
                                onValueChange = {
                                    transposeValue = it.toInt()
                                    updatePlaybackParameters()
                                },
                                valueRange = -12f..12f,
                                steps = 24,
                                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                                track = { sliderState ->
                                    PlayerSliderTrack(
                                        sliderState = sliderState,
                                        colors = SliderDefaults.colors()
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SliderStyle.SQUIGGLY -> {
                            SquigglySlider(
                                value = transposeValue.toFloat(),
                                onValueChange = {
                                    transposeValue = it.toInt()
                                    updatePlaybackParameters()
                                },
                                valueRange = -12f..12f,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        SliderStyle.COMPOSE -> {
                            Slider(
                                value = transposeValue.toFloat(),
                                onValueChange = {
                                    transposeValue = it.toInt()
                                    updatePlaybackParameters()
                                },
                                valueRange = -12f..12f,
                                steps = 24,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "${if (transposeValue > 0) "+" else ""}$transposeValue",
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    )
}
