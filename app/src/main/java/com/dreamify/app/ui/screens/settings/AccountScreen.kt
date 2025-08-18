package com.dreamify.app.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dreamify.innertube.YouTube
import com.dreamify.innertube.utils.parseCookieString
import com.dreamify.app.LocalPlayerAwareWindowInsets
import com.dreamify.app.R
import com.dreamify.app.constants.AccountChannelHandleKey
import com.dreamify.app.constants.AccountEmailKey
import com.dreamify.app.constants.AccountNameKey
import com.dreamify.app.constants.InnerTubeCookieKey
import com.dreamify.app.constants.YtmSyncKey
import com.dreamify.app.ui.component.IconButton
import com.dreamify.app.ui.component.InfoLabel
import com.dreamify.app.ui.component.PreferenceEntry
import com.dreamify.app.ui.component.PreferenceGroupTitle
import com.dreamify.app.ui.component.SwitchPreference
import com.dreamify.app.ui.component.TextFieldDialog
import com.dreamify.app.ui.utils.backToMain
import com.dreamify.app.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, defaultValue = true)

    var showToken: Boolean by remember {
        mutableStateOf(false)
    }
    var showTokenEditor by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        YouTube.useLoginForBrowse = isLoggedIn
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
    ) {
        PreferenceGroupTitle(
            title = stringResource(R.string.account)
        )
        PreferenceEntry(
            title = { Text(if (isLoggedIn) accountName else stringResource(R.string.login)) },
            description = if (isLoggedIn) {
                accountEmail.takeIf { it.isNotEmpty() }
                    ?: accountChannelHandle.takeIf { it.isNotEmpty() }
            } else {
                null
            },
            icon = { Icon(painterResource(R.drawable.person), null) },
            trailingContent = {
                if (isLoggedIn) {
                    OutlinedButton(onClick = {
                        onInnerTubeCookieChange("")
                    },
                    ) {
                        Text(stringResource(R.string.logout))
                    }
                }
            },
            onClick = { if (!isLoggedIn) navController.navigate("login") }
        )
        if (showTokenEditor) {
            TextFieldDialog(
                modifier = Modifier,
                initialTextFieldValue = TextFieldValue(innerTubeCookie),
                onDone = { onInnerTubeCookieChange(it) },
                onDismiss = { showTokenEditor = false },
                singleLine = false,
                maxLines = 20,
                isInputValid = {
                    it.isNotEmpty() &&
                            try {
                                "SAPISID" in parseCookieString(it)
                                true
                            } catch (e: Exception) {
                                false
                            }
                },
                extraContent = {
                    InfoLabel(text = stringResource(R.string.token_adv_login_description))
                }
            )
        }
        PreferenceEntry(
            title = {
                if (showToken) {
                    Text(stringResource(R.string.token_shown))
                    Text(
                        text = if (isLoggedIn) innerTubeCookie else stringResource(R.string.not_logged_in),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Light,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1
                    )
                } else {
                    Text(stringResource(R.string.token_hidden))
                }
            },
            icon = { Icon(painterResource(R.drawable.token), null) },
            onClick = {
                if (!showToken) {
                    showToken = true
                } else {
                    showTokenEditor = true
                }
            },
        )
        if (isLoggedIn) {
            SwitchPreference(
                title = { Text(stringResource(R.string.ytm_sync)) },
                icon = { Icon(painterResource(R.drawable.cached), null) },
                checked = ytmSync,
                onCheckedChange = onYtmSyncChange,
                isEnabled = true
            )
        }
        PreferenceGroupTitle(
            title = stringResource(R.string.title_spotify)
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.import_from_spotify)) },
            description = null,
            icon = { Icon(painterResource(R.drawable.spotify), null) },
            onClick = {
                navController.navigate("settings/import_from_spotify/ImportFromSpotify")
            }
        )
        PreferenceGroupTitle(
            title = stringResource(R.string.title_discord)
        )
        PreferenceEntry(
            title = { Text(stringResource(R.string.discord_integration)) },
            icon = { Icon(painterResource(R.drawable.discord), null) },
            onClick = { navController.navigate("settings/discord") }
        )
    }
    TopAppBar(
        title = { Text(stringResource(R.string.account)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null
                )
            }
        },
        scrollBehavior = scrollBehavior
    )
}