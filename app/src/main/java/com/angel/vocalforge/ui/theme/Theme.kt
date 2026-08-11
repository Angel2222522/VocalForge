package com.angel.vocalforge.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VocalForgeColors = darkColorScheme(
    primary = Color(0xFF55E0C1),
    onPrimary = Color(0xFF002019),
    secondary = Color(0xFFB9A8FF),
    onSecondary = Color(0xFF21194E),
    background = Color(0xFF08090C),
    onBackground = Color(0xFFF2F1F7),
    surface = Color(0xFF11131A),
    onSurface = Color(0xFFF2F1F7),
    surfaceVariant = Color(0xFF191C25),
    onSurfaceVariant = Color(0xFFAAAAB7),
    outline = Color(0xFF373A47),
    error = Color(0xFFFF8A9B)
)

@Composable
fun VocalForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VocalForgeColors, content = content)
}
