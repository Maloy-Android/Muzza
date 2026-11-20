package com.maloy.muzza.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable

@Composable
actual fun platformColorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
): ColorScheme {
    val context = LocalContext.current
    return if (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (darkTheme && pureBlack) {
            scheme.copy(surface = Color.Black, background = Color.Black)
        } else {
            scheme
        }
    } else {
        buildMaterialKolorScheme(darkTheme, pureBlack, themeColor)
    }
}
