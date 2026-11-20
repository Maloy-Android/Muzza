package com.maloy.muzza.util

import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.coroutines.getBooleanFlow
import com.russhwolf.settings.coroutines.getIntFlow
import com.russhwolf.settings.coroutines.getStringFlow
import com.russhwolf.settings.coroutines.getLongFlow
import com.russhwolf.settings.coroutines.getFloatFlow
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalSettingsApi::class)
class AppSettings(private val settings: ObservableSettings) {

    // --- Theme ---
    var dynamicTheme: Boolean
        get() = settings.getBoolean("dynamicTheme", false)
        set(v) = settings.putBoolean("dynamicTheme", v)

    var darkMode: String
        get() = settings.getString("darkMode", "AUTO")
        set(v) = settings.putString("darkMode", v)

    var pureBlack: Boolean
        get() = settings.getBoolean("pureBlack", false)
        set(v) = settings.putBoolean("pureBlack", v)

    // --- Content ---
    var hideExplicit: Boolean
        get() = settings.getBoolean("hideExplicit", false)
        set(v) = settings.putBoolean("hideExplicit", v)

    var contentLanguage: String
        get() = settings.getString("contentLanguage", "en")
        set(v) = settings.putString("contentLanguage", v)

    var contentCountry: String
        get() = settings.getString("contentCountry", "US")
        set(v) = settings.putString("contentCountry", v)

    // --- Playback ---
    var audioQuality: String
        get() = settings.getString("audioQuality", "AUTO")
        set(v) = settings.putString("audioQuality", v)

    var skipSilence: Boolean
        get() = settings.getBoolean("skipSilence", false)
        set(v) = settings.putBoolean("skipSilence", v)

    var audioNormalization: Boolean
        get() = settings.getBoolean("audioNormalization", false)
        set(v) = settings.putBoolean("audioNormalization", v)

    var persistentQueue: Boolean
        get() = settings.getBoolean("persistentQueue", true)
        set(v) = settings.putBoolean("persistentQueue", v)

    var repeatMode: Int
        get() = settings.getInt("repeatMode", 0)
        set(v) = settings.putInt("repeatMode", v)

    var playerVolume: Float
        get() = settings.getFloat("playerVolume", 1f)
        set(v) = settings.putFloat("playerVolume", v)

    // --- Lyrics ---
    var enableKugou: Boolean
        get() = settings.getBoolean("enableKugou", true)
        set(v) = settings.putBoolean("enableKugou", v)

    var enableLrcLib: Boolean
        get() = settings.getBoolean("enableLrcLib", true)
        set(v) = settings.putBoolean("enableLrcLib", v)

    var preferredLyricsProvider: String
        get() = settings.getString("lyricsProvider", "KUGOU")
        set(v) = settings.putString("lyricsProvider", v)

    var showLyrics: Boolean
        get() = settings.getBoolean("showLyrics", false)
        set(v) = settings.putBoolean("showLyrics", v)

    // --- Account / Auth ---
    var innerTubeCookie: String
        get() = settings.getString("innerTubeCookie", "")
        set(v) = settings.putString("innerTubeCookie", v)

    var visitorData: String
        get() = settings.getString("visitorData", "")
        set(v) = settings.putString("visitorData", v)

    var accountName: String
        get() = settings.getString("accountName", "")
        set(v) = settings.putString("accountName", v)

    var accountEmail: String
        get() = settings.getString("accountEmail", "")
        set(v) = settings.putString("accountEmail", v)

    var accountChannelHandle: String
        get() = settings.getString("accountChannelHandle", "")
        set(v) = settings.putString("accountChannelHandle", v)

    var accountImageUrl: String
        get() = settings.getString("accountImageUrl", "")
        set(v) = settings.putString("accountImageUrl", v)

    // --- Proxy ---
    var proxyEnabled: Boolean
        get() = settings.getBoolean("proxyEnabled", false)
        set(v) = settings.putBoolean("proxyEnabled", v)

    var proxyUrl: String
        get() = settings.getString("proxyUrl", "")
        set(v) = settings.putString("proxyUrl", v)

    // --- UI ---
    var defaultOpenTab: String
        get() = settings.getString("defaultOpenTab", "HOME")
        set(v) = settings.putString("defaultOpenTab", v)

    var firstSetupPassed: Boolean
        get() = settings.getBoolean("firstSetupPassed", false)
        set(v) = settings.putBoolean("firstSetupPassed", v)

    var pauseListenHistory: Boolean
        get() = settings.getBoolean("pauseListenHistory", false)
        set(v) = settings.putBoolean("pauseListenHistory", v)

    // --- Flows for reactive UI ---
    fun hideExplicitFlow(): Flow<Boolean> = settings.getBooleanFlow("hideExplicit", false)
    fun showLyricsFlow(): Flow<Boolean> = settings.getShowLyricsFlow()
    fun contentLanguageFlow(): Flow<String> = settings.getStringFlow("contentLanguage", "en")
    fun contentCountryFlow(): Flow<String> = settings.getStringFlow("contentCountry", "US")
    fun audioQualityFlow(): Flow<String> = settings.getStringFlow("audioQuality", "AUTO")
}

@OptIn(ExperimentalSettingsApi::class)
private fun ObservableSettings.getShowLyricsFlow(): Flow<Boolean> =
    getBooleanFlow("showLyrics", false)
