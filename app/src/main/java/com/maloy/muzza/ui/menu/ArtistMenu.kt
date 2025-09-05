package com.maloy.muzza.ui.menu

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maloy.muzza.LocalDatabase
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.ArtistSongSortType
import com.maloy.muzza.db.entities.Artist
import com.maloy.muzza.extensions.toMediaItem
import com.maloy.muzza.playback.queues.ListQueue
import com.maloy.muzza.ui.component.ArtistListItem
import com.maloy.muzza.ui.component.ListMenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import com.maloy.muzza.ui.component.ListMenu

@Composable
fun ArtistMenu(
    originalArtist: Artist,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val artistState = database.artist(originalArtist.id).collectAsState(initial = originalArtist)
    val artist = artistState.value ?: originalArtist

    ArtistListItem(artist = artist, showLikedIcon = false, badges = {}, trailingContent = {
        IconButton(
            onClick = {
                database.transaction {
                    update(artist.artist.toggleLike())
                    update(artist.artist.localToggleLike())
                }
            }) {
            Icon(
                painter = painterResource(if (artist.artist.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border),
                tint = if (artist.artist.bookmarkedAt != null) MaterialTheme.colorScheme.error else LocalContentColor.current,
                contentDescription = null
            )
        }
    })
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        if (artist.songCount > 0) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        coroutineScope.launch {
                            val songs = withContext(Dispatchers.IO) {
                                database.artistSongs(
                                    artist.id, ArtistSongSortType.CREATE_DATE, true
                                ).first().map { it.toMediaItem() }
                            }
                            playerConnection.playQueue(
                                ListQueue(
                                    title = artist.artist.name, items = songs
                                )
                            )
                        }
                        onDismiss()
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.play),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        coroutineScope.launch {
                            val songs = withContext(Dispatchers.IO) {
                                database.artistSongs(
                                    artist.id, ArtistSongSortType.CREATE_DATE, true
                                ).first().map { it.toMediaItem() }.shuffled()
                            }
                            playerConnection.playQueue(
                                ListQueue(
                                    title = artist.artist.name, items = songs
                                )
                            )
                        }
                        onDismiss()
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.shuffle),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        }
        if (artist.artist.isYouTubeArtist) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        onDismiss()
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT, "https://music.youtube.com/channel/${artist.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = stringResource(R.string.share),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .basicMarquee()
                        .padding(top = 4.dp),
                )
            }
        }
    }

    ListMenu(
        contentPadding = PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
        )
    ) {
        ListMenuItem(
            icon = R.drawable.music_note, title = R.string.listen_youtube_music
        ) {
            val intent = Intent(
                Intent.ACTION_VIEW, "https://music.youtube.com/channel/${artist.id}".toUri()
            )
            context.startActivity(intent)
        }
    }
}
