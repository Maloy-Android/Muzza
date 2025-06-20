package com.maloy.muzza.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maloy.innertube.models.SongItem
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.models.toMediaMetadata
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.ListMenuItem
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

        ListMenuItem(
            icon = R.drawable.playlist_play,
            title = R.string.play_next,
        ) {
            onDismiss()
            playerConnection.playNext(selection.map { it.toMediaItem() })
            onExitSelectionMode()
        }

        ListMenuItem(
            icon = R.drawable.queue_music,
            title = R.string.add_to_queue,
        ) {
            onDismiss()
            playerConnection.addToQueue(selection.map { it.toMediaItem() })
            onExitSelectionMode()
        }

        ListMenuItem(
            icon = R.drawable.playlist_add,
            title = R.string.add_to_playlist,
        ) {
            showChoosePlaylistDialog = true
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
