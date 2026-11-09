package jaitsizazu.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import jaitsizazu.core.ConvertMedia
import jaitsizazu.engine.desktop.FfmpegConverter
import jaitsizazu.engine.desktop.FfmpegLocator
import jaitsizazu.engine.desktop.FfmpegLookup
import jaitsizazu.engine.desktop.SystemProcessRunner
import jaitsizazu.ui.ConverterScreen
import jaitsizazu.ui.ConverterStateHolder
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path

const val APP_NAME = "Jaitsizazu"

/** Container: wires the desktop engine into the shared UI. */
fun main() {
    val locator = FfmpegLocator(appDirectory = appDirectory())
    val setupProblem = (locator.locate() as? FfmpegLookup.NotFound)?.message
    val convertMedia = ConvertMedia(FfmpegConverter(locator::locate, SystemProcessRunner())) { File(it).exists() }

    application {
        val scope = rememberCoroutineScope()
        val holder = remember { ConverterStateHolder(convertMedia, scope, setupProblem) }
        val state by holder.state.collectAsState()

        Window(onCloseRequest = ::exitApplication, title = APP_NAME) {
            MaterialTheme {
                Surface {
                    ConverterScreen(
                        state = state,
                        onChooseFile = { chooseFile(window)?.let(holder::selectFile) },
                        onFormatSelected = holder::selectFormat,
                        onConvert = holder::convert,
                    )
                }
            }
        }
    }
}

/**
 * The folder that holds the installed app launcher, where `bin/ffmpeg` may live.
 * jpackage sets `jpackage.app-path`; when running from Gradle it is absent.
 */
private fun appDirectory(): Path? =
    System.getProperty("jpackage.app-path")?.let { Path.of(it).toAbsolutePath().parent }

/** Shows the native file dialog. Returns the chosen path, or null if cancelled. */
private fun chooseFile(parent: Frame): String? {
    val dialog = FileDialog(parent, "Choose an audio file", FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name).path
}
