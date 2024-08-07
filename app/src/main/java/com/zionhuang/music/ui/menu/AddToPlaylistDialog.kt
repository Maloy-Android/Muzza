package com.zionhuang.music.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zionhuang.music.LocalDatabase
import com.zionhuang.music.R
import com.zionhuang.music.constants.ListThumbnailSize
import com.zionhuang.music.db.entities.Playlist
import com.zionhuang.music.db.entities.PlaylistEntity
import com.zionhuang.music.ui.component.DefaultDialog
import com.zionhuang.music.ui.component.ListDialog
import com.zionhuang.music.ui.component.ListItem
import com.zionhuang.music.ui.component.PlaylistListItem
import com.zionhuang.music.ui.component.TextFieldDialog

@Composable
fun AddToPlaylistDialog(
    isVisible: Boolean,
    onAdd: (Playlist) -> Unit,
    onDismiss: () -> Unit,
    songs: List<String> = emptyList(),
) {
    val database = LocalDatabase.current
    var playlists by remember {
        mutableStateOf(emptyList<Playlist>())
    }
    var showCreatePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showAddToPlaylistConformationDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var pickedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    val conformationDescription = if (songs.size > 1)
        R.string.add_to_playlist_multiple_conformation_desc
    else  R.string.add_to_playlist_single_conformation_desc

    LaunchedEffect(Unit) {
        database.playlistsByCreateDateAsc().collect {
            playlists = it.asReversed()
        }
    }

    if (isVisible) {
        ListDialog(
            onDismiss = onDismiss
        ) {
            item {
                ListItem(
                    title = stringResource(R.string.create_playlist),
                    thumbnailContent = {
                        Image(
                            painter = painterResource(R.drawable.add),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    },
                    modifier = Modifier.clickable {
                        showCreatePlaylistDialog = true
                    }
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    modifier = Modifier.clickable {
                        database.query {
                            val playlistsWithSong = songs.flatMap { playlistSongMaps(it) }
                            val isSongPresentInPlaylist = playlistsWithSong
                                .find { playlist.playlist.id == it.playlistId } != null

                            if (isSongPresentInPlaylist) {
                                pickedPlaylist = playlist
                                showAddToPlaylistConformationDialog = true
                            } else {
                                onAdd(playlist)
                                onDismiss()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showCreatePlaylistDialog) {
        TextFieldDialog(
            icon = { Icon(painter = painterResource(R.drawable.add), contentDescription = null) },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            onDismiss = { showCreatePlaylistDialog = false },
            onDone = { playlistName ->
                database.query {
                    insert(
                        PlaylistEntity(
                            name = playlistName
                        )
                    )
                }
            }
        )
    }

    if (showAddToPlaylistConformationDialog) {
        DefaultDialog(
            onDismiss = { showAddToPlaylistConformationDialog = false },
            content = {
                Text(
                    text = stringResource(id = conformationDescription),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )
            },
            buttons = {
                TextButton(
                    onClick = { showAddToPlaylistConformationDialog = false }
                ) {
                    Text(text = stringResource(R.string.add_to_playlist_cancel))
                }

                TextButton(
                    onClick = {
                        showAddToPlaylistConformationDialog = false
                        pickedPlaylist?.let(onAdd)
                        onDismiss()
                    }
                ) {
                    Text(text = stringResource(R.string.add_to_playlist_add))
                }
            }
        )
    }
}
