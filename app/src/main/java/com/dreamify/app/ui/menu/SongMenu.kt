package com.dreamify.app.ui.menu

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.dreamify.innertube.YouTube
import com.dreamify.app.LocalDatabase
import com.dreamify.app.LocalDownloadUtil
import com.dreamify.app.LocalPlayerConnection
import com.dreamify.app.R
import com.dreamify.app.constants.LikedAutoDownloadKey
import com.dreamify.app.constants.LikedAutodownloadMode
import com.dreamify.app.constants.ListItemHeight
import com.dreamify.app.constants.ListThumbnailSize
import com.dreamify.app.db.entities.Event
import com.dreamify.app.db.entities.Playlist
import com.dreamify.app.db.entities.PlaylistSong
import com.dreamify.app.db.entities.Song
import com.dreamify.app.extensions.toMediaItem
import com.dreamify.app.models.toMediaMetadata
import com.dreamify.app.playback.ExoDownloadService
import com.dreamify.app.playback.queues.YouTubeQueue
import com.dreamify.app.ui.component.DownloadListMenu
import com.dreamify.app.ui.component.ListDialog
import com.dreamify.app.ui.component.ListMenu
import com.dreamify.app.ui.component.ListMenuItem
import com.dreamify.app.ui.component.SongListItem
import com.dreamify.app.ui.component.TextFieldDialog
import com.dreamify.app.utils.rememberEnumPreference
import com.dreamify.app.viewmodels.CachePlaylistViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SongMenu(
    originalSong: Song,
    event: Event? = null,
    navController: NavController,
    playlistSong: PlaylistSong? = null,
    playlist: Playlist? = null,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
) {
    val context = LocalContext.current
    val (likedAutoDownload) = rememberEnumPreference(LikedAutoDownloadKey, LikedAutodownloadMode.OFF)
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val isWifiConnected = remember {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
    }
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val songState = database.song(originalSong.id).collectAsState(initial = originalSong)
    val song = songState.value ?: originalSong
    val download by LocalDownloadUtil.current.getDownload(originalSong.id)
        .collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    val scope = rememberCoroutineScope()
    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val cacheViewModel = viewModel<CachePlaylistViewModel>()

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = ""
    )

    var showEditDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showEditDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.edit), contentDescription = null) },
            title = { Text(text = stringResource(R.string.edit_song)) },
            onDismiss = { showEditDialog = false },
            initialTextFieldValue = TextFieldValue(
                song.song.title,
                TextRange(song.song.title.length)
            ),
            onDone = { title ->
                onDismiss()
                database.query {
                    update(song.song.copy(title = title))
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
        onGetSong = { playlistId ->
            coroutineScope.launch(Dispatchers.IO) {
                playlistId.playlist.browseId?.let { browseId ->
                    YouTube.addToPlaylist(browseId, song.id)
                }
            }
            listOf(song.id)
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )


    var showSelectArtistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSelectArtistDialog) {
        ListDialog(
            onDismiss = { showSelectArtistDialog = false }
        ) {
            items(
                items = song.artists,
                key = { it.id }
            ) { artist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(ListItemHeight)
                        .clickable {
                            navController.navigate("artist/${artist.id}")
                            showSelectArtistDialog = false
                            onDismiss()
                        }
                        .padding(horizontal = 12.dp),
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = artist.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ListThumbnailSize)
                                .clip(CircleShape)
                        )
                    }
                    Text(
                        text = artist.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }

    SongListItem(
        song = song,
        showLikedIcon = false,
        isSwipeable = false,
        badges = {},
        trailingContent = {
            IconButton(
                onClick = {
                    database.query {
                        update(song.song.toggleLike())
                        update(song.song.localToggleLike())
                        if (likedAutoDownload == LikedAutodownloadMode.ON && !song.song.liked && song.song.dateDownload == null || likedAutoDownload == LikedAutodownloadMode.WIFI_ONLY && !song.song.liked && song.song.dateDownload == null && isWifiConnected) {
                            val downloadRequest = DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
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
            ) {
                Icon(
                    painter = painterResource(if (song.song.liked) R.drawable.favorite else R.drawable.favorite_border),
                    tint = if (song.song.liked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    contentDescription = null
                )
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
        if (song.song.isLocal) {
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
                        playerConnection.playNext(song.toMediaItem())
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.playlist_play),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.play_next),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        onDismiss()
                        playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        }

        if (playlistSong != null) {
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
                        database.transaction {
                            coroutineScope.launch {
                                playlist?.id?.let { playlistId ->
                                    if (playlistSong.map.setVideoId != null) {
                                        YouTube.removeFromPlaylist(
                                            playlistId,
                                            playlistSong.map.songId,
                                            playlistSong.map.setVideoId
                                        )
                                    }
                                }
                            }
                            move(
                                playlistSong.map.playlistId,
                                playlistSong.map.position,
                                Int.MAX_VALUE
                            )
                            delete(playlistSong.map.copy(position = Int.MAX_VALUE))
                        }
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.playlist_remove),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.remove_from_playlist),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        }
        if (!song.song.isLocal) {
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
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://music.youtube.com/watch?v=${song.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
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
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        onDismiss()
                        playerConnection.addToQueue((song.toMediaItem()))
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
        if (!song.song.isLocal) {
            ListMenuItem(
                icon = R.drawable.playlist_play,
                title = R.string.play_next
            ) {
                onDismiss()
                playerConnection.playNext(song.toMediaItem())
            }
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = R.drawable.queue_music,
                title = R.string.add_to_queue
            ) {
                onDismiss()
                playerConnection.addToQueue((song.toMediaItem()))
            }
            item {
                HorizontalDivider()
            }
        }
        if (playlistSong != null) {
            ListMenuItem(
                icon = R.drawable.playlist_add,
                title = R.string.add_to_playlist
            ) {
                showChoosePlaylistDialog = true
            }
            item {
                HorizontalDivider()
            }
        }
        if (!song.song.isLocal)  {
            ListMenuItem(
                icon = R.drawable.edit,
                title = R.string.edit
            ) {
                showEditDialog = true
            }
            item {
                HorizontalDivider()
            }
            if (isFromCache) {
                ListMenuItem(
                    icon = R.drawable.cached,
                    title = R.string.remove_from_cache
                ) {
                    onDismiss()
                    cacheViewModel.removeSongFromCache(song.id)
                }
                item {
                    HorizontalDivider()
                }
            }
            DownloadListMenu(
                state = download?.state,
                onDownload = {
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
                },
                onRemoveDownload = {
                    DownloadService.sendRemoveDownload(
                        context,
                        ExoDownloadService::class.java,
                        song.id,
                        false
                    )
                }
            )
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = if (song.artists.size == 1) R.drawable.artist else R.drawable.artists,
                title = R.string.view_artist
            ) {
                if (song.artists.size == 1) {
                    navController.navigate("artist/${song.artists[0].id}")
                    onDismiss()
                } else {
                    showSelectArtistDialog = true
                }
            }
            item {
                HorizontalDivider()
            }
            if (song.song.albumId != null) {
                ListMenuItem(
                    icon = R.drawable.album,
                    title = R.string.view_album
                ) {
                    onDismiss()
                    navController.navigate("album/${song.song.albumId}")
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
                    "https://music.youtube.com/watch?v=${song.id}".toUri()
                )
                context.startActivity(intent)
            }
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
                title = R.string.refetch
            ) {
                refetchIconDegree -= 360
                scope.launch(Dispatchers.IO) {
                    YouTube.queue(listOf(song.id)).onSuccess {
                        val newSong = it.firstOrNull()
                        if (newSong != null) {
                            database.transaction {
                                update(song, newSong.toMediaMetadata())
                            }
                        }
                    }
                }
            }
            item {
                HorizontalDivider()
            }
        }
        if (song.song.inLibrary == null) {
            ListMenuItem(
                icon = R.drawable.library_add,
                title = R.string.add_to_library
            ) {
                database.query {
                    update(song.song.toggleLibrary())
                }
            }
        } else {
            ListMenuItem(
                icon = R.drawable.library_add_check,
                title = R.string.remove_from_library
            ) {
                database.query {
                    update(song.song.toggleLibrary())
                }
            }
        }
        if (event != null) {
            item {
                HorizontalDivider()
            }
            ListMenuItem(
                icon = R.drawable.delete,
                title = R.string.remove_from_history
            ) {
                onDismiss()
                database.query {
                    delete(event)
                }
            }
        }
    }
}
