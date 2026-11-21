# Muzza for iOS

The iOS app uses the shared InnerTube client and an iOS SQLDelight database. Home, Explore, search, collection pages, playback, YouTube radio queues, local playlists, saved items, listening history, and synchronized lyrics use live data rather than sample cards. Local playlists can be created, renamed, reordered, and deleted.

## Build and verification

Use macOS on Apple silicon with Xcode, an iOS simulator, and JDK 21. Open `iosApp.xcodeproj` and build the `iosApp` scheme; its build phase compiles the shared Kotlin framework. The iOS GitHub Actions workflow runs the transport and library simulator tests, validates the Swift media helpers, launches the simulator app, and builds a signed IPA when signing credentials are configured.

## Account and offline audio

YouTube sign-in uses an isolated web view; the session is verified and stored in the device Keychain. Google may reject sign-in in an embedded web view, in which case a session cookie from the user's own account can be entered on the device. Never put cookies in source code or share them with another person.

The YouTube Music library can be browsed separately from the on-device library. Liked-song changes can be sent to YouTube when the account sync setting is enabled. Album, artist, and playlist saves currently remain on the device.

Downloads are stored in the app's Application Support directory and are available without a network connection. Keep the app open during a download; background download scheduling is not implemented. Lyrics are requested from LRCLIB or KuGou when the lyrics screen is opened and are cached locally.

The iOS version does not yet include every Android feature, including background downloads, full two-way library synchronization, ListenTogether, Spotify import, Discord integration, and advanced audio effects.
