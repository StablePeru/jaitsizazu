package jaitsizazu.ui

import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.Leftover
import jaitsizazu.core.Problem

/**
 * Everything the downloader screen shows. Plain Kotlin, no Compose types and no
 * texts: the screen puts problems into words in the app's language.
 *
 * [setupWarnings] holds what a startup check found (for example, yt-dlp is
 * missing); the screen explains how to fix each. Like the converter's, they do
 * not block downloading: the engine looks again on every download.
 *
 * On Android the download engine is unpacked on the first start. While
 * [preparing], nothing can be downloaded or updated yet. When that setup
 * failed, [setupFailure] is the reason, shown instead of downloading or
 * updating. The desktop app has no such setup: it is never preparing.
 */
data class DownloaderState(
    val url: String = "",
    val quality: DownloadQuality = DownloadQuality.AUDIO_MP3,
    val outputDirectory: String = "",
    val status: DownloadStatus = DownloadStatus.Idle,
    val update: UpdateStatus = UpdateStatus.Idle,
    val setupWarnings: List<Problem> = emptyList(),
    val preparing: Boolean = false,
    val setupFailure: Problem? = null,
) {
    /**
     * A download runs or is still stopping, or an update runs. They never run
     * together: an update replaces the engine.
     */
    val isBusy: Boolean
        get() = status is DownloadStatus.Downloading || status == DownloadStatus.Cancelling ||
            update == UpdateStatus.Updating

    val canDownload: Boolean
        get() = url.isNotBlank() && !isBusy && !preparing

    val canUpdate: Boolean
        get() = !isBusy && !preparing
}

sealed interface DownloadStatus {
    data object Idle : DownloadStatus

    /** [progress] is null until the engine reports some. */
    data class Downloading(val progress: DownloadProgress? = null) : DownloadStatus
    data class Done(val outputPath: String) : DownloadStatus
    /** The download failed because of [problem]; [leftover] is what it could not remove. */
    data class Failed(val problem: Problem, val leftover: Leftover? = null) : DownloadStatus

    /** The user stopped the download; the engine is still stopping yt-dlp and removing its partial files. */
    data object Cancelling : DownloadStatus

    /**
     * The download has fully stopped. [leftoverFolder] holds the temporary
     * folder the engine could not remove, or null when it removed everything.
     */
    data class Cancelled(val leftoverFolder: String? = null) : DownloadStatus
}

/** The "Update yt-dlp" action. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Updating : UpdateStatus
    /** [toolReport] is what yt-dlp said it did, as it said it. */
    data class Done(val toolReport: String) : UpdateStatus

    /** yt-dlp ended without an error but did not say which version it has; [lastLine] is its last line, raw. */
    data class Unclear(val lastLine: String?) : UpdateStatus

    data class Failed(val problem: Problem) : UpdateStatus
}
