package com.gigawalljfl.geolock.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NeonCyan = Color(0xFF00F0FF)
val Magenta = Color(0xFFFF007F)
val MatrixGreen = Color(0xFF00E676)
val VoidBlack = Color(0xFF0A0F14)
val PanelDark = Color(0xFF10161C)
val AmberWarn = Color(0xFFFFB020)

private val GeoLockColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = Magenta,
    tertiary = MatrixGreen,
    background = VoidBlack,
    surface = PanelDark,
    error = Magenta,
    onPrimary = VoidBlack,
    onSecondary = VoidBlack,
    onBackground = NeonCyan,
    onSurface = NeonCyan
)

@Composable
fun GeoLockTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GeoLockColorScheme,
        typography = GeoLockTypography,
        content = content
    )
}
