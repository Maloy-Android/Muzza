package com.maloy.muzza.ui.component

import android.annotation.SuppressLint
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.surfaceColorAtElevation
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.maloy.innertube.YouTube
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.YTItem
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalDownloadUtil
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.GridThumbnailHeight
import com.maloy.muzza.constants.ListItemHeight
import com.maloy.muzza.constants.ListThumbnailSize
import com.maloy.muzza.constants.SwipeSongToDismissKey
import com.maloy.muzza.constants.ThumbnailCornerRadius
import com.maloy.muzza.db.entities.Album
import com.maloy.muzza.db.entities.Artist
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.RecentActivityEntity
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.MediaMetadata
import com.maloy.muzza.playback.queues.LocalAlbumRadio
import com.maloy.muzza.ui.menu.FolderMenu
import com.maloy.muzza.utils.DirectoryTree
import com.maloy.muzza.utils.imageCache
import com.maloy.muzza.utils.joinByBullet
import com.maloy.muzza.utils.makeTimeString
import com.maloy.muzza.utils.rememberPreference
import com.maloy.muzza.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

const val ActiveBoxAlpha = 0.6f

@Composable
inline fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    noinline subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (isActive)
            modifier
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color = MaterialTheme.colorScheme.secondaryContainer)
        else
            modifier
                .height(ListItemHeight)
                .padding(horizontal = 8.dp),
    ) {
        Box(
            modifier = Modifier.padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            thumbnailContent()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    subtitle()
                }
            }
        }

        trailingContent()
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false
) = ListItem(
    title = title,
    subtitle = {
        badges()

        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.secondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) {
    Column(
        modifier = if (fillMaxWidth) {
            modifier
                .padding(12.dp)
                .fillMaxWidth()
        } else {
            modifier
                .padding(12.dp)
                .width(GridThumbnailHeight * thumbnailRatio)
        }
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier = if (fillMaxWidth) {
                Modifier.fillMaxWidth()
            } else {
                Modifier.height(GridThumbnailHeight)
            }
                .aspectRatio(thumbnailRatio)
        ) {
            thumbnailContent()
        }

        Spacer(modifier = Modifier.height(6.dp))

        title()

        Row(verticalAlignment = Alignment.CenterVertically) {
            badges()

            subtitle()
        }
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    },
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth
)

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    isSwipeable: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (!song.song.isLocal) {
            if (showLikedIcon && song.song.liked) {
                Icon.Favorite()
            }
            if (showInLibraryIcon && song.song.inLibrary != null) {
                Icon.Library()
            }
            if (showDownloadIcon) {
                val download by LocalDownloadUtil.current.getDownload(song.id)
                    .collectAsState(initial = null)
                Icon.Download(download?.state)
            }
        } else {
            if (showLikedIcon && song.song.liked) {
                Icon.Favorite()
            }
            if (showInLibraryIcon && song.song.inLibrary != null) {
                Icon.Library()
            }
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
    contentScale: ContentScale = ContentScale.Fit,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { false })
    val colorScheme = MaterialTheme.colorScheme

    val (swipeSongToDismiss) = rememberPreference(SwipeSongToDismissKey, defaultValue = true)

    if (isSwipeable && swipeSongToDismiss) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val target = dismissState.targetValue
                var swipeStartTime = 0L
                LaunchedEffect(target) {
                    when (target) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            val swipeEndTime = System.currentTimeMillis()
                            if (swipeEndTime - swipeStartTime > 1000) {
                                Toast.makeText(context, R.string.play_next, Toast.LENGTH_SHORT)
                                    .show()
                                playerConnection.playNext(listOf(song.toMediaItem()))
                            }
                        }

                        SwipeToDismissBoxValue.EndToStart -> {
                            val swipeEndTime = System.currentTimeMillis()
                            if (swipeEndTime - swipeStartTime > 1000) {
                                Toast.makeText(context, R.string.add_to_queue, Toast.LENGTH_SHORT)
                                    .show()
                                playerConnection.addToQueue(listOf(song.toMediaItem()))
                            }
                        }

                        else -> {
                            swipeStartTime = System.currentTimeMillis()
                        }
                    }
                }
                val color by
                animateColorAsState(
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                        SwipeToDismissBoxValue.StartToEnd -> colorScheme.primary
                        SwipeToDismissBoxValue.EndToStart -> colorScheme.primary
                    }, label = ""
                )
                val icon = when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> R.drawable.playlist_play
                    SwipeToDismissBoxValue.EndToStart -> R.drawable.queue_music
                    else -> null
                }
                when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> Arrangement.Start
                    SwipeToDismissBoxValue.EndToStart -> Arrangement.End
                    else -> null
                }?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(color)
                                .align(
                                    when (target) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.Center
                                    }
                                )
                                .padding(horizontal = 8.dp)
                        ) {
                            icon?.let {
                                Icon(
                                    painter = painterResource(it),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.CenterEnd),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        ) {
            ListItem(
                title = song.song.title,
                subtitle = joinByBullet(
                    song.artists.joinToString { it.name },
                    makeTimeString(song.song.duration * 1000L)
                ),
                badges = badges,
                thumbnailContent = {
                    if (song.song.isLocal) {
                        song.song.let {
                            AsyncLocalImage(
                                image = { imageCache.getLocalThumbnail(it.localPath, false) },
                                contentDescription = null,
                                contentScale = contentScale,
                                modifier = Modifier
                                    .size(ListThumbnailSize)
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            )
                            PlayingIndicatorBox(
                                isActive = isActive,
                                playWhenReady = isPlaying,
                                color = Color.White,
                                modifier = Modifier
                                    .size(ListThumbnailSize)
                                    .align(alignment = Alignment.CenterVertically)
                                    .background(
                                        color = Color.Black.copy(alpha = ActiveBoxAlpha),
                                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                                    )
                            )
                        }
                    } else {
                        ItemThumbnail(
                            thumbnailUrl = song.song.thumbnailUrl,
                            albumIndex = albumIndex,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            shape = RoundedCornerShape(ThumbnailCornerRadius),
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    }
                },
                trailingContent = trailingContent,
                modifier = modifier,
                isActive = isActive
            )
        }
    } else {
        ListItem(
            title = song.song.title,
            subtitle = joinByBullet(
                song.artists.joinToString { it.name },
                makeTimeString(song.song.duration * 1000L)
            ),
            badges = badges,
            thumbnailContent = {
                if (song.song.isLocal) {
                        song.song.let {
                            AsyncLocalImage(
                                image = { imageCache.getLocalThumbnail(it.localPath, false) },
                                contentDescription = null,
                                contentScale = contentScale,
                                modifier = Modifier
                                    .size(ListThumbnailSize)
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                            )
                            PlayingIndicatorBox(
                                isActive = isActive,
                                playWhenReady = isPlaying,
                                color = Color.White,
                                modifier = Modifier
                                    .size(ListThumbnailSize)
                                    .background(
                                        color = Color.Black.copy(alpha = ActiveBoxAlpha),
                                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                                    )
                            )
                        }
                } else {
                    ItemThumbnail(
                        thumbnailUrl = song.song.thumbnailUrl,
                        albumIndex = albumIndex,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        shape = RoundedCornerShape(ThumbnailCornerRadius),
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                }
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive
        )
    }
}

@Composable
fun SongFolderItem(
    folderTitle: String,
    modifier: Modifier = Modifier,

    ) = ListItem(title = folderTitle, thumbnailContent = {
    Icon(
        Icons.Rounded.FolderCopy,
        contentDescription = null,
        modifier = modifier.size(48.dp)
    )
},
    modifier = modifier
)

@Composable
fun SongFolderItem(
    folderTitle: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
) = ListItem(title = folderTitle,
    subtitle = subtitle,
    thumbnailContent = {
        Icon(
            Icons.Rounded.FolderCopy,
            contentDescription = null,
            modifier = modifier.size(48.dp)
        )
    },
    modifier = modifier
)

@Composable
fun SongFolderItem(
    folder: DirectoryTree,
    modifier: Modifier = Modifier,
    folderTitle: String? = null,
    menuState: MenuState,
    navController: NavController,
    subtitle: String,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(title = folderTitle ?: folder.currentDir,
    subtitle = subtitle,
    thumbnailContent = {
        Icon(
    Icons.Rounded.FolderCopy,
    contentDescription = null,
    modifier = modifier.size(48.dp)
)
},
    trailingContent = {
        androidx.compose.material3.IconButton(
            onClick = {
                menuState.show {
                    FolderMenu(
                        folder = folder,
                        navController = navController,
                        onDismiss = menuState::dismiss
                    )
                }
            }
        ) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = null
            )
        }
    },
    modifier = modifier
)

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (!song.song.isLocal) {
            if (showLikedIcon && song.song.liked) {
                Icon.Favorite()
            }
            if (showInLibraryIcon && song.song.inLibrary != null) {
                Icon.Library()
            }
            if (showDownloadIcon) {
                val download by LocalDownloadUtil.current.getDownload(song.id)
                    .collectAsState(initial = null)
                Icon.Download(download?.state)
            }
        } else {
            if (showLikedIcon && song.song.liked) {
                Icon.Favorite()
            }
            if (showInLibraryIcon && song.song.inLibrary != null) {
                Icon.Library()
            }
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
) = GridItem(
    title = song.song.title,
    subtitle = joinByBullet(
        song.artists.joinToString { it.name },
        makeTimeString(song.song.duration * 1000L)
    ),
    badges = badges,
    thumbnailContent = {
        if (song.song.isLocal) {
            song.song.let {
                AsyncLocalImage(
                    image = { imageCache.getLocalThumbnail(it.localPath, false) },
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier
                        .size(GridThumbnailHeight)
                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                )
                PlayingIndicatorBox(
                    isActive = isActive,
                    playWhenReady = isPlaying,
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = Color.Black.copy(alpha = ActiveBoxAlpha),
                            shape = RoundedCornerShape(ThumbnailCornerRadius)
                        )
                )
            }
        } else {
            ItemThumbnail(
                thumbnailUrl = song.song.thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier
                    .size(GridThumbnailHeight)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
            )
        }
        SongPlayButton(
            visible = !isActive
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(ListThumbnailSize)
                .clip(CircleShape)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        AsyncImage(
            model = artist.artist.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableIntStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState = when {
                    songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                    songs.all { downloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING, STATE_COMPLETED) } -> STATE_DOWNLOADING
                    else -> Download.STATE_STOPPED
                }
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }

        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle = joinByBullet(
        album.artists.joinToString { it.name },
        pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        album.album.year?.toString()
    ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
            modifier = Modifier.size(ListThumbnailSize)
        )
    },
    trailingContent = trailingContent,
    modifier = modifier
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableIntStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState = when {
                    songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED
                    songs.all { downloads[it.id]?.state in listOf(STATE_QUEUED, STATE_DOWNLOADING, STATE_COMPLETED) } -> STATE_DOWNLOADING
                    else -> Download.STATE_STOPPED
                }
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }

        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = album.album.title,
    subtitle = album.artists.joinToString { it.name },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = RoundedCornerShape(ThumbnailCornerRadius),
        )

        AlbumPlayButton(
            visible = !isActive,
            onClick = {
                coroutineScope.launch {
                    database.albumWithSongs(album.id).first()?.let { albumWithSongs ->
                        playerConnection.playQueue(
                            LocalAlbumRadio(albumWithSongs)
                        )
                    }
                }
            }
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    thumbnail: ImageVector,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (RowScope.() -> Unit) = {},
    showLikedIcon: Boolean = true
) {
    val context = LocalContext.current
    var customThumbnailUri by remember { mutableStateOf<Uri?>(null) }

    fun loadSavedImage(): Uri? {
        val file = File(context.filesDir, "playlist_covers/cover_${playlist.playlist.id}.jpg")
        return if (file.exists()) Uri.fromFile(file) else null
    }

    LaunchedEffect(playlist) {
        customThumbnailUri = loadSavedImage()
    }

    ListItem(
        title = playlist.playlist.name,
        subtitle = pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
        thumbnailContent = {
            Box(
                modifier = Modifier
                    .size(ListThumbnailSize)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                if (customThumbnailUri != null) {
                    AsyncImage(
                        model = customThumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PlaylistThumbnail(
                        thumbnails = playlist.thumbnails,
                        size = ListThumbnailSize,
                        placeHolder = {
                            Icon(
                                imageVector = thumbnail,
                                contentDescription = null,
                                tint = LocalContentColor.current.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(ListThumbnailSize / 2)
                                    .align(Alignment.Center)
                            )
                        },
                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                    )
                }
            }
        },
        badges = {
            if (playlist.playlist.isLocal) {
                Icon.PlaylistLocal()
            }
            if (showLikedIcon && playlist.playlist.bookmarkedAt != null && !playlist.playlist.isLocal) {
                Icon.Favorite()
            }
        },
        trailingContent = trailingContent,
        modifier = modifier
    )
}
@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    thumbnail: ImageVector,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = false,
    showLikedIcon: Boolean = true,
    badges: @Composable (RowScope.() -> Unit) = {
        if (playlist.playlist.isLocal) {
            Icon.PlaylistLocal()
        }
        if (showLikedIcon && playlist.playlist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    }
) {
    val context = LocalContext.current
    var customThumbnailUri by remember { mutableStateOf<Uri?>(null) }

    fun loadSavedImage(): Uri? {
        val file = File(context.filesDir, "playlist_covers/cover_${playlist.playlist.id}.jpg")
        return if (file.exists()) Uri.fromFile(file) else null
    }

    LaunchedEffect(playlist) {
        customThumbnailUri = loadSavedImage()
    }

    GridItem(
        title = playlist.playlist.name,
        subtitle = pluralStringResource(R.plurals.n_song, playlist.songCount, playlist.songCount),
        badges = badges,
        thumbnailContent = {
            val width = maxWidth
            Box(
                modifier = Modifier
                    .size(width)
                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
            ) {
                if (customThumbnailUri != null) {
                    AsyncImage(
                        model = customThumbnailUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    PlaylistThumbnail(
                        thumbnails = playlist.thumbnails,
                        size = width,
                        placeHolder = {
                            Icon(
                                imageVector = thumbnail,
                                contentDescription = null,
                                tint = LocalContentColor.current.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(width / 2)
                                    .align(Alignment.Center)
                            )
                        },
                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                    )
                }
            }
        },
        fillMaxWidth = fillMaxWidth,
        modifier = modifier
    )
}

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(mediaMetadata.id).collectAsState(initial = null)

        if (song?.song?.liked == true) {
            Icon.Favorite()
        }
        if (song?.song?.inLibrary != null) {
            Icon.Library()
        }
        val download by LocalDownloadUtil.current.getDownload(song?.id)
            .collectAsState(initial = null)
        Icon.Download(download?.state)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
    contentScale: ContentScale = ContentScale.Fit,
    ) = ListItem(
    title = mediaMetadata.title,
    subtitle = joinByBullet(
        mediaMetadata.artists.joinToString { it.name },
        makeTimeString(mediaMetadata.duration * 1000L)
    ),
    badges = badges,
    thumbnailContent = {
        Box {
            if (mediaMetadata.isLocal) {
                mediaMetadata.let {
                    AsyncLocalImage(
                        image = { imageCache.getLocalThumbnail(it.localPath, false) },
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .clip(RoundedCornerShape(ThumbnailCornerRadius))
                    )
                    PlayingIndicatorBox(
                        isActive = isActive,
                        playWhenReady = isPlaying,
                        color = Color.White,
                        modifier = Modifier
                            .size(ListThumbnailSize)
                            .align(alignment = Alignment.Center)
                            .background(
                                color = Color.Black.copy(alpha = ActiveBoxAlpha),
                                shape = RoundedCornerShape(ThumbnailCornerRadius)
                            )
                    )
                }
            } else {
                ItemThumbnail(
                    thumbnailUrl = mediaMetadata.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(ThumbnailCornerRadius),
                    modifier = Modifier.size(ListThumbnailSize)
                )
            }
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive
)

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun YouTubeCardItem(
    item: RecentActivityEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(60.dp)
            .width((screenWidthDp.dp - 12.dp) / 2)
            .padding(6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(ListThumbnailSize)
            ) {
                val thumbnailShape = RoundedCornerShape(ThumbnailCornerRadius)
                AsyncImage(
                    model = item.thumbnail,
                    contentScale = ContentScale.Crop,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .clip(thumbnailShape)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    isSwipeable: Boolean = true,
    albumIndex: Int? = null,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsState(initial = null)
        val album by database.album(item.id).collectAsState(initial = null)
        val playlist by database.playlist(item.id).collectAsState(initial = null)

        if (item.explicit) {
            Icon.Explicit()
        }
        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null ||
            item is PlaylistItem && showLikedIcon && playlist?.playlist?.bookmarkedAt != null
        ) {
            Icon.Favorite()
        }
        if (item is SongItem && song?.song?.inLibrary != null) {
            Icon.Library()
        }
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsState()
            Icon.Download(downloads[item.id]?.state)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val (swipeSongToDismiss) = rememberPreference(SwipeSongToDismissKey, defaultValue = true)
    if (item is SongItem && isSwipeable && swipeSongToDismiss) {
        val context = LocalContext.current
        val playerConnection = LocalPlayerConnection.current ?: return
        val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { false })
        val colorScheme = MaterialTheme.colorScheme

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val target = dismissState.targetValue
                var swipeStartTime = 0L
                LaunchedEffect(target) {
                    when (target) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            val swipeEndTime = System.currentTimeMillis()
                            if (swipeEndTime - swipeStartTime > 1000) {
                                Toast.makeText(context, R.string.play_next, Toast.LENGTH_SHORT)
                                    .show()
                                playerConnection.playNext(listOf(item.toMediaItem()))
                            }
                        }

                        SwipeToDismissBoxValue.EndToStart -> {
                            val swipeEndTime = System.currentTimeMillis()
                            if (swipeEndTime - swipeStartTime > 1000) {
                                Toast.makeText(context, R.string.add_to_queue, Toast.LENGTH_SHORT)
                                    .show()
                                playerConnection.addToQueue(listOf(item.toMediaItem()))
                            }
                        }

                        else -> {
                            swipeStartTime =
                                System.currentTimeMillis()
                        }
                    }
                }
                val color by
                animateColorAsState(
                    when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.Settled -> Color.Transparent
                        SwipeToDismissBoxValue.StartToEnd -> colorScheme.primary
                        SwipeToDismissBoxValue.EndToStart -> colorScheme.primary
                    }, label = ""
                )
                val icon = when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> R.drawable.playlist_play
                    SwipeToDismissBoxValue.EndToStart -> R.drawable.queue_music
                    else -> null
                }
                when (target) {
                    SwipeToDismissBoxValue.StartToEnd -> Arrangement.Start
                    SwipeToDismissBoxValue.EndToStart -> Arrangement.End
                    else -> null
                }?.let {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .background(color)
                                .align(
                                    when (target) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.Center
                                    }
                                )
                                .padding(horizontal = 8.dp)
                        ) {
                            icon?.let {
                                Icon(
                                    painter = painterResource(it),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .align(Alignment.CenterEnd),
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        ) {
            BaseListItemContent(
                item = item,
                modifier = modifier,
                albumIndex = albumIndex,
                badges = badges,
                isActive = isActive,
                isPlaying = isPlaying,
                trailingContent = trailingContent
            )
        }
    } else {
        BaseListItemContent(
            item = item,
            modifier = modifier,
            albumIndex = albumIndex,
            badges = badges,
            isActive = isActive,
            isPlaying = isPlaying,
            trailingContent = trailingContent
        )
    }
}

