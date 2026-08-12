package com.komari.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KomariPurple = Color(0xFFA370F7)
val KomariPurpleDark = Color(0xFF7C4DDB)
val KomariPurpleLight = Color(0xFFC9A6FF)
val KomariGreen = Color(0xFF4CAF50)
val KomariRed = Color(0xFFE53935)
val KomariBlue = Color(0xFF42A5F5)

private val LightColors = lightColorScheme(
    primary = KomariPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E5FF),
    onPrimaryContainer = Color(0xFF331260),
    secondary = KomariPurpleLight,
    onSecondary = Color(0xFF331260),
    surface = Color(0xFFFDFBFF),
    background = Color(0xFFF6F3FB)
)

@Composable
fun KomariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}