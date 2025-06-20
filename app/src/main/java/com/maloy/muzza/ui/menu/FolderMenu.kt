package com.maloy.muzza.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.db.entities.Event
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.ui.component.ListMenuItem
import com.maloy.muzza.ui.component.ListMenu
import com.maloy.muzza.ui.component.SongFolderItem
import com.maloy.muzza.ui.utils.DirectoryTree
import com.maloy.muzza.utils.joinByBullet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun FolderMenu(
    folder: DirectoryTree,
    event: Event? = null,
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val allFolderSongs = folder.toList()

    var subDirSongCount by remember {
        mutableIntStateOf(0)
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            database.localSongCountInPath(folder.getFullPath()).first()
            subDirSongCount = database.localSongCountInPath(folder.getFullPath()).first()
        }
    }

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            if (allFolderSongs.isEmpty()) return@AddToPlaylistDialog emptyList()
            allFolderSongs.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false }
    )

    SongFolderItem(
        folderTitle = folder.currentDir,
        modifier = Modifier,
        subtitle = joinByBullet(
            pluralStringResource(R.plurals.n_song, subDirSongCount, subDirSongCount),
            folder.parent
        ),
    )

    HorizontalDivider()

    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        ListMenuItem(
            icon = R.drawable.playlist_play,
            title = R.string.play_next
        ) {
            onDismiss()
            allFolderSongs.forEach {
                playerConnection.playNext(it.toMediaItem())
            }
        }
        ListMenuItem(
            icon = R.drawable.queue_music,
            title = R.string.add_to_queue
        ) {
            onDismiss()
            allFolderSongs.forEach {
                playerConnection.addToQueue((it.toMediaItem()))
            }
        }
        ListMenuItem(
            icon = R.drawable.playlist_add,
            title = R.string.add_to_playlist
        ) {
            showChoosePlaylistDialog = true
        }
    }
}