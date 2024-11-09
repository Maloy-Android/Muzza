package com.maloy.muzza.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.CONTENT_TYPE_HEADER
import com.maloy.muzza.constants.CONTENT_TYPE_PLAYLIST
import com.maloy.muzza.constants.GridThumbnailHeight
import com.maloy.muzza.db.entities.Playlist
import com.maloy.muzza.db.entities.PlaylistEntity
import com.maloy.muzza.ui.component.PlaylistGridItem
import com.maloy.muzza.viewmodels.LibraryMixViewModel
import java.util.UUID
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val likedSongs by viewModel.likedSongs.collectAsState()
    val downloadSongs by viewModel.downloadSongs.collectAsState(initial = null)
    val likedPlaylist = Playlist(
        playlist = PlaylistEntity(id = UUID.randomUUID().toString(), name = stringResource(R.string.liked)),
        songCount = if (likedSongs != null) likedSongs!!.size else 0,
        thumbnails = emptyList()
    )
    val downloadPlaylist = Playlist(
        playlist = PlaylistEntity(id = UUID.randomUUID().toString(), name = stringResource(R.string.offline)),
        songCount = if (downloadSongs!= null) downloadSongs!!.size else 0,
        thumbnails = emptyList()
    )
    val lazyGridState = rememberLazyGridState()
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            item(
                key = "filter",
                span = { GridItemSpan(maxLineSpan) },
                contentType = CONTENT_TYPE_HEADER
            ) {
                filterContent()
            }
            item(
                key = "likedPlaylist",
                contentType = { CONTENT_TYPE_PLAYLIST }
            ) {
                PlaylistGridItem(
                    playlist = likedPlaylist,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                navController.navigate("auto_playlist/liked")
                            },
                        )
                        .animateItem()
                )
            }
            item(
                key = "downloadedPlaylist",
                contentType = { CONTENT_TYPE_PLAYLIST }
            ) {
                PlaylistGridItem(
                    playlist = downloadPlaylist,
                    fillMaxWidth = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                navController.navigate("auto_playlist/downloaded")
                            },
                        )
                        .animateItem()
                )
            }
        }
    }
}