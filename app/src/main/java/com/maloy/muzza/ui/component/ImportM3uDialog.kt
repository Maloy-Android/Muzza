package com.maloy.muzza.ui.component


import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalSnackbarHostState
import com.maloy.muzza.R
import com.maloy.muzza.constants.ScannerM3uMatchCriteria
import com.maloy.muzza.db.MusicDatabase
import com.maloy.muzza.db.entities.ArtistEntity
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.db.entities.SongEntity
import com.maloy.muzza.ui.menu.AddToPlaylistDialog
import com.maloy.muzza.ui.utils.youtubeSongLookup
import com.maloy.muzza.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InputStream

@Composable
fun ImportM3uDialog(
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val snackbarHostState = LocalSnackbarHostState.current

    var scannerSensitivity by remember {
        mutableStateOf(ScannerM3uMatchCriteria.LEVEL_1)
    }

    val remoteLookup by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var importedTitle by remember { mutableStateOf("") }
    val importedSongs = remember { mutableStateListOf<Song>() }
    val rejectedSongs = remember { mutableStateListOf<String>() }

    val importM3uLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    isLoading = true
                    if (uri != null) {
                        val result = loadM3u(
                            context,
                            database,
                            snackbarHostState,
                            uri,
                            searchOnline = remoteLookup
                        )
                        importedSongs.clear()
                        importedSongs.addAll(result.first)
                        rejectedSongs.clear()
                        rejectedSongs.addAll(result.second)
                        importedTitle = result.third
                    }
                } catch (e: Exception) {
                    reportException(e)
                } finally {
                    isLoading = false
                }
            }

        }


    DefaultDialog(
        onDismiss = onDismiss,
        icon = { Icon(painterResource(R.drawable.restore), null) },
        title = { Text(stringResource(R.string.import_playlist)) },
    ) {
        EnumListPreference(
            title = { Text(stringResource(R.string.scanner_sensitivity_title)) },
            icon = { Icon(Icons.Rounded.GraphicEq, null) },
            selectedValue = scannerSensitivity,
            onValueSelected = { scannerSensitivity = it },
            valueText = {
                when (it) {
                    ScannerM3uMatchCriteria.LEVEL_1 -> stringResource(R.string.scanner_sensitivity_L1)
                    ScannerM3uMatchCriteria.LEVEL_2 -> stringResource(R.string.scanner_sensitivity_L2)
                }
            }
        )
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            }

            Button(
                onClick = {
                    importedSongs.clear()
                    rejectedSongs.clear()
                    importM3uLauncher.launch(arrayOf("audio/*"))
                },
                enabled = !isLoading
            ) {
                Text(stringResource(R.string.m3u_import_playlist))
            }
        }

        if (importedSongs.isNotEmpty()) {
            Text(
                text = stringResource(R.string.import_success_songs),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 150.dp)
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp)
            ) {
                itemsIndexed(
                    items = importedSongs.map { it.title },
                    key = { _, song -> song.hashCode() }
                ) { _, item ->
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }

        if (rejectedSongs.isNotEmpty()) {
            Text(
                text = stringResource(R.string.import_failed_songs),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 150.dp)
                    .padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp)
            ) {
                itemsIndexed(
                    items = rejectedSongs,
                    key = { _, song -> song.hashCode() }
                ) { _, item ->
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                }
            }
        }



        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(
                onClick = {
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.cancel))
            }

            TextButton(
                onClick = {
                    showChoosePlaylistDialog = true
                },
                enabled = importedSongs.isNotEmpty()
            ) {
                Text(stringResource(R.string.add_to_playlist))
            }
        }
    }

    if (showChoosePlaylistDialog) {
        AddToPlaylistDialog(
            navController = navController,
            isVisible = true,
            initialTextFieldValue = importedTitle,
            songs = importedSongs,
            onGetSong = {
                runBlocking(Dispatchers.IO) {
                    importedSongs.forEach { song ->
                        if (database.song(song.id).first() == null) {
                            database.insert(song.song)
                            song.artists.forEach { artist ->
                                if (database.artist(artist.id).first() == null) {
                                    database.insert(artist)
                                }
                            }
                        }
                    }
                }
                importedSongs.map { it.id }
            },
            onDismiss = { showChoosePlaylistDialog = false }
        )
    }
}

fun loadM3u(
    context: Context,
    database: MusicDatabase,
    snackbarHostState: SnackbarHostState,
    uri: Uri,
    searchOnline: Boolean = false
): Triple<ArrayList<Song>, ArrayList<String>, String> {
    val songs = ArrayList<Song>()
    val rejectedSongs = ArrayList<String>()

    runCatching {
        context.applicationContext.contentResolver.openInputStream(uri)?.use { stream ->
            val lines = stream.readLines()
            if (lines.isEmpty()) return@runCatching
            if (lines.first().startsWith("#EXTM3U")) {
                lines.forEachIndexed { index, rawLine ->
                    if (rawLine.startsWith("#EXTINF:")) {
                        val artists =
                            rawLine.substringAfter("#EXTINF:").substringAfter(',')
                                .substringBefore(" - ").split(';')
                        val title = rawLine.substringAfter("#EXTINF:").substringAfter(',')
                            .substringAfter(" - ")
                        val source = if (index + 1 < lines.size) lines[index + 1] else null

                        Song(
                            song = SongEntity(
                                id = "",
                                title = title,
                                isLocal = true,
                                localPath = if (source?.startsWith("http") == false) source.substringAfter(
                                    ','
                                ) else null
                            ),
                            artists = artists.map { ArtistEntity("", it) },
                        )
                        val matches = if (source == null) {
                            runBlocking(Dispatchers.IO) {
                                database.searchSongsInDb(title).first().toMutableList()
                            }
                        } else {
                            runBlocking(Dispatchers.IO) {
                                var id = source.substringBefore(',')
                                if (id.isEmpty()) {
                                    id = source.substringAfter("watch?").substringAfter("=")
                                        .substringBefore('?')
                                }
                                val dbResult = mutableListOf(database.song(id).first())
                                dbResult.addAll(database.searchSongsInDb(title).first())
                                dbResult.filterNotNull().toMutableList()
                            }
                        }
                        if (searchOnline && matches.isEmpty() && source?.contains(',') == false) {
                            val onlineResult = runBlocking(Dispatchers.IO) {
                                youtubeSongLookup("$title ${artists.joinToString(" ")}", source)
                            }
                            onlineResult.forEach { it ->
                                val result = Song(
                                    song = it.toSongEntity(),
                                    artists = it.artists.map {
                                        ArtistEntity(
                                            id = it.id ?: ArtistEntity.generateArtistId(),
                                            name = it.name
                                        )
                                    }
                                )
                                matches.add(result)
                            }
                        }
                        val oldSize = songs.size
                        songs.add(matches.first())
                        if (oldSize == songs.size) {
                            rejectedSongs.add(rawLine)
                        }
                    }
                }
            }
        }
    }.onFailure {
        reportException(it)
        Toast.makeText(context, R.string.m3u_import_playlist_failed, Toast.LENGTH_SHORT).show()
    }

    if (songs.isEmpty()) {
        CoroutineScope(Dispatchers.Main).launch {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.m3u_import_failed),
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
        }
    }
    return Triple(
        songs,
        rejectedSongs,
        uri.path?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
    )
}

fun InputStream.readLines(): List<String> {
    return this.bufferedReader().useLines { it.toList() }
}
