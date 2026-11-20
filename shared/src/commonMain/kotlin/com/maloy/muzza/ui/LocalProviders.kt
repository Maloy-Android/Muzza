package com.maloy.muzza.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.maloy.muzza.data.repository.MusicRepository
import com.maloy.muzza.presentation.viewmodel.PlayerViewModel

/**
 * CompositionLocals used throughout the app UI.
 * These replace the Android-specific Hilt injection in Compose screens.
 */

val LocalDatabase = staticCompositionLocalOf<MusicRepository> {
    error("No MusicRepository provided")
}

val LocalPlayerViewModel = staticCompositionLocalOf<PlayerViewModel> {
    error("No PlayerViewModel provided")
}

// Window insets placeholder — actual values provided per platform
val LocalPlayerAwareWindowInsets = compositionLocalOf<androidx.compose.foundation.layout.WindowInsets> {
    androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0)
}
