package com.maloy.muzza.ui.screens.artist

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.maloy.innertube.models.AlbumItem
import com.maloy.innertube.models.ArtistItem
import com.maloy.innertube.models.PlaylistItem
import com.maloy.innertube.models.SongItem
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.AppBarHeight
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.queues.YouTubeQueue
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.NavigationTitle
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.component.YouTubeGridItem
import com.maloy.muzza.ui.component.YouTubeListItem
import com.maloy.muzza.ui.component.shimmer.ButtonPlaceholder
import com.maloy.muzza.ui.component.shimmer.ListItemPlaceHolder
import com.maloy.muzza.ui.component.shimmer.ShimmerHost
import com.maloy.muzza.ui.component.shimmer.TextPlaceholder
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.menu.YouTubeAlbumMenu
import com.maloy.muzza.ui.menu.YouTubeArtistMenu
import com.maloy.muzza.ui.menu.YouTubePlaylistMenu
import com.maloy.muzza.ui.menu.YouTubeSongMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.ui.utils.fadingEdge
import com.maloy.muzza.ui.utils.resize
import com.maloy.muzza.viewmodels.ArtistViewModel
import com.valentinilk.shimmer.shimmer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val artistPage = viewModel.artistPage
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()

    val lazyListState = rememberLazyListState()

    val snackbarHostState = remember { SnackbarHostState() }

    val transparentAppBar by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex <= 1
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .add(
                    WindowInsets(
                        top = -WindowInsets.systemBars.asPaddingValues()
                            .calculateTopPadding() - AppBarHeight
                    )
                )
                .asPaddingValues(),
        ) {
            if (artistPage == null) {
                item(key = "shimmer") {
                    ShimmerHost {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.2f / 1),
                        ) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmer()
                                    .background(MaterialTheme.colorScheme.onSurface)
                                    .fadingEdge(
                                        top = WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateTopPadding() + AppBarHeight,
                                        bottom = 200.dp
                                    )
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            TextPlaceholder(
                                height = 36.dp,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .padding(bottom = 16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ButtonPlaceholder(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(40.dp)
                                )

                                Spacer(modifier = Modifier.weight(1f))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ButtonPlaceholder(
                                        modifier = Modifier
                                            .width(100.dp)
                                            .height(40.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .shimmer()
                                            .background(
                                                MaterialTheme.colorScheme.onSurface,
                                                RoundedCornerShape(24.dp)
                                            )
                                    )
                                }
                            }
                        }
                        repeat(6) {
                            ListItemPlaceHolder()
                        }
                    }
                }
            } else {
                item(key = "header") {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.2f / 1)
                        ) {
                            AsyncImage(
                                model = artistPage.artist.thumbnail.resize(1200, 1000),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                                    .fadingEdge(
                                        bottom = 200.dp
                                    )
                            )
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)
                        ) {
                            Text(
                                text = artistPage.artist.title,
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 32.sp,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        database.transaction {
                                            val artist = libraryArtist?.artist
                                            if (artist != null) {
                                                update(artist.toggleLike())
                                                update(artist.localToggleLike())
                                            } else {
                                                artistPage.artist.let {
                                                    insert(
                                                        ArtistEntity(
                                                            id = it.id,
                                                            name = it.title,
                                                            channelId = it.channelId,
                                                            thumbnailUrl = it.thumbnail,
                                                        ).toggleLike()
                                                            .localToggleLike()
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (libraryArtist?.artist?.bookmarkedAt != null)
                                            MaterialTheme.colorScheme.surface
                                        else
                                            Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.height(40.dp)
                                ) {
                                    val isSubscribed = libraryArtist?.artist?.bookmarkedAt != null
                                    Text(
                                        text = stringResource(if (isSubscribed) R.string.subscribed else R.string.subscribe),
                                        fontSize = 14.sp,
                                        color = if (!isSubscribed) MaterialTheme.colorScheme.error else LocalContentColor.current
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    artistPage.artist.radioEndpoint?.let { radioEndpoint ->
                                        OutlinedButton(
                                            onClick = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        radioEndpoint
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(50),
                                            modifier = Modifier.height(40.dp)
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.radio),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.radio),
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    artistPage.artist.shuffleEndpoint?.let { shuffleEndpoint ->
                                        IconButton(
                                            onClick = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        shuffleEndpoint
                                                    )
                                                )
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(24.dp)
                                                )
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.shuffle),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
                if (librarySongs.isNotEmpty()) {
                    item {
                        NavigationTitle(
                            title = stringResource(R.string.from_your_library),
                            onClick = {
                                navController.navigate("artist/${viewModel.artistId}/songs")
                            }
                        )
                    }

                    items(
                        items = librarySongs,
                        key = { "local_${it.id}" }
                    ) { song ->
                        SongListItem(
                            song = song,
                            showInLibraryIcon = true,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    WatchEndpoint(
                                                        videoId = song.id
                                                    ), song.toMediaMetadata()
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }

                artistPage.sections.fastForEach { section ->
                    if (section.items.isNotEmpty()) {
                        item {
                            NavigationTitle(
                                title = section.title,
                                onClick = section.moreEndpoint?.let {
                                    {
                                        navController.navigate(
                                            "artist/${viewModel.artistId}/items?browseId=${it.browseId}?params=${it.params}",
                                        )
                                    }
                                }
                            )
                        }
                    }

                    if ((section.items.firstOrNull() as? SongItem)?.album != null) {
                        items(
                            items = section.items,
                            key = { it.id }
                        ) { song ->
                            YouTubeListItem(
                                item = song as SongItem,
                                isActive = mediaMetadata?.id == song.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
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
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                                    .animateItem()
                            )
                        }
                    } else {
                        item {
                            LazyRow {
                                items(
                                    items = section.items,
                                    key = { it.id }
                                ) { item ->
                                    YouTubeGridItem(
                                        item = item,
                                        isActive = when (item) {
                                            is SongItem -> mediaMetadata?.id == item.id
                                            is AlbumItem -> mediaMetadata?.album?.id == item.id
                                            else -> false
                                        },
                                        test = when (item) {
                                            is SongItem -> false
                                            else -> true
                                        },
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier
                                            .combinedClickable(
                                                onClick = {
                                                    when (item) {
                                                        is SongItem -> playerConnection.playQueue(
                                                            YouTubeQueue.radio(item.toMediaMetadata())
                                                        )

                                                        is AlbumItem -> navController.navigate("album/${item.id}")
                                                        is ArtistItem -> navController.navigate("artist/${item.id}")
                                                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                                    }
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        when (item) {
                                                            is SongItem -> YouTubeSongMenu(
                                                                song = item,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )

                                                            is AlbumItem -> YouTubeAlbumMenu(
                                                                albumItem = item,
                                                                navController = navController,
                                                                onDismiss = menuState::dismiss
                                                            )

                                                            is ArtistItem -> YouTubeArtistMenu(
                                                                artist = item,
                                                                onDismiss = menuState::dismiss
                                                            )

                                                            is PlaylistItem -> YouTubePlaylistMenu(
                                                                navController = navController,
                                                                playlist = item,
                                                                coroutineScope = coroutineScope,
                                                                onDismiss = menuState::dismiss
                                                            )
                                                        }
                                                    }
                                                }
                                            )
                                            .animateItem()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    TopAppBar(
        title = { if (!transparentAppBar) Text(artistPage?.artist?.title.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        actions = {
            IconButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        viewModel.artistPage?.artist?.shareLink?.toUri()
                    )
                    context.startActivity(intent)
                }
            ) {
                Icon(
                    painterResource(R.drawable.music_note),
                    contentDescription = null
                )
            }

            IconButton(
                onClick = {
                    viewModel.artistPage?.artist?.shareLink?.let { link ->
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, link)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                }
            ) {
                Icon(
                    painterResource(R.drawable.share),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors = if (transparentAppBar) {
            TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        } else {
            TopAppBarDefaults.topAppBarColors()
        }
    )
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                .align(Alignment.BottomCenter)
        )
    }
}
