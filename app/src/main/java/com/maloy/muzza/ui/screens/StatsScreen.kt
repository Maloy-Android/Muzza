package com.maloy.muzza.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.maloy.innertube.models.WatchEndpoint
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.StatPeriod
import com.maloy.muzza.extensions.togglePlayPause
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.queues.YouTubeQueue
import com.maloy.muzza.ui.component.AlbumGridItem
import com.maloy.muzza.ui.component.ArtistGridItem
import com.maloy.muzza.ui.component.ChipsRow
import com.maloy.muzza.ui.component.EmptyPlaceholder
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.LazyColumnScrollbar
import com.maloy.muzza.ui.component.LocalMenuState
import com.maloy.muzza.ui.component.NavigationTitle
import com.maloy.muzza.ui.component.SongListItem
import com.maloy.muzza.ui.menu.AlbumMenu
import com.maloy.muzza.ui.menu.ArtistMenu
import com.maloy.muzza.ui.menu.SongMenu
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.viewmodels.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val statPeriod by viewModel.statPeriod.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()
    val mostPlayedAlbums by viewModel.mostPlayedAlbums.collectAsState()
    val lazylistState = rememberLazyListState()
    val lazyChecker by remember {
        derivedStateOf {
            lazylistState.firstVisibleItemIndex > 0
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
        state = lazylistState
    ) {
        if (mostPlayedSongs.isEmpty() && mostPlayedArtists.isEmpty() && mostPlayedAlbums.isEmpty()) {
            item {
                EmptyPlaceholder(
                    icon = R.drawable.trending_up,
                    text = stringResource(R.string.stats_empty),
                    modifier = Modifier
                        .fillParentMaxSize()
                        .animateItem()
                )
            }
        } else {
            item {
                ChipsRow(
                    chips = listOf(
                        StatPeriod.ALL to stringResource(R.string.filter_all),
                        StatPeriod.`1_WEEK` to pluralStringResource(R.plurals.n_week, 1, 1),
                        StatPeriod.`1_MONTH` to pluralStringResource(R.plurals.n_month, 1, 1),
                        StatPeriod.`3_MONTH` to pluralStringResource(R.plurals.n_month, 3, 3),
                        StatPeriod.`6_MONTH` to pluralStringResource(R.plurals.n_month, 6, 6),
                        StatPeriod.`1_YEAR` to pluralStringResource(R.plurals.n_year, 1, 1)
                    ),
                    currentValue = statPeriod,
                    onValueUpdate = { viewModel.statPeriod.value = it }
                )
            }

            if (mostPlayedSongs.isNotEmpty()) {
                item(key = "mostPlayedSongs") {
                    NavigationTitle(
                        title = stringResource(R.string.most_played_songs),
                        modifier = Modifier.animateItem()
                    )
                }

                items(
                    items = mostPlayedSongs,
                    key = { it.id }
                ) { song ->
                    SongListItem(
                        song = song,
                        isActive = song.id == mediaMetadata?.id,
                        showInLibraryIcon = true,
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
                                                endpoint = WatchEndpoint(song.id),
                                                preloadItem = song.toMediaMetadata()
                                            )
                                        )
                                    }
                                },
                                onLongClick = {
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

            if (mostPlayedArtists.isNotEmpty()) {
                item(key = "mostPlayedArtists") {
                    NavigationTitle(
                        title = stringResource(R.string.most_played_artists),
                        modifier = Modifier.animateItem()
                    )

                    LazyRow(
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = mostPlayedArtists,
                            key = { it.id }
                        ) { artist ->
                            ArtistGridItem(
                                artist = artist,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("artist/${artist.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                                    .animateItem()
                            )
                        }
                    }
                }
            }

            if (mostPlayedAlbums.isNotEmpty()) {
                item(key = "mostPlayedAlbums") {
                    NavigationTitle(
                        title = stringResource(R.string.most_played_albums),
                        modifier = Modifier.animateItem()
                    )

                    LazyRow(
                        modifier = Modifier.animateItem()
                    ) {
                        items(
                            items = mostPlayedAlbums,
                            key = { it.id }
                        ) { album ->
                            AlbumGridItem(
                                album = album,
                                isActive = album.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                coroutineScope = coroutineScope,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("album/${album.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                AlbumMenu(
                                                    originalAlbum = album,
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
                }
            }
        }
    }

    if (lazyChecker) {
        LazyColumnScrollbar(
            state = lazylistState
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.stats)) },
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
        }
    )
}
