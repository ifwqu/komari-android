package com.komari.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KomariPurple = Color(0xFFA370F7)
val KomariPurpleDark = Color(0xFF7C4DDB)
val KomariPurpleLight = Color(0xFFC9A6FF)

/** iOS 风格配色：浅灰分组背景 + 白色卡片 */
val IosBackground = Color(0xFFF2F2F7)
val IosCard = Color(0xFFFFFFFF)
val IosSeparator = Color(0xFFE5E5EA)
val IosGrayText = Color(0xFF8E8E93)
val IosBlue = Color(0xFF007AFF)

private val LightColors = lightColorScheme(
    primary = KomariPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0E5FF),
    onPrimaryContainer = Color(0xFF331260),
    secondary = KomariPurpleLight,
    onSecondary = Color(0xFF331260),
    surface = IosCard,
    onSurface = Color(0xFF1C1C1E),
    background = IosBackground,
    onBackground = Color(0xFF1C1C1E),
    surfaceVariant = IosBackground,
    onSurfaceVariant = IosGrayText,
    outline = IosSeparator
)

@Composable
fun KomariTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}