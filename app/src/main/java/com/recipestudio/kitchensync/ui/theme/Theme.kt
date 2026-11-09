package com.recipestudio.kitchensync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFCF5A3A),
    background = Color(0xFFF3EFE7),
    surface = Color(0xFFF8F4EE),
    onBackground = Color(0xFF1E1D1A),
    onSurface = Color(0xFF1E1D1A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCF5A3A),
    background = Color(0xFF17171A),
    surface = Color(0xFF1F1F22),
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFFFFFFF)
)

@Composable
fun RecipeStudioTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}
