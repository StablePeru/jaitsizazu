package jaitsizazu.android

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jaitsizazu.core.ConvertMedia
import jaitsizazu.core.DownloadMedia
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.engine.android.AndroidProcessTree
import jaitsizazu.engine.android.AndroidYtDlpUpdater
import jaitsizazu.engine.android.DownloadsPublisher
import jaitsizazu.engine.android.JobFolders
import jaitsizazu.engine.android.PUBLIC_FOLDER
import jaitsizazu.engine.android.PickedFolders
import jaitsizazu.engine.android.PublishingConverter
import jaitsizazu.engine.android.PublishingDownloader
import jaitsizazu.engine.android.YoutubeDlAndroidSetup
import jaitsizazu.engine.android.safeFileName
import jaitsizazu.engine.desktop.FfmpegConverter
import jaitsizazu.engine.desktop.JsRuntime
import jaitsizazu.engine.desktop.SystemProcessRunner
import jaitsizazu.engine.desktop.YtDlpDownloader
import jaitsizazu.ui.ConverterStateHolder
import jaitsizazu.ui.DownloaderStateHolder
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/**
 * Container: wires the Android engine into the shared state holders, which
 * live as long as this ViewModel, so a screen rotation keeps every state and
 * running job. Work runs on `Dispatchers.IO`, like on desktop.
 *
 * Every file is made in the app's cache first and then published to
 * [PUBLIC_FOLDER]: a picked file is copied in, converted there and the result
 * published; a download goes into its own job folder and is published once
 * finished. Downloads run only while the app is open: there is no foreground
 * service yet.
 */
@OptIn(ExperimentalPathApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val runtime = YoutubeDlAndroidSetup.runtime(application)
    private val jobs = JobFolders(application.cacheDir.toPath().resolve("jobs"))
    private val runner = SystemProcessRunner(
        startProcess = runtime::startProcess,
        // Android has no Process.descendants(): a cancel must still stop the ffmpeg that yt-dlp started.
        killDescendants = AndroidProcessTree()::killDescendants,
    )
    private val publisher = DownloadsPublisher(application)

    val converter = ConverterStateHolder(
        ConvertMedia(
            PublishingConverter(
                FfmpegConverter(runtime::locateFfmpeg, runner),
                publisher,
            ),
        ) { File(it).exists() },
        viewModelScope,
        workContext = Dispatchers.IO,
    )

    val downloader = DownloaderStateHolder(
        // The publishing downloader ignores the request's folder: the fixed public one is only shown.
        DownloadMedia(
            PublishingDownloader(
                YtDlpDownloader(
                    runtime::locateYtDlp,
                    runtime::locateFfmpeg,
                    runtime::locateQuickJs,
                    runner,
                    jsRuntimeName = JsRuntime.QUICKJS,
                ),
                publisher,
                createJobFolder = { jobs.newJob("download-") },
            ),
        ) { it == PUBLIC_FOLDER },
        AndroidYtDlpUpdater.create(application, runtime),
        viewModelScope,
        defaultOutputDirectory = PUBLIC_FOLDER,
        workContext = Dispatchers.IO,
        // Nothing can run before yt-dlp and Python are unpacked: see init.
        preparing = true,
    )

    /** The folder of the file selected for conversion. Used on the main thread only. */
    private val pickedFolders = PickedFolders()

    /** What went wrong while preparing the engine, shown next to a failed pick too. */
    private var engineWarnings: List<Problem> = emptyList()

    init {
        // Off the main thread: the first start unpacks Python, yt-dlp and ffmpeg.
        viewModelScope.launch {
            engineWarnings = withContext(Dispatchers.IO) {
                jobs.removeOldSessions()
                try {
                    YoutubeDlAndroidSetup.prepare(application, runtime)
                } catch (e: Exception) {
                    // Never leave the downloader waiting for a setup that ended.
                    listOf(Problem.Unexpected(e.message ?: e::class.java.simpleName))
                }
            }
            converter.showSetupWarnings(converterWarnings())
            downloader.finishSetup(engineWarnings, failure = downloadBlocker())
        }
    }

    /** The setup problem that stops every download and update: yt-dlp (with Python) is not ready, or the setup broke. */
    private fun downloadBlocker(): Problem? =
        engineWarnings.firstOrNull { it is Problem.ToolNotReadyInApp && it.tool == Tool.YT_DLP || it is Problem.Unexpected }

    /**
     * Copies the file at [uri], picked by the user, into a job folder under its
     * own (safe) name, and selects it for conversion.
     */
    fun pickFileToConvert(uri: Uri) {
        viewModelScope.launch {
            val copied = withContext(Dispatchers.IO) { runCatching { copyIn(uri) } }
            // Back on the main thread: picks are handled one at a time.
            copied.onSuccess { path ->
                converter.showSetupWarnings(converterWarnings())
                // Ignored while a conversion runs: then the new copy goes, and the running one keeps its file.
                converter.selectFile(path.toString())
                val selected = converter.state.value.selectedFile == path.toString()
                pickedFolders.picked(path.parent, selected)?.let(::removeWhenUnused)
            }.onFailure { e ->
                val problem = Problem.CannotCreateFile(uri.lastPathSegment ?: uri.toString(), e.message)
                converter.showSetupWarnings(converterWarnings() + problem)
            }
        }
    }

    private fun converterWarnings(): List<Problem> =
        engineWarnings.filter { it is Problem.ToolNotReadyInApp && it.tool == Tool.FFMPEG }

    private fun copyIn(uri: Uri): Path {
        val resolver = getApplication<Application>().contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        val folder = jobs.newJob("input-")
        val target = folder.resolve(safeFileName(name))
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Cannot read $uri")
            input.use { Files.copy(it, target) }
        } catch (e: Exception) {
            runCatching { folder.deleteRecursively() }
            throw e
        }
        return target
    }

    /**
     * Removes [folder] once the conversion started last has ended: a cancelled
     * one may still be stopping, with its input and output in that folder.
     */
    private fun removeWhenUnused(folder: Path) {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            converter.awaitConversionEnd()
            withContext(Dispatchers.IO) { runCatching { folder.deleteRecursively() } }
        }
    }
}
