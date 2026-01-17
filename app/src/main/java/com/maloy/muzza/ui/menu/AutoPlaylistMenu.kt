package com.maloy.muzza.ui.menu

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import com.maloy.innertube.YouTube
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.YtmSyncKey
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.extensions.toMediaItemWithPlaylist
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.AutoPlaylistListItem
import com.maloy.muzza.ui.component.DefaultDialog
import com.maloy.muzza.ui.component.DownloadListMenu
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.ListMenuItem
import com.maloy.muzza.utils.SyncUtils
import com.maloy.muzza.utils.isInternetAvailable
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.utils.scanLocal
import com.maloy.muzza.utils.syncDB
import com.maloy.muzza.viewmodels.CachePlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.collections.contains

@Composable
fun AutoPlaylistMenu(
    playlist: Playlist,
    navController: NavController,
    coroutineScope: CoroutineScope,
    songs: List<Song>?,
    onDismiss: () -> Unit,
    thumbnail: String?,
    showSyncLikedSongsButton: Boolean = false,
    showSyncLocalSongsButton: Boolean = false,
    showRemoveFromCacheButton: Boolean = false,
    showM3UBackupButton: Boolean = true,
    syncUtils: SyncUtils?,
    iconThumbnail: ImageVector
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songs = songs ?: return
    val cacheViewModel = viewModel<CachePlaylistViewModel>()

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = ""
    )

    val mediaPermissionLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            "SAPISID" in parseCookieString(innerTubeCookie)
        }

    LaunchedEffect(songs) {
        if (songs.isEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it.id]?.state == Download.STATE_COMPLETED })
                    Download.STATE_COMPLETED
                else if (songs.all {
                        downloads[it.id]?.state == Download.STATE_QUEUED
                                || downloads[it.id]?.state == Download.STATE_DOWNLOADING
                                || downloads[it.id]?.state == Download.STATE_COMPLETED
                    })
                    Download.STATE_DOWNLOADING
                else
                    Download.STATE_STOPPED
        }
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = { playlist ->
            coroutineScope.launch(Dispatchers.IO) {
                playlist.playlist.browseId?.let { playlistId ->
                    playlistId.let { addPlaylistId ->
                        YouTube.addPlaylistToPlaylist(playlistId, addPlaylistId)
                    }
                }
            }
            songs.map { it.id }
        },
        onDismiss = {
            showChoosePlaylistDialog = false
        }
    )

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            icon = { Icon(Icons.Rounded.CloudOff, null) },
            content = {
                Text(
                    text = stringResource(
                        R.string.remove_download_playlist_confirm,
                        playlist.playlist.name
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        songs.forEach { song ->
                            DownloadService.sendRemoveDownload(
                                context,
                                ExoDownloadService::class.java,
                                song.song.id,
                                false
                            )
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

    val playlistName = playlist.title
    val m3uLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri: Uri? ->
        uri?.let {
            CoroutineScope(Dispatchers.IO).launch {
                var result = "#EXTM3U\n"
                songs.forEach { s ->
                    val se = s.song
                    result += "#EXTINF:${se.duration},${s.artists.joinToString(";") { it.name }} - ${s.title}\n"
                    result += if (se.isLocal) se.localPath else "https://music.youtube.com/watch?v=${se.id}\n"
                }
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(result.toByteArray(Charsets.UTF_8))
                }
            }
        }
    }
    AutoPlaylistListItem(
        playlist = playlist,
        thumbnail = thumbnail,
        iconThumbnail = iconThumbnail
    )
    HorizontalDivider()
    if (songs.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onDismiss()
                        playerConnection.playQueue(
                            ListQueue(
                                title = playlist.playlist.name,
                                items = songs.map { it.toMediaItemWithPlaylist(playlist.id) })
                        )
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.play),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
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
                        onDismiss()
                        playerConnection.playQueue(
                            ListQueue(
                                title = playlist.playlist.name,
                                items = songs.shuffled()
                                    .map { it.toMediaItemWithPlaylist(playlist.id) })
                        )
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.shuffle),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
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
                        onDismiss()
                        playerConnection.addToQueue(songs.map { it.toMediaItemWithPlaylist(playlist.id) })
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.add_to_queue),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        }
    }
    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        if (songs.isNotEmpty()) {
            ListMenuItem(
                icon = R.drawable.playlist_play,
                title = R.string.play_next
            ) {
                onDismiss()
                playerConnection.playNext(songs.map { it.toMediaItemWithPlaylist(playlist.id) })
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
        }
        if (!showSyncLocalSongsButton && songs.isNotEmpty()) {
            item {
                HorizontalDivider()
            }
            DownloadListMenu(
                state = downloadState,
                onDownload = {
                    songs.forEach { song ->
                        val downloadRequest = DownloadRequest.Builder(song.id, song.id.toUri())
                            .setCustomCacheKey(song.id)
                            .setData(song.song.title.toByteArray())
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
                    showRemoveDownloadDialog = true
                }
            )
        } else if (songs.isNotEmpty()) {
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation)
                    )
                },
                title = R.string.sync
            ) {
                refetchIconDegree -= 360
                if (context.checkSelfPermission(mediaPermissionLevel) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(
                        context as Activity,
                        arrayOf(mediaPermissionLevel),
                        PackageManager.PERMISSION_GRANTED
                    )
                }
                coroutineScope.launch(Dispatchers.IO) {
                    val directoryStructure = scanLocal(context).value
                    syncDB(
                        database,
                        directoryStructure.toList(),
                    )
                }
            }
        }
        if (showSyncLikedSongsButton && isLoggedIn && ytmSync && isInternetAvailable(context)) {
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation)
                    )
                },
                title = R.string.sync
            ) {
                refetchIconDegree -= 360
                coroutineScope.launch(Dispatchers.IO) {
                    syncUtils?.syncLikedSongs()
                }
            }
        }
        if (showRemoveFromCacheButton && songs.isNotEmpty()) {
            ListMenuItem(
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation)
                    )
                },
                title = R.string.remove_from_cache
            ) {
                refetchIconDegree -= 360
                songs.forEach { songs ->
                    onDismiss()
                    cacheViewModel.removeSongFromCache(songs.id)
                }
            }
            item {
                HorizontalDivider()
            }
        }
        if (showM3UBackupButton && songs.isNotEmpty()) {
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = R.drawable.backup,
                title = R.string.playlist_m3u_export
            ) {
                m3uLauncher.launch("$playlistName.m3u")
            }
        }
    }
}
