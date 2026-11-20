package com.maloy.muzza.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.SchemeTonalSpot

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MuzzaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(darkTheme, pureBlack, themeColor) {
        // On Android with dynamic color support, platform provides the scheme.
        // On iOS (or when themeColor != default), we always use material-kolor.
        platformColorScheme(darkTheme, pureBlack, themeColor)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content,
    )
}

/** Platform provides dynamic color on Android S+; falls back to material-kolor on iOS. */
expect fun platformColorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
): ColorScheme

fun buildMaterialKolorScheme(
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
): ColorScheme =
    SchemeTonalSpot(Hct.fromInt(themeColor.toArgb()), darkTheme, 0.0)
        .toColorScheme()
        .pureBlack(darkTheme && pureBlack)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(surface = Color.Black, background = Color.Black) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
