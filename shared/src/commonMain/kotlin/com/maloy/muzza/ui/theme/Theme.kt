package com.maloy.muzza.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MuzzaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val colorScheme = platformColorScheme(darkTheme, pureBlack, themeColor)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

@Composable
expect fun platformColorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
): ColorScheme

fun buildMaterialKolorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
): ColorScheme {
    val scheme = if (darkTheme) {
        darkColorScheme(primary = themeColor)
    } else {
        lightColorScheme(primary = themeColor)
    }
    return if (darkTheme && pureBlack) {
        scheme.copy(surface = Color.Black, background = Color.Black)
    } else {
        scheme
    }
}
