package com.dreamify.app.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dreamify.innertube.models.SongItem
import com.dreamify.app.LocalDatabase
import com.dreamify.app.LocalPlayerConnection
import com.dreamify.app.R
import com.dreamify.app.extensions.toMediaItem
import com.dreamify.app.models.toMediaMetadata
import com.dreamify.app.playback.queues.ListQueue
import com.dreamify.app.ui.component.ListMenu
import com.dreamify.app.ui.component.ListMenuItem
import java.time.LocalDateTime

@Composable
fun YouTubeSongSelectionMenu(
    navController: NavController,
    selection: List<SongItem>,
    onDismiss: () -> Unit,
    onExitSelectionMode: () -> Unit,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            val mediaMetadata = selection.map {
                it.toMediaMetadata()
            }
            database.transaction {
                mediaMetadata.forEach(::insert)
            }
            selection.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false },
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
            icon = R.drawable.playlist_play,
            title = R.string.play_next,
        ) {
            onDismiss()
            playerConnection.playNext(selection.map { it.toMediaItem() })
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
        ListMenuItem(
            icon = R.drawable.library_add,
            title = R.string.add_to_library,
        ) {
            database.query {
                selection.forEach { song ->
                    insert(song.toMediaMetadata())
                }
                selection.forEach { song ->
                    inLibrary(song.id, LocalDateTime.now())
                }
            }
            onDismiss()
        }
    }
}
