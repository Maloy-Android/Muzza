package com.maloy.muzza.ui.menu

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.utils.completed
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.PlaylistSong
import com.maloy.muzza.db.entities.PlaylistSongMap
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.ExoDownloadService
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.DefaultDialog
import com.maloy.muzza.ui.component.DownloadListMenu
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.ListMenuItem
import com.maloy.muzza.ui.component.PlaylistListItem
import com.maloy.muzza.ui.component.TextFieldDialog
import com.maloy.muzza.ui.screens.settings.ConfirmationDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@Composable
fun PlaylistMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val dbPlaylist by database.playlist(playlist.id).collectAsState(initial = playlist)
    var songs by remember {
        mutableStateOf(emptyList<Song>())
    }

    LaunchedEffect(Unit) {
        database.playlistSongs(playlist.id).collect {
            songs = it.map(PlaylistSong::song)
        }
    }

    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
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

    var showEditDialog by remember {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = stringResource(R.string.edit_playlist)) },
            onDismiss = { showEditDialog = false },
            initialTextFieldValue = TextFieldValue(
                playlist.playlist.name,
                TextRange(playlist.playlist.name.length)
            ),
            onDone = { name ->
                onDismiss()
                database.query {
                    update(playlist.playlist.copy(name = name))
                }
                coroutineScope.launch(Dispatchers.IO) {
                    playlist.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                }
            }
        )
    }
    fun saveImageToPrivateStorage(uri: Uri): Uri? {
        return try {
            val dir = File(context.filesDir, "playlist_covers")
            if (!dir.exists()) dir.mkdirs()

            val outputFile = File(dir, "cover_${playlist.playlist.id}.jpg")

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Uri.fromFile(outputFile)
        } catch (e: Exception) {
            null
        }
    }

    fun loadSavedImage(): Uri? {
        val file = File(context.filesDir, "playlist_covers/cover_${playlist.playlist.id}.jpg")
        return if (file.exists()) Uri.fromFile(file) else null
    }
    var customThumbnailUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(playlist) {
        customThumbnailUri = loadSavedImage()
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            val savedUri = saveImageToPrivateStorage(selectedUri)
                customThumbnailUri = savedUri
        }
    }
    fun deletePlaylistCover(context: Context, playlistId: String): Boolean {
        val file = File(context.filesDir, "playlist_covers/cover_$playlistId.jpg")
        return file.exists() && file.delete()
    }
    var showClearPlaylistThumbnailDialog by remember {
        mutableStateOf(false)
    }
    if (showClearPlaylistThumbnailDialog) {
        ConfirmationDialog(
            title = R.string.remove_custom_playlist_thumbnail,
            icon = Icons.Rounded.HideImage,
            onDismiss = { showClearPlaylistThumbnailDialog = false },
            onConfirm = {
                showClearPlaylistThumbnailDialog = false
                deletePlaylistCover(context = context, playlistId = playlist.playlist.id)
            }
        )
    }

    var showRemoveDownloadDialog by remember {
        mutableStateOf(false)
    }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
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

    var showDeletePlaylistDialog by remember {
        mutableStateOf(false)
    }

    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text = stringResource(R.string.delete_playlist_confirm, playlist.playlist.name),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                    }
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        onDismiss()
                        database.query {
                            delete(playlist.playlist)
                        }

                        if (!playlist.playlist.isLocal) {
                            coroutineScope.launch(Dispatchers.IO) {
                                playlist.playlist.browseId?.let { YouTube.deletePlaylist(it) }
                            }
                        }
                    }
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            }
        )
    }

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

    PlaylistListItem(
        playlist = playlist,
        thumbnail = Icons.AutoMirrored.Rounded.QueueMusic,
        trailingContent = {
            if (!playlist.playlist.isEditable) {
                IconButton(
                    onClick = {
                        database.query {
                            dbPlaylist?.playlist?.toggleLike()?.let { update(it) }
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                        tint = if (dbPlaylist?.playlist?.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        contentDescription = null
                    )
                }
            }
        }
    )
    HorizontalDivider()
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
                            items = songs.map { it.toMediaItem() }
                        ))
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
                            items = songs.shuffled().map { it.toMediaItem() }
                        ))
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
                    playerConnection.addToQueue(songs.map { it.toMediaItem() })
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

    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        ListMenuItem(
            icon = R.drawable.edit,
            title = R.string.edit
        ) {
            showEditDialog = true
        }
        item {
            HorizontalDivider()
        }
        if (customThumbnailUri == null) {
            ListMenuItem(
                icon = R.drawable.image,
                title = R.string.edit_playlist_thumbnail
            ) {
                pickImageLauncher.launch("image/*")
            }
        } else {
            ListMenuItem(
                icon = R.drawable.remove_image,
                title = R.string.remove_custom_playlist_thumbnail
            ) {
                showClearPlaylistThumbnailDialog = true
            }
        }
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
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.delete,
            title = R.string.delete
        ) {
            showDeletePlaylistDialog = true
        }
        item {
            HorizontalDivider()
        }
        if (playlist.playlist.browseId != null) {
            ListMenuItem(
                icon = R.drawable.sync,
                title = R.string.sync
            ) {
                onDismiss()
                coroutineScope.launch(Dispatchers.IO) {
                    val playlistPage =
                        YouTube.playlist(playlist.playlist.browseId).completed().getOrNull()
                            ?: return@launch
                    database.transaction {
                        clearPlaylist(playlist.id)
                        playlistPage.songs
                            .map(SongItem::toMediaMetadata)
                            .onEach(::insert)
                            .mapIndexed { position, song ->
                                PlaylistSongMap(
                                    songId = song.id,
                                    playlistId = playlist.id,
                                    position = position
                                )
                            }
                            .forEach(::insert)
                    }
                }
            }
            item {
                HorizontalDivider()
            }
        }
        ListMenuItem(
            icon = R.drawable.backup,
            title = R.string.playlist_m3u_export
        ) {
            m3uLauncher.launch("playlist.m3u")
        }
    }
}
