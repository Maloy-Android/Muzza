package com.maloy.muzza.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maloy.innertube.utils.parseCookieString
import com.maloy.muzza.LocalPlayerAwareWindowInsets
import com.maloy.muzza.R
import com.maloy.muzza.constants.AccountChannelHandleKey
import com.maloy.muzza.constants.AccountEmailKey
import com.maloy.muzza.constants.AccountNameKey
import com.maloy.muzza.constants.ContentCountryKey
import com.maloy.muzza.constants.ContentLanguageKey
import com.maloy.muzza.constants.CountryCodeToName
import com.maloy.muzza.constants.EnableKugouKey
import com.maloy.muzza.constants.EnableLrcLibKey
import com.maloy.muzza.constants.HideExplicitKey
import com.maloy.muzza.constants.HistoryDuration
import com.maloy.muzza.constants.InnerTubeCookieKey
import com.maloy.muzza.constants.LanguageCodeToName
import com.maloy.muzza.constants.PreferredLyricsProvider
import com.maloy.muzza.constants.PreferredLyricsProviderKey
import com.maloy.muzza.constants.ProxyEnabledKey
import com.maloy.muzza.constants.ProxyTypeKey
import com.maloy.muzza.constants.ProxyUrlKey
import com.maloy.muzza.constants.SYSTEM_DEFAULT
import com.maloy.muzza.ui.component.EditTextPreference
import com.maloy.muzza.ui.component.IconButton
import com.maloy.muzza.ui.component.ListPreference
import com.maloy.muzza.ui.component.PreferenceEntry
import com.maloy.muzza.ui.component.PreferenceGroupTitle
import com.maloy.muzza.ui.component.SliderPreference
import com.maloy.muzza.ui.component.SwitchPreference
import com.maloy.muzza.ui.utils.backToMain
import com.maloy.muzza.utils.rememberEnumPreference
import com.maloy.muzza.utils.rememberPreference
import java.net.Proxy
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val accountName by rememberPreference(AccountNameKey, "")
    val accountEmail by rememberPreference(AccountEmailKey, "")
    val accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }
    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrcLib, onEnableLrcLibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)

    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")
    val (historyDuration, onHistoryDurationChange) = rememberPreference(key = HistoryDuration, defaultValue = 30f)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (preferredProvider, onPreferredProviderChange) = rememberEnumPreference(key = PreferredLyricsProviderKey, defaultValue = PreferredLyricsProvider.LRCLIB)


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

        PreferenceEntry(
            title = { Text(if (isLoggedIn) accountName else stringResource(R.string.login)) },
            description = if (isLoggedIn) {
                accountEmail.takeIf { it.isNotEmpty() }
                    ?: accountChannelHandle.takeIf { it.isNotEmpty() }
            } else null,
            icon = { Icon(painterResource(R.drawable.person), null) },
            onClick = { navController.navigate("login") }
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
                                Uri.parse("package:${context.packageName}")
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

        SwitchPreference(
            title = { Text(stringResource(R.string.hide_explicit)) },
            icon = { Icon(painterResource(R.drawable.explicit), null) },
            checked = hideExplicit,
            onCheckedChange = onHideExplicitChange
        )

        PreferenceGroupTitle(
            title = stringResource(R.string.app_language),
        )

        LanguageSelector()

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_lrclib)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = enableLrcLib,
            onCheckedChange = onEnableLrcLibChange
        )

        SwitchPreference(
            title = { Text(stringResource(R.string.enable_kugou)) },
            icon = { Icon(painterResource(R.drawable.lyrics), null) },
            checked = enableKugou,
            onCheckedChange = onEnableKugouChange
        )

        ListPreference(
            title = { Text(stringResource(R.string.default_lyrics_provider)) },
            selectedValue = preferredProvider,
            values = listOf(PreferredLyricsProvider.KUGOU, PreferredLyricsProvider.LRCLIB),
            valueText = { it.name.toLowerCase(androidx.compose.ui.text.intl.Locale.current).capitalize(androidx.compose.ui.text.intl.Locale.current) },
            onValueSelected = onPreferredProviderChange
        )

        SliderPreference(
            title = { Text(stringResource(R.string.history_duration)) },
            value = historyDuration,
            onValueChange = onHistoryDurationChange,
        )

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


@Composable
fun LanguageSelector() {
    val context = LocalContext.current
    // List of supported languages and their locale codes
    val languages = listOf(
        "Arabic" to "ar",
        "Belarusian" to "be",
        "Chinese Simplified" to "zh",
        "Czech" to "cs",
        "Dutch" to "nl",
        "English" to "en",
        "French" to "fr",
        "German" to "de",
        "Indonesian" to "id",
        "Italian" to "it",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Portuguese, Brazilian" to "pt-BR",
        "Russian" to "ru",
        "Spanish" to "es",
        "Turkish" to "tr",
        "Ukrainian" to "uk",
        "Vietnamese" to "vi",
        "Bulgarian" to "bg",
        "Bengali" to "bn-rIN",
        "German" to "DE",
        "Greek" to "el-rGR",
        "Perdita" to "fa-rIR",
        "Finnish" to "fi-rFi",
        "Hungarian" to "hu",
        "Indonesian" to "id",
        "Malayalam" to "ml-rIN",
        "Punjabi" to "pa",
        "Polish" to "pl",
        "Swedish" to "sv-rSE"
    )

    // State to hold the currently selected language
    var selectedLanguage by remember { mutableStateOf(languages[0].second) }
    var expanded by remember { mutableStateOf(false) } // Dropdown expanded state

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),

        ) {
        Column(modifier = Modifier.padding(16.dp)) {


            // Dropdown button
            FloatingActionButton(
                modifier = Modifier
                    .size(48.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { expanded = true },
            ) {
                Icon(
                    painter = painterResource(R.drawable.translate),
                    contentDescription = null
                )
            }


            Box(
                modifier = Modifier.padding(16.dp),
                contentAlignment = Alignment.Center

            )
            {


                // Dropdown menu for language selection
                DropdownMenu(

                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .background(MaterialTheme.colorScheme.background, shape = RoundedCornerShape(16.dp))
                ) {
                    languages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(text = language.first) },
                            onClick = {
                                selectedLanguage = language.second
                                expanded = false
                                updateLanguage(context, selectedLanguage)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}


fun updateLanguage(context: Context, languageCode: String) {
    val locale: Locale = if (languageCode.contains("-")) {
        // Handle languages with regions like pt-BR
        val parts = languageCode.split("-")
        Locale(parts[0], parts[1])
    } else {
        Locale(languageCode)
    }

    val config = Configuration(context.resources.configuration)
    config.setLocales(LocaleList(locale))

    // Update the configuration
    context.resources.updateConfiguration(config, context.resources.displayMetrics)

    // Optionally, recreate the activity to apply the language change throughout the app
    (context as? androidx.activity.ComponentActivity)?.recreate()
}
