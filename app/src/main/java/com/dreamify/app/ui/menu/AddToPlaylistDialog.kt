package com.dreamify.app.ui.menu

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.dreamify.app.LocalDatabase
import com.dreamify.app.R
import com.dreamify.app.constants.ListThumbnailSize
import com.dreamify.app.db.entities.Playlist
import com.dreamify.app.db.entities.PlaylistEntity
import com.dreamify.app.ui.component.DefaultDialog
import com.dreamify.app.ui.component.ListDialog
import com.dreamify.app.ui.component.ListItem
import com.dreamify.app.ui.component.PlaylistListItem
import com.dreamify.app.ui.component.TextFieldDialog
import com.dreamify.innertube.YouTube
import com.dreamify.innertube.utils.parseCookieString
import com.dreamify.app.constants.InnerTubeCookieKey
import com.dreamify.app.db.entities.Song
import com.dreamify.app.utils.isInternetAvailable
import com.dreamify.app.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
fun AddToPlaylistDialog(
    navController: NavController,
    isVisible: Boolean,
    initialTextFieldValue: String? = null,
    noSyncing: Boolean = false,
    onGetSong: suspend (Playlist) -> List<String>,
    onDismiss: () -> Unit,
    songs: List<Song>? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    var playlists by remember {
        mutableStateOf(emptyList<Playlist>())
    }
    val (innerTubeCookie) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    var showAddPlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    var syncedPlaylist: Boolean by remember {
        mutableStateOf(false)
    }

    var showDuplicateDialog by remember {
        mutableStateOf(false)
    }
    var selectedPlaylist by remember {
        mutableStateOf<Playlist?>(null)
    }
    var songIds by remember {
        mutableStateOf<List<String>?>(null)
    }
    var duplicates by remember {
        mutableStateOf(emptyList<String>())
    }

    LaunchedEffect(Unit) {
        database.editablePlaylistsByCreateDateAsc().collect {
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
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                            modifier = Modifier.size(ListThumbnailSize)
                        )
                    },
                    modifier = Modifier.clickable {
                        showAddPlaylistDialog = true
                    }
                )
            }

            items(playlists) { playlist ->
                PlaylistListItem(
                    playlist = playlist,
                    thumbnail = Icons.AutoMirrored.Rounded.QueueMusic,
                    modifier = Modifier.clickable {
                        selectedPlaylist = playlist
                        coroutineScope.launch(Dispatchers.IO) {
                            if (songIds == null) {
                                songIds = onGetSong(playlist)
                            }
                            duplicates = database.playlistDuplicates(playlist.id, songIds!!)
                            if (duplicates.isNotEmpty()) {
                                showDuplicateDialog = true
                            } else {
                                onDismiss()
                                database.addSongToPlaylist(playlist, songIds!!)
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddPlaylistDialog) {
        TextFieldDialog(
            icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            initialTextFieldValue = TextFieldValue(initialTextFieldValue?: ""),
            onDismiss = { showAddPlaylistDialog = false },
            onDone = { playlistName ->
                coroutineScope.launch(Dispatchers.IO) {
                    val browseId = if (syncedPlaylist)
                        YouTube.createPlaylist(playlistName)
                    else null

                    database.query {
                        insert(
                            PlaylistEntity(
                                name = playlistName,
                                browseId = browseId,
                                bookmarkedAt = LocalDateTime.now(),
                                isEditable = true,
                                isLocal = !syncedPlaylist
                            )
                        )
                    }
                }
            },
            extraContent = {
                if (isLoggedIn && isInternetAvailable(context)) {
                Row(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 40.dp)
                ) {
                        Column {
                            Text(
                                text = stringResource(R.string.sync_playlist),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                text = stringResource(R.string.allows_for_sync_witch_youtube),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth(0.7f)
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isInternetAvailable(context)) {
                                Switch(
                                    enabled = !noSyncing,
                                    checked = syncedPlaylist,
                                    onCheckedChange = {
                                        syncedPlaylist = !syncedPlaylist
                                    },
                                )
                            }
                        }
                    }
                }
            }
        )
    }

    if (showDuplicateDialog) {
        DefaultDialog(
            title = { Text(stringResource(R.string.duplicates)) },
            buttons = {
                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                        onDismiss()
                        database.transaction {
                            addSongToPlaylist(
                                selectedPlaylist!!,
                                songIds!!.filter {
                                    !duplicates.contains(it)
                                }
                            )
                        }
                    }
                ) {
                    Text(stringResource(R.string.skip_duplicates))
                }

                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                        onDismiss()
                        database.transaction {
                            addSongToPlaylist(selectedPlaylist!!, songIds!!)
                        }
                    }
                ) {
                    Text(stringResource(R.string.add_anyway))
                }

                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            onDismiss = {
                showDuplicateDialog = false
            }
        ) {
            Text(
                text = if (duplicates.size == 1) {
                    stringResource(R.string.duplicates_description_single)
                } else {
                    stringResource(R.string.duplicates_description_multiple, duplicates.size)
                },
                textAlign = TextAlign.Start,
                modifier = Modifier.align(Alignment.Start)
            )
        }
    }
}
