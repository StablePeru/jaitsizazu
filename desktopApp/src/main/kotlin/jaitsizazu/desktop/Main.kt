package jaitsizazu.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import jaitsizazu.core.ConvertMedia
import jaitsizazu.core.DownloadMedia
import jaitsizazu.engine.desktop.ExternalTool
import jaitsizazu.engine.desktop.FfmpegConverter
import jaitsizazu.engine.desktop.SystemProcessRunner
import jaitsizazu.engine.desktop.ToolLocator
import jaitsizazu.engine.desktop.ToolLookup
import jaitsizazu.engine.desktop.YtDlpDownloader
import jaitsizazu.engine.desktop.YtDlpUpdater
import jaitsizazu.ui.AppLanguage
import jaitsizazu.ui.ConverterScreen
import jaitsizazu.ui.ConverterStateHolder
import jaitsizazu.ui.DownloaderScreen
import jaitsizazu.ui.DownloaderStateHolder
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.choose_file_title
import jaitsizazu.ui.resources.choose_folder_approve
import jaitsizazu.ui.resources.choose_folder_title
import jaitsizazu.ui.resources.tab_convert
import jaitsizazu.ui.resources.tab_download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import java.awt.FileDialog
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.UIManager

const val APP_NAME = "Jaitsizazu"

/** The external tools, each looked for on every use (see [ToolLocator]). */
private class Tools(appDirectory: Path?) {
    val ffmpeg = ToolLocator(ExternalTool.FFMPEG, appDirectory = appDirectory)
    val ytDlp = ToolLocator(ExternalTool.YT_DLP, appDirectory = appDirectory)
    val deno = ToolLocator(ExternalTool.DENO, appDirectory = appDirectory)
}

/**
 * Container: wires the desktop engine into the shared UI, in two tabs.
 *
 * Every text is in [AppLanguage.DEFAULT], Basque, whatever the language of the
 * system. The tools are looked for on every conversion and download. The checks
 * at startup only warn early, never block, and run off the UI thread like the
 * work itself. Uncaught exceptions go to `crash.log` in [logDirectory] and to
 * an error dialog.
 */
fun main() {
    useAppLanguage(AppLanguage.DEFAULT)
    val crashLog = logDirectory().resolve("crash.log")
    // Read once now, so the crash handler never reads resources. If even this fails, English.
    val crashTexts = runCatching { runBlocking { loadCrashTexts(crashLog) } }.getOrElse { CrashTexts.fallback(crashLog) }
    Thread.setDefaultUncaughtExceptionHandler(CrashReporter(crashLog, ::showErrorDialog, crashTexts))
    // Native-looking Swing dialogs (the folder chooser, the error dialog). Optional.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val tools = Tools(appDirectory())
    val runner = SystemProcessRunner()
    val convertMedia = ConvertMedia(FfmpegConverter(tools.ffmpeg::locate, runner)) { File(it).exists() }
    val downloadMedia = DownloadMedia(
        YtDlpDownloader(tools.ytDlp::locate, tools.ffmpeg::locate, tools.deno::locate, runner),
    ) { File(it).isDirectory }
    val updater = YtDlpUpdater(tools.ytDlp::locate, runner)
    val defaultFolder = defaultDownloadDirectory().toString()

    application {
        val scope = rememberCoroutineScope()
        val converter = remember { ConverterStateHolder(convertMedia, scope, workContext = Dispatchers.IO) }
        val downloader = remember {
            DownloaderStateHolder(downloadMedia, updater, scope, defaultFolder, workContext = Dispatchers.IO)
        }

        LaunchedEffect(Unit) {
            val (ffmpeg, ytDlp, deno) = withContext(Dispatchers.IO) {
                listOf(tools.ffmpeg.locate(), tools.ytDlp.locate(), tools.deno.locate())
            }
            converter.showSetupWarnings(listOfNotNull((ffmpeg as? ToolLookup.NotFound)?.problem))
            downloader.showSetupWarnings(downloadSetupWarnings(ytDlp, ffmpeg, deno))
        }

        Window(onCloseRequest = ::exitApplication, title = APP_NAME) {
            MaterialTheme {
                Surface {
                    AppTabs(
                        convert = {
                            val state by converter.state.collectAsState()
                            val title = stringResource(Res.string.choose_file_title)
                            ConverterScreen(
                                state = state,
                                onChooseFile = { chooseFile(window, title)?.let(converter::selectFile) },
                                onFormatSelected = converter::selectFormat,
                                onConvert = converter::convert,
                                onCancel = converter::cancel,
                            )
                        },
                        download = {
                            val state by downloader.state.collectAsState()
                            val title = stringResource(Res.string.choose_folder_title)
                            val approve = stringResource(Res.string.choose_folder_approve)
                            DownloaderScreen(
                                state = state,
                                onUrlChange = downloader::setUrl,
                                onQualitySelected = downloader::selectQuality,
                                onChooseFolder = {
                                    chooseFolder(state.outputDirectory, title, approve)?.let(downloader::selectFolder)
                                },
                                onDownload = downloader::download,
                                onCancel = downloader::cancel,
                                onUpdate = downloader::updateDownloader,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** The "Convert" and "Download" tabs. Both screens keep their state while hidden: the holders own it. */
@Composable
private fun AppTabs(convert: @Composable () -> Unit, download: @Composable () -> Unit) {
    var selected by remember { mutableStateOf(0) }
    Column {
        TabRow(selectedTabIndex = selected) {
            Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text(stringResource(Res.string.tab_convert)) })
            Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text(stringResource(Res.string.tab_download)) })
        }
        if (selected == 0) convert() else download()
    }
}

/**
 * The folder that holds the installed app launcher, where `bin/` with the tools may live.
 * jpackage sets `jpackage.app-path`; when running from Gradle it is absent.
 */
private fun appDirectory(): Path? =
    System.getProperty("jpackage.app-path")?.let { Path.of(it).toAbsolutePath().parent }

/** Shows [message] in a modal Swing dialog. [CrashReporter] calls it on the event thread. Swing does not need Skiko. */
private fun showErrorDialog(message: String) {
    if (GraphicsEnvironment.isHeadless()) return
    JOptionPane.showMessageDialog(null, message, APP_NAME, JOptionPane.ERROR_MESSAGE)
}

/** Shows the native file dialog, titled [title]. Returns the chosen path, or null if cancelled. */
private fun chooseFile(parent: Frame, title: String): String? {
    val dialog = FileDialog(parent, title, FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file ?: return null
    return File(dialog.directory, name).path
}

/**
 * Shows a folder chooser that starts in [current], titled [title], with [approve]
 * on its button. Returns the chosen folder, or null if cancelled.
 * Swing's chooser, because the native AWT dialog cannot pick folders on Windows.
 */
private fun chooseFolder(current: String, title: String, approve: String): String? {
    val chooser = JFileChooser(current.takeIf { it.isNotBlank() }?.let(::File)).apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showDialog(null, approve) == JFileChooser.APPROVE_OPTION) chooser.selectedFile?.path else null
}
