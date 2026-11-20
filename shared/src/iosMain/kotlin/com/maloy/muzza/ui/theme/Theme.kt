package com.maloy.muzza.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

// iOS has no dynamic Material You — always use material-kolor
actual fun platformColorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
): ColorScheme = buildMaterialKolorScheme(darkTheme, pureBlack, themeColor)
