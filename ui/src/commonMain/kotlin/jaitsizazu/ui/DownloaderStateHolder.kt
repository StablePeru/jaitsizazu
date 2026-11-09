package jaitsizazu.ui

import jaitsizazu.core.DownloadCancelledWithLeftovers
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
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Holds the downloader screen state and runs downloads and engine updates in [scope].
 *
 * While a download or an update runs, every other action is ignored, except
 * [cancel] for a download. A cancelled download keeps blocking them until it
 * has fully stopped. Editing the address, the format or the folder clears the
 * previous result.
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

    /** The running download, so that [cancel] can stop it and wait for it. */
    private var activeDownload: ActiveDownload? = null

    private class ActiveDownload {
        lateinit var job: Job

        /** Set from the work context when the cancelled download left files behind; read once the job has ended. */
        @Volatile
        var leftoverFolder: String? = null
    }

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

        val download = ActiveDownload()
        activeDownload = download
        mutableState.update { it.copy(status = DownloadStatus.Downloading()) }
        download.job = scope.launch {
            val job = coroutineContext.job
            val onProgress = { progress: DownloadProgress ->
                // Both checks run inside the atomic update, so a report that arrives after
                // cancel(), after the result, or from an earlier download never replaces the status.
                mutableState.update {
                    if (job.isActive && it.status is DownloadStatus.Downloading) {
                        it.copy(status = DownloadStatus.Downloading(progress))
                    } else {
                        it
                    }
                }
            }
            val status = try {
                val result = withContext(workContext) {
                    try {
                        downloadMedia(request, onProgress)
                    } catch (e: DownloadCancelledWithLeftovers) {
                        // Read here, inside the work context: past withContext the coroutine
                        // machinery hands on the plain cancellation of this job instead.
                        download.leftoverFolder = e.folder
                        throw e
                    }
                }
                when (result) {
                    is MediaResult.Success -> DownloadStatus.Done(result.outputPath)
                    is MediaResult.Failure -> DownloadStatus.Failed(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadStatus.Failed("Unexpected error: ${e.message ?: e::class.simpleName}")
            }
            // A result that arrives after cancel() must not replace the Cancelling status.
            ensureActive()
            mutableState.update {
                // A download that worked proves the tools are there after all.
                val warning = if (status is DownloadStatus.Done) null else it.setupWarning
                it.copy(status = status, setupWarning = warning)
            }
        }
    }

    /**
     * Stops the running download, if any. The status turns to Cancelling at
     * once and stays there, blocking every other action, until the download job
     * has ended: the engine has stopped yt-dlp and removed its partial files.
     * Only then can an update replace the yt-dlp binary, which Windows refuses
     * while yt-dlp still runs. The status then turns to Cancelled, naming any
     * temporary folder the engine could not remove.
     */
    fun cancel() {
        if (mutableState.value.status !is DownloadStatus.Downloading) return
        val download = activeDownload ?: return
        activeDownload = null
        mutableState.update { it.copy(status = DownloadStatus.Cancelling) }
        download.job.cancel()
        scope.launch {
            download.job.join()
            mutableState.update {
                if (it.status == DownloadStatus.Cancelling) it.copy(status = DownloadStatus.Cancelled(download.leftoverFolder)) else it
            }
        }
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
