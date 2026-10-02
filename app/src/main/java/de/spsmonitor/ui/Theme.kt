package de.spsmonitor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HellesSchema = lightColorScheme(
    primary = Color(0xFF1F5F9E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F6),
    onPrimaryContainer = Color(0xFF0C2E4F),
    secondary = Color(0xFF4A6572),
    background = Color(0xFFF4F6F9),
    surface = Color.White,
    error = Color(0xFFC0392B)
)

private val DunklesSchema = darkColorScheme(
    primary = Color(0xFF8FC0EC),
    onPrimary = Color(0xFF0C2E4F),
    primaryContainer = Color(0xFF26486B),
    onPrimaryContainer = Color(0xFFD3E4F6),
    background = Color(0xFF131A21),
    surface = Color(0xFF1C242D),
    error = Color(0xFFEF8278)
)

@Composable
fun SpsMonitorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DunklesSchema else HellesSchema,
        content = content
    )
}
