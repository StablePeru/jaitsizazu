package jaitsizazu.ui

import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality

/**
 * Everything the downloader screen shows. Plain Kotlin, no Compose types.
 *
 * [setupWarning] is set when a startup check found a problem (for example,
 * yt-dlp is missing) and explains how to fix it. Like the converter's, it does
 * not block downloading: the engine looks again on every download.
 */
data class DownloaderState(
    val url: String = "",
    val quality: DownloadQuality = DownloadQuality.AUDIO_MP3,
    val outputDirectory: String = "",
    val status: DownloadStatus = DownloadStatus.Idle,
    val update: UpdateStatus = UpdateStatus.Idle,
    val setupWarning: String? = null,
) {
    /** A download or an update runs. They never run together: an update replaces the engine. */
    val isBusy: Boolean
        get() = status is DownloadStatus.Downloading || update == UpdateStatus.Updating

    val canDownload: Boolean
        get() = url.isNotBlank() && !isBusy

    val canUpdate: Boolean
        get() = !isBusy
}

sealed interface DownloadStatus {
    data object Idle : DownloadStatus

    /** [progress] is null until the engine reports some. */
    data class Downloading(val progress: DownloadProgress? = null) : DownloadStatus
    data class Done(val outputPath: String) : DownloadStatus
    data class Failed(val reason: String) : DownloadStatus

    /** The user stopped the download; the engine removes its partial files. */
    data object Cancelled : DownloadStatus
}

/** The "Update yt-dlp" action. */
sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Updating : UpdateStatus
    data class Done(val message: String) : UpdateStatus
    data class Failed(val reason: String) : UpdateStatus
}
