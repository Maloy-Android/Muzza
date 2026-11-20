package com.maloy.muzza.constants

// All preference key names as plain string constants — usable with multiplatform-settings
// (replaces DataStore Preferences.Key on Android)

object PrefKeys {
    const val DYNAMIC_THEME = "dynamicTheme"
    const val DARK_MODE = "darkMode"
    const val PURE_BLACK = "pureBlack"
    const val SELECTED_THEME_COLOR = "selectedThemeColor"
    const val LYRICS_TEXT_POSITION = "lyricsTextPosition"
    const val LYRIC_FONT_SIZE = "lyricFontSize"
    const val SLIDER_STYLE = "sliderStyle"
    const val HIDE_EXPLICIT = "hideExplicit"
    const val CONTENT_LANGUAGE = "contentLanguage"
    const val CONTENT_COUNTRY = "contentCountry"
    const val AUDIO_QUALITY = "audioQuality"
    const val SKIP_SILENCE = "skipSilence"
    const val AUDIO_NORMALIZATION = "audioNormalization"
    const val PERSISTENT_QUEUE = "persistentQueue"
    const val REPEAT_MODE = "repeatMode"
    const val PLAYER_VOLUME = "playerVolume"
    const val SHOW_LYRICS = "showLyrics"
    const val ENABLE_KUGOU = "enableKugou"
    const val ENABLE_LRCLIB = "enableLrcLib"
    const val PREFERRED_LYRICS_PROVIDER = "lyricsProvider"
    const val INNER_TUBE_COOKIE = "innerTubeCookie"
    const val VISITOR_DATA = "visitorData"
    const val ACCOUNT_NAME = "accountName"
    const val ACCOUNT_EMAIL = "accountEmail"
    const val ACCOUNT_CHANNEL_HANDLE = "accountChannelHandle"
    const val ACCOUNT_IMAGE_URL = "accountImageUrl"
    const val PROXY_ENABLED = "proxyEnabled"
    const val PROXY_URL = "proxyUrl"
    const val DEFAULT_OPEN_TAB = "defaultOpenTab"
    const val FIRST_SETUP_PASSED = "firstSetupPassed"
    const val PAUSE_LISTEN_HISTORY = "pauseListenHistory"
    const val PAUSE_SEARCH_HISTORY = "pauseSearchHistory"
    const val DISCORD_TOKEN = "discordToken"
    const val DISCORD_ENABLED = "discordRPCEnable"
    const val SONG_SORT_TYPE = "songSortType"
    const val SONG_SORT_DESCENDING = "songSortDescending"
    const val ARTIST_SORT_TYPE = "artistSortType"
    const val ARTIST_SORT_DESCENDING = "artistSortDescending"
    const val ALBUM_SORT_TYPE = "albumSortType"
    const val ALBUM_SORT_DESCENDING = "albumSortDescending"
    const val PLAYLIST_SORT_TYPE = "playlistSortType"
    const val PLAYLIST_SORT_DESCENDING = "playlistSortDescending"
    const val MAX_IMAGE_CACHE_SIZE = "maxImageCacheSize"
    const val MAX_SONG_CACHE_SIZE = "maxSongCacheSize"
    const val CROSSFADE_ENABLED = "crossfadeEnabled"
    const val CROSSFADE_DURATION = "crossfadeDuration"
    const val PLAYER_STYLE = "playerStyle"
    const val PLAYER_BACKGROUND_STYLE = "playerBackgroundStyle"
    const val LISTEN_TOGETHER_SERVER_URL = "listenTogetherServerUrl"
    const val LISTEN_TOGETHER_USERNAME = "listenTogetherUsername"
    const val SEARCH_SOURCE = "searchSource"
    const val YTM_SYNC = "ytmSync"
}

// Enums (platform-agnostic)
enum class DarkMode { ON, OFF, AUTO }
enum class NavigationTab { HOME, EXPLORE, LIBRARY }
enum class LyricsPosition { LEFT, CENTER, RIGHT }
enum class SliderStyle { DEFAULT, SQUIGGLY, COMPOSE }
enum class GridCellSize { BIG, SMALL }
enum class AudioQuality { AUTO, HIGH, LOW }
enum class LibraryViewType { LIST, GRID }
enum class PreferredLyricsProvider { KUGOU, LRCLIB, SIMPMUSIC }
enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class PlaylistSongSortType { CUSTOM, CREATE_DATE, NAME, ARTIST, PLAY_TIME }
enum class ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }
enum class AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }
enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }
enum class ArtistFilter { LIBRARY, LIKED, PROFILES }
enum class PlaylistFilter { LIKED, LOCAL, DOWNLOADED }
enum class AlbumFilter { LIBRARY, LIKED, DOWNLOADED }
enum class SearchSource { LOCAL, ONLINE }
enum class PlayerStyle { OLD, NEW }
enum class MiniPlayerStyle { OLD, NEW }
enum class PlayerBackgroundStyle { DEFAULT, GRADIENT, BLUR, BLURMOV }
enum class HistorySource { LOCAL, REMOTE }

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
