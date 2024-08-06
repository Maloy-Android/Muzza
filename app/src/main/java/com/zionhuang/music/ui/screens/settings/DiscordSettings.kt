package com.zionhuang.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.zionhuang.music.LocalPlayerAwareWindowInsets
import com.zionhuang.music.R
import com.zionhuang.music.constants.DiscordTokenKey
import com.zionhuang.music.constants.DiscordUsernameKey
import com.zionhuang.music.constants.DiscordNameKey
import com.zionhuang.music.constants.EnableDiscordRPCKey
import com.zionhuang.music.constants.HideRPCOnPauseKey
import com.zionhuang.music.constants.ShowArtistRPCKey
import com.zionhuang.music.ui.component.IconButton
import com.zionhuang.music.ui.component.PreferenceEntry
import com.zionhuang.music.ui.component.PreferenceGroupTitle
import com.zionhuang.music.ui.component.SwitchPreference
import com.zionhuang.music.ui.utils.backToMain
import com.zionhuang.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var discordToken by rememberPreference(DiscordTokenKey, "")
    var discordUsername by rememberPreference(DiscordUsernameKey, "")
    var discordName by rememberPreference(DiscordNameKey, "")

    val (discordRPC, onDiscordRPCChange) = rememberPreference(key = EnableDiscordRPCKey, defaultValue = true)
    val (showArtist, onShowArtistChange) = rememberPreference(key = ShowArtistRPCKey, defaultValue = true)
    val (hideRPCOnPause, onHideRPCOnPauseChange) = rememberPreference(key = HideRPCOnPauseKey, defaultValue = true)


    var isLoggedIn = remember(discordToken) {
        discordToken != ""
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = "ACCOUNT"
        )
        PreferenceEntry(
            title = { Text(if (isLoggedIn) discordName else stringResource(R.string.login)) },
            description = if (isLoggedIn) {
                "@$discordUsername"
            } else null,
            icon = { Icon(painterResource(R.drawable.discord), null) },
            onClick = { navController.navigate("settings/discord/login") }
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.logout)) },
            description = null,
            icon = { Icon(painterResource(R.drawable.logout), null) },
            onClick = {
                discordName = ""
                discordToken = ""
                discordUsername = ""
                isLoggedIn = false

            }
        )

        PreferenceGroupTitle(
            title = "OPTIONS"
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.enable_rpc)) },
            checked = discordRPC,
            onCheckedChange = onDiscordRPCChange,
            isEnabled = isLoggedIn
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.show_artist_icon)) },
            description = stringResource(R.string.unstable_warning),
            icon = { Icon(painterResource(R.drawable.person), null) },
            checked = showArtist,
            onCheckedChange = onShowArtistChange,
            isEnabled = isLoggedIn && discordRPC
        )
        SwitchPreference(
            title = { Text(stringResource(R.string.hide_RPC_on_pause)) },
            description = stringResource(R.string.unstable_warning),
            icon = { Icon(painterResource(R.drawable.pause), null) },
            checked = hideRPCOnPause,
            onCheckedChange = onHideRPCOnPauseChange,
            isEnabled = isLoggedIn && discordRPC
        )
    }
    TopAppBar(
        title = { Text(stringResource(R.string.discord)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}