package com.maloy.muzza.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalListenTogetherManager
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.extensions.metadata
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.DownloadListMenu
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.ListMenuItem

@Composable
fun QueueSelectionMenu(
    navController: NavController,
    selection: List<Timeline.Window>,
    onExitSelectionMode: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }
    LaunchedEffect(selection) {
        if (selection.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState = when {
                selection.all { downloads[it.mediaItem.metadata?.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                selection.all { downloads[it.mediaItem.metadata?.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING, STATE_COMPLETED) } -> STATE_DOWNLOADING
                else -> Download.STATE_STOPPED
            }
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            database.transaction {
                selection.forEach { item ->
                    insert(item.mediaItem.metadata!!)
                }
            }
            selection.mapNotNull { it.mediaItem.metadata?.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
            onDismiss()
            onExitSelectionMode()
        }
    )

    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        if (listenTogetherManager != null && listenTogetherManager.isInRoom && !listenTogetherManager.isHost) {
            ListMenuItem(
                icon = R.drawable.queue_music,
                title = R.string.suggest_to_host
            ) {
                selection.forEach { song ->
                    val durationMs =
                        if ((song.mediaItem.metadata?.duration ?: return@ListMenuItem) > 0) song.mediaItem.metadata?.duration?.toLong()
                            ?.times(1000) ?: return@ListMenuItem else 180000L
                    val trackInfo = com.maloy.muzza.listentogether.TrackInfo(
                        id = song.mediaItem.metadata?.id ?: return@ListMenuItem,
                        title = song.mediaItem.metadata?.title ?: return@ListMenuItem,
                        artist = song.mediaItem.metadata?.artists?.joinToString(", ") { it.name } ?: return@ListMenuItem,
                        album = song.mediaItem.metadata?.album?.id,
                        duration = durationMs,
                        thumbnail = song.mediaItem.metadata?.thumbnailUrl
                    )
                    listenTogetherManager.suggestTrack(trackInfo)
                    onDismiss()
                }
            }
            item {
                HorizontalDivider()
            }
        }
        ListMenuItem(
            icon = R.drawable.play,
            title = R.string.play,
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    items = selection.map { it.mediaItem },
                ),
            )
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.shuffle,
            title = R.string.shuffle,
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    items = selection.map { it.mediaItem }.shuffled(),
                )
            )
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.playlist_play,
            title = R.string.play_next,
        ) {
            onDismiss()
            playerConnection.playNext(selection.map { it.mediaItem })
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.queue_music,
            title = R.string.add_to_queue,
        ) {
            onDismiss()
            playerConnection.addToQueue(selection.map { it.mediaItem })
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.playlist_add,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
        item {
            HorizontalDivider()
        }
        DownloadListMenu(
            state = downloadState,
            onDownload = {
                selection.forEach {
                    val mediaMetadata = it.mediaItem.metadata ?: return@forEach
                    database.query {
                        insert(mediaMetadata)
                    }
                    val downloadRequest = DownloadRequest.Builder(mediaMetadata.id, mediaMetadata.id.toUri())
                        .setCustomCacheKey(mediaMetadata.id)
                        .setData(mediaMetadata.title.toByteArray())
                        .build()
                    DownloadService.sendAddDownload(
                        context,
                        ExoDownloadService::class.java,
                        downloadRequest,
                        false
                    )
                }
            },
            onRemoveDownload = {
                selection.forEach {
                    DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        it.mediaItem.metadata!!.id,
                        false
                    )
                }
            }
        )
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.playlist_remove,
            title = R.string.remove_from_queue
        ) {
            selection.sortedBy {
                -it.firstPeriodIndex
            }.forEach { item ->
                playerConnection.player.removeMediaItem(item.firstPeriodIndex)
            }
            onDismiss()
        }
    }
}