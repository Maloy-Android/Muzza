package com.maloy.muzza.ui.util

/**
 * KMP string resources.
 * On Android these delegate to R.string via actual implementations.
 * On iOS they return hardcoded English strings (localization via .strings files later).
 */
expect object MuzzaStrings {
    val home: String
    val explore: String
    val library: String
    val search: String
    val settings: String
    val artists: String
    val albums: String
    val playlists: String
    val songs: String
    val history: String
    val queue: String
    val lyrics: String
    val download: String
    val shuffle: String
    val addToPlaylist: String
    val addToQueue: String
    val playNext: String
    val like: String
    val unlike: String
    val share: String
    val delete: String
    val rename: String
    val edit: String
    val cancel: String
    val confirm: String
    val ok: String
    val error: String
    val loading: String
    val noResults: String
    val nowPlaying: String
    val pause: String
    val play: String
    val next: String
    val previous: String
    val repeat: String
    val repeatOne: String
    val account: String
    val signIn: String
    val signOut: String
    val appearance: String
    val language: String
    val storage: String
    val backup: String
    val about: String
    val version: String
}
