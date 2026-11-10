package com.mrafshun20.lasernesting.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFC107),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF5C4300),
    onPrimaryContainer = Color(0xFFFFE082),
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF101214),
    onBackground = Color(0xFFE8EAED),
    surface = Color(0xFF1C1F22),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF262A2E),
    onSurfaceVariant = Color(0xFF9AA0A6),
    error = Color(0xFFFF5252),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFCDD2)
)

/** Desktop entry point. Same flows as the Android app: parts (DXF import),
 * setup (machine / sheet / language), nest (results, remnant, export). */
fun main() = application {
    val state = DesktopState()
    val windowState = rememberWindowState(size = DpSize(1100.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Laser Nesting",
        state = windowState
    ) {
        MaterialTheme(colorScheme = DarkColors) {
            DesktopApp(state)
        }
    }
}
