package jaitsizazu.ui

import jaitsizazu.core.DownloadCancelledWithLeftovers
import jaitsizazu.core.DownloadMedia
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.DownloaderUpdater
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
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
 *
 * When [preparing], the engine is still being set up (on Android): downloads
 * and updates wait until [finishSetup].
 */
class DownloaderStateHolder(
    private val downloadMedia: DownloadMedia,
    private val updater: DownloaderUpdater,
    private val scope: CoroutineScope,
    defaultOutputDirectory: String,
    private val workContext: CoroutineContext = EmptyCoroutineContext,
    preparing: Boolean = false,
) {
    private val mutableState =
        MutableStateFlow(DownloaderState(outputDirectory = defaultOutputDirectory, preparing = preparing))
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

    /** Shows [problems] as warnings that do not block downloading, or clears them when empty. */
    fun showSetupWarnings(problems: List<Problem>) {
        mutableState.update { it.copy(setupWarnings = problems) }
    }

    /**
     * Ends the engine setup: shows [warnings] like [showSetupWarnings], and
     * lets downloads and updates run. When [failure] is set, the setup failed:
     * downloading or updating shows it instead of running.
     */
    fun finishSetup(warnings: List<Problem>, failure: Problem? = null) {
        mutableState.update { it.copy(setupWarnings = warnings, preparing = false, setupFailure = failure) }
    }

    /**
     * Shows [problem] as the result of a download that could not start, for
     * example because the storage permission was denied. Ignored while busy.
     */
    fun showProblem(problem: Problem) {
        if (isBusy) return
        mutableState.update { it.copy(status = DownloadStatus.Failed(problem)) }
    }

    fun download() {
        val current = mutableState.value
        if (!current.canDownload) return
        current.setupFailure?.let { failure ->
            mutableState.update { it.copy(status = DownloadStatus.Failed(failure)) }
            return
        }
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
                    is MediaResult.Failure -> DownloadStatus.Failed(result.problem, result.leftover)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DownloadStatus.Failed(Problem.Unexpected(e.message ?: e::class.simpleName.orEmpty()))
            }
            // A result that arrives after cancel() must not replace the Cancelling status.
            ensureActive()
            mutableState.update {
                // A download that worked proves the tools are there after all.
                val warnings = if (status is DownloadStatus.Done) emptyList() else it.setupWarnings
                it.copy(status = status, setupWarnings = warnings)
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
        val current = mutableState.value
        if (!current.canUpdate) return
        current.setupFailure?.let { failure ->
            mutableState.update { it.copy(update = UpdateStatus.Failed(failure)) }
            return
        }

        mutableState.update { it.copy(update = UpdateStatus.Updating) }
        scope.launch {
            val status = try {
                when (val result = withContext(workContext) { updater.update() }) {
                    is UpdateResult.Updated -> UpdateStatus.Done(result.toolReport)
                    is UpdateResult.Unclear -> UpdateStatus.Unclear(result.lastLine)
                    is UpdateResult.Failed -> UpdateStatus.Failed(result.problem)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateStatus.Failed(Problem.Unexpected(e.message ?: e::class.simpleName.orEmpty()))
            }
            mutableState.update { it.copy(update = status) }
        }
    }

    private fun edit(change: (DownloaderState) -> DownloaderState) {
        if (isBusy) return
        mutableState.update { change(it).copy(status = DownloadStatus.Idle) }
    }
}
