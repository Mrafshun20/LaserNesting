package com.mrafshun20.lasernesting.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/** Windows entry point: iBebels laser sheet nesting. */
fun main() = application {
    val state = DesktopState()
    val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "iBebels",
        icon = painterResource("ibebels_logo.png"),
        state = windowState
    ) {
        LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(1100, 700) }
        DesktopApp(state)
    }
}