@Composable
private fun BaseListItemContent(
    item: YTItem,
    modifier: Modifier,
    albumIndex: Int?,
    badges: @Composable RowScope.() -> Unit,
    isActive: Boolean,
    isPlaying: Boolean,
    trailingContent: @Composable RowScope.() -> Unit
) {
    ListItem(
        title = item.title,
        subtitle = when (item) {
            is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
            is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
            is ArtistItem -> null
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        },
        badges = badges,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                albumIndex = albumIndex,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(ThumbnailCornerRadius),
                modifier = Modifier.size(ListThumbnailSize)
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive
    )
}


@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    test: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsState(initial = null)
        val album by database.album(item.id).collectAsState(initial = null)
        val playlist by database.playlist(item.id).collectAsState(initial = null)

        if (item.explicit) {
            Icon.Explicit()
        }
        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null ||
            item is PlaylistItem && playlist?.playlist?.bookmarkedAt != null && playlist?.playlist?.isLocal != true
        ) {
            Icon.Favorite()
        }
        if (item is SongItem && song?.song?.inLibrary != null) {
            Icon.Library()
        }
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsState()
            Icon.Download(downloads[item.id]?.state)
        }
    },
    thumbnailRatio: Float = if (item is SongItem) 16f / 9 else 1f,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (item is ArtistItem) TextAlign.Center else TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    },
    subtitle = {
        val subtitle = when (item) {
            is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
            is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
            is ArtistItem -> null
            is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        }

        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    badges = badges,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem

        if (test) {
            ItemThumbnail(
                test = true,
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(
                    ThumbnailCornerRadius
                ),
            )
        }  else {
            ItemThumbnail(
                test = false,
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = if (item is ArtistItem) CircleShape else RoundedCornerShape(
                    ThumbnailCornerRadius
                ),
            )
        }

        AlbumPlayButton(
            visible = item is AlbumItem && !isActive,
            onClick = {
                coroutineScope?.launch(Dispatchers.IO) {
                    var albumWithSongs = database.albumWithSongs(item.id).first()
                    if (albumWithSongs?.songs.isNullOrEmpty()) {
                        YouTube.album(item.id).onSuccess { albumPage ->
                            database.transaction {
                                insert(albumPage)
                            }
                            albumWithSongs = database.albumWithSongs(item.id).first()
                        }.onFailure {
                            reportException(it)
                        }
                    }
                    albumWithSongs?.let {
                        withContext(Dispatchers.Main) {
                            playerConnection.playQueue(
                                LocalAlbumRadio(it)
                            )
                        }
                    }
                }
            }
        )
        SongPlayButton(
            visible = item is SongItem && !isActive,
        )
    },
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: Any?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    test: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut()
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        } else if (test) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .aspectRatio(1f)
            )
        } else {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
            )
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color = if (albumIndex != null) MaterialTheme.colorScheme.onBackground else Color.White,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (albumIndex != null) Color.Transparent else Color.Black.copy(
                        alpha = 0.4f
                    ),
                    shape = shape
                )
        )
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
) {
    when (thumbnails.size) {
        0 -> placeHolder()

        1 -> if (thumbnails[0].startsWith("/storage")) {
            AsyncImage(
                model = thumbnails[0],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )
        } else {
            AsyncImage(
                model = thumbnails[0],
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )
        }

        else -> Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
            ) {
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd
                ).fastForEachIndexed { index, alignment ->
                    if (thumbnails.getOrNull(index)?.startsWith("/storage") == true) {
                        AsyncLocalImage(
                            image = { imageCache.getLocalThumbnail(thumbnails[index], true) },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(size / 2)
                        )
                    } else {
                        AsyncImage(
                            model = thumbnails.getOrNull(index),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(alignment)
                                .size(size / 2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                .clickable(onClick = onClick)
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
fun BoxScope.SongPlayButton(
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.Center)
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = ActiveBoxAlpha))
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(45.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = ActiveBoxAlpha))
                    .align(Alignment.Center)
            )
        }
    }
}

private object Icon {
    @Composable
    fun Favorite() {
        Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun Library() {
        Icon(
            painter = painterResource(R.drawable.library_add_check),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun Download(state: Int?) {
        when (state) {
            STATE_COMPLETED -> Icon(
                painter = painterResource(R.drawable.offline),
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .padding(end = 2.dp)
            )

            STATE_QUEUED, STATE_DOWNLOADING -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier
                    .size(16.dp)
                    .padding(end = 2.dp)
            )

            else -> {}
        }
    }

    @Composable
    fun Explicit() {
        Icon(
            painter = painterResource(R.drawable.explicit),
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }

    @Composable
    fun PlaylistLocal() {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .padding(end = 2.dp)
        )
    }
}