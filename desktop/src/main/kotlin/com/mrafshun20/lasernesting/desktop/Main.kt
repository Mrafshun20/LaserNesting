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
    primary = Color(0xFFB89B5E),
    onPrimary = Color(0xFF141210),
    primaryContainer = Color(0xFF3A3226),
    onPrimaryContainer = Color(0xFFD9C08A),
    secondary = Color(0xFF7A9BB5),
    background = Color(0xFF0E1113),
    onBackground = Color(0xFFE7EAEC),
    surface = Color(0xFF161B1F),
    onSurface = Color(0xFFE7EAEC),
    surfaceVariant = Color(0xFF21272C),
    onSurfaceVariant = Color(0xFF98A1A7),
    error = Color(0xFFC07A6E),
    errorContainer = Color(0xFF3A2320),
    onErrorContainer = Color(0xFFE8B8AE)
)

/** Desktop entry point. Same flows as the Android app: parts (DXF import),
 * setup (machine / sheet / language), nest (results, remnant, export). */
fun main() = application {
    val state = DesktopState()
    val windowState = rememberWindowState(size = DpSize(1100.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "ibebels",
        state = windowState
    ) {
        MaterialTheme(colorScheme = DarkColors) {
            DesktopApp(state)
        }
    }
}
