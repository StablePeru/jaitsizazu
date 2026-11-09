package jaitsizazu.ui

import jaitsizazu.core.DownloadMedia
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.DownloaderUpdater
import jaitsizazu.core.MediaResult
import jaitsizazu.core.UpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Holds the downloader screen state and runs downloads and engine updates in [scope].
 *
 * While a download or an update runs, every other action is ignored, except
 * [cancel] for a download. Editing the address, the format or the folder clears
 * the previous result.
 *
 * State changes happen on [scope], which is meant to be single-threaded (the UI
 * thread), except progress: the engine reports it from its own threads, and
 * each report is one atomic state update that only applies while that same
 * download still runs. The work itself runs in [workContext]: the desktop app
 * passes `Dispatchers.IO`.
 */
class DownloaderStateHolder(
    private val downloadMedia: DownloadMedia,
    private val updater: DownloaderUpdater,
    private val scope: CoroutineScope,
    defaultOutputDirectory: String,
    private val workContext: CoroutineContext = EmptyCoroutineContext,
) {
    private val mutableState = MutableStateFlow(DownloaderState(outputDirectory = defaultOutputDirectory))
    val state: StateFlow<DownloaderState> = mutableState.asStateFlow()

    private var downloadJob: Job? = null

    private val isBusy get() = mutableState.value.isBusy

    fun setUrl(url: String) = edit { it.copy(url = url) }

    fun selectQuality(quality: DownloadQuality) = edit { it.copy(quality = quality) }

    fun selectFolder(path: String) = edit { it.copy(outputDirectory = path) }

    /** Shows [message] as a warning that does not block downloading, or clears it when null. */
    fun showSetupWarning(message: String?) {
        mutableState.update { it.copy(setupWarning = message) }
    }

    fun download() {
        val current = mutableState.value
        if (!current.canDownload) return
        val request = DownloadRequest(current.url, current.quality, current.outputDirectory)

        mutableState.update { it.copy(status = DownloadStatus.Downloading()) }
        downloadJob = scope.launch {
            val job = coroutineContext.job
            val onProgress = { progress: DownloadProgress ->
                // A report that arrives after cancel() or after the result must not bring Downloading back.
                if (job.isActive) {
                    mutableState.update {
                        if (it.status is DownloadStatus.Downloading) it.copy(status = DownloadStatus.Downloading(progress)) else it
                    }
                }
            }
            val status = try {
                when (val result = withContext(workContext) { downloadMedia(request, onProgress) }) {
                    is MediaResult.Success -> DownloadStatus.Done(result.outputPath)
                    is MediaResult.Failure -> DownloadStatus.Failed(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadStatus.Failed("Unexpected error: ${e.message ?: e::class.simpleName}")
            }
            // A result that arrives after cancel() must not replace the Cancelled status.
            ensureActive()
            mutableState.update {
                // A download that worked proves the tools are there after all.
                val warning = if (status is DownloadStatus.Done) null else it.setupWarning
                it.copy(status = status, setupWarning = warning)
            }
        }
    }

    /**
     * Stops the running download, if any. The status turns to Cancelled at once;
     * the engine stops its work and removes its partial files in the background.
     */
    fun cancel() {
        if (mutableState.value.status !is DownloadStatus.Downloading) return
        downloadJob?.cancel()
        downloadJob = null
        mutableState.update { it.copy(status = DownloadStatus.Cancelled) }
    }

    /** Updates the download engine. It cannot be cancelled: stopping halfway could break the engine. */
    fun updateDownloader() {
        if (!mutableState.value.canUpdate) return

        mutableState.update { it.copy(update = UpdateStatus.Updating) }
        scope.launch {
            val status = try {
                when (val result = withContext(workContext) { updater.update() }) {
                    is UpdateResult.Updated -> UpdateStatus.Done(result.message)
                    is UpdateResult.Failed -> UpdateStatus.Failed(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateStatus.Failed("Unexpected error: ${e.message ?: e::class.simpleName}")
            }
            mutableState.update { it.copy(update = status) }
        }
    }

    private fun edit(change: (DownloaderState) -> DownloaderState) {
        if (isBusy) return
        mutableState.update { change(it).copy(status = DownloadStatus.Idle) }
    }
}
