package com.maloy.muzza.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable

// iOS has no dynamic Material You — always use material-kolor
@Composable
actual fun platformColorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
): ColorScheme = buildMaterialKolorScheme(darkTheme, pureBlack, themeColor)
