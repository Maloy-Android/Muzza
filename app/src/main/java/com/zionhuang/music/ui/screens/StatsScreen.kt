package com.zionhuang.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.music.LocalPlayerAwareWindowInsets
import com.zionhuang.music.LocalPlayerConnection
import com.zionhuang.music.R
import com.zionhuang.music.constants.StatPeriod
import com.zionhuang.music.extensions.togglePlayPause
import com.zionhuang.music.models.toMediaMetadata
import com.zionhuang.music.playback.queues.YouTubeQueue
import com.zionhuang.music.ui.component.ArtistListItem
import com.zionhuang.music.ui.component.LocalMenuState
import com.zionhuang.music.ui.component.NavigationTitle
import com.zionhuang.music.ui.component.SongListItem
import com.zionhuang.music.ui.menu.SongMenu
import com.zionhuang.music.viewmodels.StatsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val statPeriod by viewModel.statPeriod.collectAsState()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsState()
    val mostPlayedArtists by viewModel.mostPlayedArtists.collectAsState()

    LazyColumn(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
        modifier = Modifier.windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top))
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Spacer(Modifier.width(8.dp))

                StatPeriod.values().forEach { period ->
                    FilterChip(
                        label = {
                            Text(
                                when (period) {
                                    StatPeriod.`1_WEEK` -> pluralStringResource(R.plurals.n_week, 1, 1)
                                    StatPeriod.`1_MONTH` -> pluralStringResource(R.plurals.n_month, 1, 1)
                                    StatPeriod.`3_MONTH` -> pluralStringResource(R.plurals.n_month, 3, 3)
                                    StatPeriod.`6_MONTH` -> pluralStringResource(R.plurals.n_month, 6, 6)
                                    StatPeriod.`1_YEAR` -> pluralStringResource(R.plurals.n_year, 1, 1)
                                    StatPeriod.ALL -> stringResource(R.string.filter_all)
                                }
                            )
                        },
                        selected = statPeriod == period,
                        colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.background),
                        onClick = {
                            viewModel.statPeriod.value = period
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
        item {
            NavigationTitle(stringResource(R.string.most_played_songs))
        }
        items(
            items = mostPlayedSongs,
            key = { it.id }
        ) { song ->
            SongListItem(
                song = song,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                trailingContent = {
                    IconButton(
                        onClick = {
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
                                    navController = navController,
                                    playerConnection = playerConnection,
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
                    .combinedClickable {
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
                    }
                    .animateItemPlacement()
            )
        }
        item {
            NavigationTitle(stringResource(R.string.most_played_artists))
        }
        items(
            items = mostPlayedArtists,
            key = { it.id }
        ) { artist ->
            ArtistListItem(
                artist = artist,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate("artist/${artist.id}")
                    }
                    .animateItemPlacement()
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.stats)) },
        navigationIcon = {
            IconButton(onClick = navController::navigateUp) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
