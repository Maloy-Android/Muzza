package com.maloy.muzza.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.my.kizzy.rpc.KizzyRPC
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.LocalPlayerConnection
import com.maloy.muzza.R
import com.maloy.muzza.constants.DiscordNameKey
import com.maloy.muzza.constants.DiscordTokenKey
import com.maloy.muzza.constants.DiscordUsernameKey
import com.maloy.muzza.constants.EnableDiscordRPCKey
import com.maloy.muzza.db.entities.Song
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.media3.common.Player.STATE_READY
import com.maloy.muzza.constants.DiscordUseDetailsKey
import com.maloy.muzza.constants.DiscordUserAvatarKay
import com.maloy.muzza.ui.component.InfoLabel
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.SongProgressBar
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.component.TextFieldDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val song by playerConnection.currentSong.collectAsState(null)

    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")
    var discordUserAvatar by rememberPreference(DiscordUserAvatarKay, "")

    val playbackState by playerConnection.playbackState.collectAsState()
    var position by rememberSaveable(playbackState) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    val (useDetails, onUseDetailsChange) = rememberPreference(
        key = DiscordUseDetailsKey,
        defaultValue = false
    )

    LaunchedEffect(discordToken) {
        val token = discordToken
        if (token.isEmpty()) {
            discordUsername = ""
            discordName = ""
            discordUserAvatar = ""
            return@LaunchedEffect
        }
        launch(Dispatchers.IO) {
            KizzyRPC.getUserInfo(token).onSuccess {
                discordUsername = it.username
                discordName = it.name
                discordUserAvatar = if (it.userAvatar.isNotEmpty()) {
                    "https://cdn.discordapp.com/avatars/${it.id}/${it.userAvatar}.png"
                } else {
                    ""
                }
            }.onFailure {
                discordUsername = ""
                discordName = ""
            }
        }
    }


    LaunchedEffect(playbackState) {
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(100)
                position = playerConnection.player.currentPosition
            }
        }
    }

    val (discordRPC, onDiscordRPCChange) = rememberPreference(
        key = EnableDiscordRPCKey,
        defaultValue = true
    )
    val isLoggedIn = remember(discordToken) { discordToken != "" }

    var showTokenDialog by rememberSaveable { mutableStateOf(false) }

    if (showTokenDialog) {
        TextFieldDialog(
            onDismiss = { showTokenDialog = false },
            icon = { Icon(painterResource(R.drawable.token), null) },
            onDone = {
                discordToken = it
                showTokenDialog = false
            },
            singleLine = true,
            isInputValid = { it.isNotEmpty() },
            extraContent = {
                InfoLabel(text = stringResource(R.string.login_by_token))
            }
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PreferenceGroupTitle(
                    title = stringResource(R.string.account),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoggedIn && discordUserAvatar.isNotEmpty()) {
                        AsyncImage(
                            model = discordUserAvatar,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.discord),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLoggedIn) discordName else stringResource(R.string.not_logged_in),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.alpha(if (isLoggedIn) 1f else 0.5f),
                        )
                        if (discordUsername.isNotEmpty()) {
                            Text(
                                text = "@$discordUsername",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isLoggedIn) {
                        OutlinedButton(
                            onClick = {
                                discordName = ""
                                discordToken = ""
                                discordUsername = ""
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.logout))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { navController.navigate("settings/discord/login") },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.login))
                        }
                    }
                }
            }
        }

        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PreferenceGroupTitle(
                    title = stringResource(R.string.options),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (!isLoggedIn) {
                    PreferenceEntry(
                        title = {
                            Text(stringResource(R.string.token_shown))
                        },
                        icon = { Icon(painterResource(R.drawable.token), null) },
                        onClick = {
                            showTokenDialog = true
                        }
                    )
                }

                SwitchPreference(
                    title = { Text(stringResource(R.string.discord_use_details)) },
                    description = stringResource(R.string.discord_use_details_description),
                    checked = useDetails,
                    onCheckedChange = onUseDetailsChange,
                    isEnabled = isLoggedIn && discordRPC,
                )

                SwitchPreference(
                    title = {
                        Text(
                            stringResource(R.string.enable_discord_rpc),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    checked = discordRPC,
                    onCheckedChange = onDiscordRPCChange,
                    isEnabled = isLoggedIn,
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            PreferenceGroupTitle(
                title = stringResource(R.string.preview),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            RichPresence(song, position)
        }
    }

    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.discord_integration)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
fun RichPresence(song: Song?, currentPlaybackTimeMillis: Long = 0L) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.muzza_listening),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier.size(108.dp),
                ) {
                    AsyncImage(
                        model = song?.song?.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.TopStart)
                            .aspectRatio(1f)
                            .run {
                                if (song == null) {
                                    border(
                                        2.dp,
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        RoundedCornerShape(12.dp)
                                    )
                                } else {
                                    this
                                }
                            },
                    )

                    song?.artists?.firstOrNull()?.thumbnailUrl?.let {
                        Box(
                            modifier = Modifier
                                .border(
                                    2.dp,
                                    MaterialTheme.colorScheme.surface,
                                    CircleShape
                                )
                                .padding(2.dp)
                                .align(Alignment.BottomEnd),
                        ) {
                            AsyncImage(
                                model = it,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = song?.song?.title ?: stringResource(R.string.song_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = song?.artists?.joinToString { it.name }
                            ?: stringResource(R.string.artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    song?.album?.title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (song != null) {
                        SongProgressBar(
                            currentTimeMillis = currentPlaybackTimeMillis,
                            durationMillis = song.song.duration.times(1000L),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                enabled = song != null,
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://music.youtube.com/watch?v=${song?.id}".toUri()
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.listen_youtube_music),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        "https://github.com/Maloy-Android/Muzza".toUri()
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.visit_muzza),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
