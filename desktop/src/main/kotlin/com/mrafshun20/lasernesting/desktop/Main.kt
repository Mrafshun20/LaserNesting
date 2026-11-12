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
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

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

/**
 * Where startup failures are recorded, so a launch that shows nothing
 * still leaves a trace the user can send back. Next to the autosave
 * folder, in the user's home directory.
 */
private fun startupLogFile(): File {
    val home = System.getProperty("user.home") ?: "."
    return File(File(home, "LaserNesting"), "startup-error.log")
}

private fun logStartup(message: String, error: Throwable? = null) {
    runCatching {
        val file = startupLogFile()
        file.parentFile?.mkdirs()
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val sw = StringWriter()
        if (error != null) {
            error.printStackTrace(PrintWriter(sw))
        }
        val javaVersion = System.getProperty("java.version") ?: "?"
        val os = (System.getProperty("os.name") ?: "?") +
            " " + (System.getProperty("os.version") ?: "?") +
            " " + (System.getProperty("os.arch") ?: "?")
        val renderApi = System.getProperty("skiko.renderApi") ?: "(default)"
        file.appendText(
            "[$stamp] $message\n" +
                "  java=$javaVersion os=$os renderApi=$renderApi\n" +
                (if (error != null) sw.toString() else "") +
                "\n"
        )
    }
}

/**
 * Shows a plain native message box with the startup failure, so the app
 * never dies silently. Runs on the Swing event thread; failures to show
 * it are ignored — the log file is the real record.
 */
private fun showFatalErrorDialog(logFile: File, error: Throwable) {
    runCatching {
        SwingUtilities.invokeAndWait {
            JOptionPane.showMessageDialog(
                null,
                "ibebels could not start.\n\n${error.javaClass.simpleName}: ${error.message}\n\n" +
                    "Details were written to:\n${logFile.absolutePath}",
                "ibebels — startup failed",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
}

/** Desktop entry point. Same flows as the Android app: parts (DXF import),
 * setup (machine / sheet / language), nest (results, remnant, export). */
fun main() {
    val logFile = startupLogFile()
    logStartup("main() entered")
    try {
        runApp()
    } catch (firstError: Throwable) {
        logStartup("startup failed with the default (hardware) renderer", firstError)
        // The hardware DirectX path fails on some Windows machines
        // (old Intel graphics / drivers). Retry with software rendering
        // instead of dying silently — for this 2D vector tool it is
        // fast enough and works everywhere.
        try {
            System.setProperty("skiko.renderApi", "SOFTWARE")
            logStartup("retrying with skiko.renderApi=SOFTWARE")
            runApp()
        } catch (secondError: Throwable) {
            logStartup("startup failed with software rendering too", secondError)
            showFatalErrorDialog(logFile, secondError)
            throw secondError
        }
    }
}

private fun runApp() = application {
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
