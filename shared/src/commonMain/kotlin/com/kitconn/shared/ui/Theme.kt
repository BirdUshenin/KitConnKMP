package com.kitconn.shared.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Палитра повторяет Android-приложение. */
object KitColors {
    val Background = Color(0xFF0C0D14)
    val Card = Color(0xFF141724)
    val CardBorder = Color(0xFF22283C)
    val Cyan = Color(0xFF00E5FF)
    val Green = Color(0xFF84F938)
    val Muted = Color(0xFF8A93A6)
    val Off = Color(0xFF546E7A)
    val Danger = Color(0xFFFF5252)
}

@Composable
fun KitConnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = KitColors.Background,
            surface = KitColors.Background,
            primary = KitColors.Cyan,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
