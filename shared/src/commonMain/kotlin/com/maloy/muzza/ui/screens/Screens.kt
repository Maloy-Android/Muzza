package com.maloy.muzza.ui.screens

import androidx.compose.runtime.Immutable

@Immutable
sealed class Screens(val route: String) {
    data object Home     : Screens("home")
    data object Explore  : Screens("explore")
    data object Library  : Screens("library")

    companion object {
        val MainScreens = listOf(Home, Explore, Library)
    }
}

// All named routes used in navigation
object Routes {
    const val HOME               = "home"
    const val EXPLORE            = "explore"
    const val LIBRARY            = "library"
    const val LIBRARY_ARTISTS    = "library_artists"
    const val LIBRARY_ALBUMS     = "library_albums"
    const val LIBRARY_PLAYLISTS  = "library_playlists"
    const val HISTORY            = "history"
    const val STATS              = "stats"
    const val MOOD_AND_GENRES    = "mood_and_genres"
    const val NEW_RELEASE        = "new_release"
    const val NEW_CLIPS          = "new_clips"
    const val SETTINGS           = "settings"
    const val SETTINGS_APPEARANCE= "settings/appearance"
    const val SETTINGS_ACCOUNT   = "settings/account"
    const val SETTINGS_CONTENT   = "settings/content"
    const val SETTINGS_PLAYER    = "settings/player"
    const val SETTINGS_LYRICS    = "settings/player/lyrics"
    const val SETTINGS_STORAGE   = "settings/storage"
    const val SETTINGS_PRIVACY   = "settings/privacy"
    const val SETTINGS_BACKUP    = "settings/backup_restore"
    const val SETTINGS_DISCORD   = "settings/discord"
    const val SETTINGS_ABOUT     = "settings/about"
    const val LOGIN              = "login"
    const val SETUP_WIZARD       = "setup_wizard"
    const val AUTO_PLAYLIST_LIKED      = "auto_playlist/liked_songs"
    const val AUTO_PLAYLIST_DOWNLOADED = "auto_playlist/downloaded_songs"
    const val AUTO_PLAYLIST_LOCAL      = "AutoPlaylistLocal"
    const val AUTO_PLAYLIST_LIBRARY    = "AutoPlaylistLibrary"
    const val CACHED_PLAYLIST          = "CachedPlaylist"
}
