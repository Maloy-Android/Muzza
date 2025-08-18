@file:Suppress("DEPRECATION")

package com.dreamify.app.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import com.dreamify.app.LocalPlayerAwareWindowInsets
import com.dreamify.app.R
import com.dreamify.app.constants.ContentCountryKey
import com.dreamify.app.constants.ContentLanguageKey
import com.dreamify.app.constants.CountryCodeToName
import com.dreamify.app.constants.HideExplicitKey
import com.dreamify.app.constants.LanguageCodeToName
import com.dreamify.app.constants.LikedAutoDownloadKey
import com.dreamify.app.constants.LikedAutodownloadMode
import com.dreamify.app.constants.ProxyEnabledKey
import com.dreamify.app.constants.ProxyTypeKey
import com.dreamify.app.constants.ProxyUrlKey
import com.dreamify.app.constants.SYSTEM_DEFAULT
import com.dreamify.app.constants.SelectedLanguageKey
import com.dreamify.app.ui.component.EditTextPreference
import com.dreamify.app.ui.component.IconButton
import com.dreamify.app.ui.component.ListPreference
import com.dreamify.app.ui.component.PreferenceEntry
import com.dreamify.app.ui.component.PreferenceGroupTitle
import com.dreamify.app.ui.component.SwitchPreference
import com.dreamify.app.ui.utils.backToMain
import com.dreamify.app.utils.rememberEnumPreference
import com.dreamify.app.utils.rememberPreference
import java.net.Proxy
import java.util.Locale
import androidx.core.net.toUri
import androidx.core.content.edit

@SuppressLint("PrivateResource")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val (likedAutoDownload, onLikedAutoDownload) = rememberEnumPreference(LikedAutoDownloadKey, LikedAutodownloadMode.OFF)
    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (selectedLanguage, onSelectedLanguage) = rememberPreference(key = SelectedLanguageKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)

    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.home)
        )

        ListPreference(
            title = { Text(stringResource(R.string.content_language)) },
            icon = { Icon(painterResource(R.drawable.language), null) },
            selectedValue = contentLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = {
                LanguageCodeToName.getOrElse(it) {
                    stringResource(R.string.system_default)
                }
            },
            onValueSelected = onContentLanguageChange
        )
        ListPreference(
            title = { Text(stringResource(R.string.content_country)) },
            icon = { Icon(painterResource(R.drawable.location_on), null) },
            selectedValue = contentCountry,
            values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
            valueText = {
                CountryCodeToName.getOrElse(it) {
                    stringResource(R.string.system_default)
                }
            },
            onValueSelected = onContentCountryChange
        )

        ListPreference(
            title = { Text(stringResource(R.string.like_autodownload)) },
            icon = { Icon(Icons.Rounded.Favorite, null) },
            values = listOf(LikedAutodownloadMode.OFF, LikedAutodownloadMode.ON, LikedAutodownloadMode.WIFI_ONLY),
            selectedValue = likedAutoDownload,
            valueText = { when (it){
                LikedAutodownloadMode.OFF -> stringResource(R.string.state_off)
                LikedAutodownloadMode.ON -> stringResource(R.string.state_on)
                LikedAutodownloadMode.WIFI_ONLY -> stringResource(R.string.wifi_only)
            } },
            onValueSelected = onLikedAutoDownload
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.open_supported_links)) },
                description = stringResource(R.string.configure_supported_links),
                icon = { Icon(painterResource(R.drawable.add_link), null) },
                onClick = {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                "package:${context.packageName}".toUri()
                            ),
                        )
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            R.string.intent_supported_links_not_found,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
            )
        }

        PreferenceGroupTitle(title = stringResource(R.string.content))
        SwitchPreference(
            title = { Text(stringResource(R.string.hide_explicit)) },
            icon = { Icon(painterResource(R.drawable.explicit), null) },
            checked = hideExplicit,
            onCheckedChange = onHideExplicitChange
        )

        PreferenceGroupTitle(title = stringResource(R.string.notifications))
        PreferenceEntry(
            title = { Text(stringResource(R.string.notifications_settings)) },
            icon = { Icon(painterResource(R.drawable.notification_on), null) },
            onClick = { navController.navigate("settings/content/notification") }
        )

        PreferenceGroupTitle(title = stringResource(R.string.app_language))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PreferenceEntry(
                title = { Text(stringResource(R.string.app_language)) },
                icon = { Icon(painterResource(R.drawable.translate), null) },
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }
            )
        } else {
            ListPreference(
                title = { Text(stringResource(R.string.app_language)) },
                icon = { Icon(painterResource(R.drawable.translate), null) },
                selectedValue = selectedLanguage,
                values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                valueText = { LanguageCodeToName[it] ?: stringResource(R.string.system_default) },
                onValueSelected = {
                    onSelectedLanguage(it)
                    updateLanguage(context, it)
                    saveLanguagePreference(context, it)
                }
            )
        }

        PreferenceGroupTitle(
            title = stringResource(R.string.proxy)
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_proxy)) },
            icon = { Icon(painterResource(R.drawable.wifi_proxy), null) },
            checked = proxyEnabled,
            onCheckedChange = onProxyEnabledChange
        )

        AnimatedVisibility(proxyEnabled) {
            Column {
                ListPreference(
                    title = { Text(stringResource(R.string.proxy_type)) },
                    selectedValue = proxyType,
                    values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                    valueText = { it.name },
                    onValueSelected = onProxyTypeChange
                )
                EditTextPreference(
                    title = { Text(stringResource(R.string.proxy_url)) },
                    value = proxyUrl,
                    onValueChange = onProxyUrlChange
                )
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.content)) },
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

fun saveLanguagePreference(context: Context, languageCode: String) {
    val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    sharedPreferences.edit { putString("app_language", languageCode) }
}


fun updateLanguage(context: Context, languageCode: String) {
    val locale = Locale(languageCode)
    val config = Configuration(context.resources.configuration)
    config.setLocales(LocaleList(locale))
    context.resources.updateConfiguration(config, context.resources.displayMetrics)
}
