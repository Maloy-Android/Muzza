package com.dreamify.app.ui.menu

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.dreamify.app.LocalDatabase
import com.dreamify.app.LocalDownloadUtil
import com.dreamify.app.LocalPlayerConnection
import com.dreamify.app.R
import com.dreamify.app.constants.LikedAutoDownloadKey
import com.dreamify.app.constants.LikedAutodownloadMode
import com.dreamify.app.db.entities.Song
import com.dreamify.app.extensions.toMediaItem
import com.dreamify.app.playback.ExoDownloadService
import com.dreamify.app.playback.queues.ListQueue
import com.dreamify.app.ui.component.DownloadListMenu
import com.dreamify.app.ui.component.ListMenu
import com.dreamify.app.ui.component.ListMenuItem
import com.dreamify.app.utils.rememberEnumPreference
import com.dreamify.app.viewmodels.CachePlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun SongSelectionMenu(
    navController: NavController,
    selection: List<Song>,
    onDismiss: () -> Unit,
    onExitSelectionMode: () -> Unit,
    onRemoveFromQueue: (() -> Unit)? = null,
    onRemoveFromHistory: (() -> Unit)? = null,
    isFromCache: Boolean = false,
    showDownloadButton: Boolean = true
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val allInLibrary by remember(selection) {
        mutableStateOf(selection.isNotEmpty() && selection.all { it.song.inLibrary != null })
    }
    val allLiked by remember(selection) {
        mutableStateOf(selection.isNotEmpty() && selection.all { it.song.liked })
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    val (likedAutoDownload) = rememberEnumPreference(LikedAutoDownloadKey, LikedAutodownloadMode.OFF)
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isWifiConnected = remember {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
    }

    val cacheViewModel = viewModel<CachePlaylistViewModel>()

    LaunchedEffect(selection) {
        if (selection.isEmpty()) {
            onDismiss()
        } else {
            downloadUtil.downloads.collect { downloads ->
                downloadState = when {
                    selection.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                    selection.all { downloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING, STATE_COMPLETED) } -> STATE_DOWNLOADING
                    else -> Download.STATE_STOPPED
                }
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
            selection.map {
                runBlocking {
                    withContext(Dispatchers.IO) {
                        it.song.id
                    }
                }
            }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    ListMenu(
        contentPadding =
        PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        ListMenuItem(
            icon = R.drawable.play,
            title = R.string.play,
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    items = selection.map { it.toMediaItem() },
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
                    items = selection.shuffled().map { it.toMediaItem() },
                ),
            )
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
            playerConnection.addToQueue(selection.map { it.toMediaItem() })
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.playlist_add,
            title = R.string.add_to_playlist,
        ) {
            showChoosePlaylistDialog = true
        }
        item {
            HorizontalDivider()
        }
        if (showDownloadButton) {
            DownloadListMenu(
                state = downloadState,
                onDownload = {
                    selection.forEach { song ->
                        val downloadRequest =
                            DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.song.title.toByteArray())
                                .build()
                        DownloadService.sendAddDownload(
                            context,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                    }
                },
                onRemoveDownload = {
                    selection.forEach { song ->
                        DownloadService.sendRemoveDownload(
                            context,
                            ExoDownloadService::class.java,
                            song.song.id,
                            false
                        )
                    }
                },
            )
            item {
                HorizontalDivider()
            }
        }
        if (isFromCache) {
            ListMenuItem(
                icon = R.drawable.cached,
                title = R.string.remove_from_cache
            ) {
                selection.forEach { song ->
                    onDismiss()
                    cacheViewModel.removeSongFromCache(song.id)
                }
            }
            item {
                HorizontalDivider()
            }
        }

        if (allInLibrary) {
            ListMenuItem(
                icon = R.drawable.library_add_check,
                title = R.string.remove_from_library,
            ) {
                database.query {
                    selection.forEach { song ->
                        inLibrary(song.id, null)
                    }
                }
            }
        } else {
            ListMenuItem(
                icon = R.drawable.library_add,
                title = R.string.add_to_library,
            ) {
                database.transaction {
                    selection.forEach { song ->
                        inLibrary(song.id, LocalDateTime.now())
                    }
                }
            }
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = if (allLiked) R.drawable.favorite else R.drawable.favorite_border,
            tint = { if (allLiked) MaterialTheme.colorScheme.error else LocalContentColor.current },
            title = if (allLiked) R.string.action_remove_like_all else R.string.action_like_all,
        ) {
            database.query {
                if (allLiked) {
                    selection.forEach { song ->
                        update(song.song.copy(liked = false))
                    }
                } else {
                    selection.forEach { likedSong ->
                        update(likedSong.song.toggleLike())
                        update(likedSong.song.localToggleLike())
                        if (likedAutoDownload == LikedAutodownloadMode.ON && !likedSong.song.liked && likedSong.song.dateDownload == null || likedAutoDownload == LikedAutodownloadMode.WIFI_ONLY && !likedSong.song.liked && likedSong.song.dateDownload == null && isWifiConnected) {
                            val downloadRequest = DownloadRequest
                                .Builder(likedSong.id, likedSong.id.toUri())
                                .setCustomCacheKey(likedSong.id)
                                .setData(likedSong.title.toByteArray())
                                .build()
                            DownloadService.sendAddDownload(
                                context,
                                ExoDownloadService::class.java,
                                downloadRequest,
                                false
                            )
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
            }
        }
        if (onRemoveFromQueue != null) {
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = R.drawable.playlist_remove,
                title = R.string.remove_from_playlist,
            ) {
                onDismiss()
                onRemoveFromQueue()
                onExitSelectionMode()
            }
        }
        if (onRemoveFromHistory != null) {
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = R.drawable.delete,
                title = R.string.remove_from_history,
            ) {
                onDismiss()
                onRemoveFromHistory()
                onExitSelectionMode()
            }
        }
    }
}
