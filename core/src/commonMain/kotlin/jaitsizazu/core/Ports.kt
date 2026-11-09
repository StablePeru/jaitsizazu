package jaitsizazu.core

import kotlin.coroutines.cancellation.CancellationException

/** Outcome of an engine operation. */
sealed interface MediaResult {
    /** The operation wrote its output to [outputPath]. */
    data class Success(val outputPath: String) : MediaResult

    /** The operation failed. [reason] is meant for the user. */
    data class Failure(val reason: String) : MediaResult
}

/**
 * Port: converts local media files. Adapters wrap ffmpeg.
 *
 * [convert] writes to [outputPath], chosen by the caller. It must fail rather
 * than overwrite a file that already exists there.
 */
interface MediaConverter {
    suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult
}

/** How far a running download is. */
sealed interface DownloadProgress {
    /**
     * Bytes are arriving. [percent] goes from 0 to 100. Each value is null
     * while the engine does not know it.
     */
    data class Downloading(
        val percent: Double?,
        val bytesPerSecond: Double?,
        val etaSeconds: Long?,
    ) : DownloadProgress

    /** The data is in; the engine is converting, merging or moving the file. */
    data object Processing : DownloadProgress
}

/**
 * Port: downloads media from a URL. Adapters wrap yt-dlp.
 *
 * [download] saves into the request's output folder and returns the path of
 * the saved file. It must never overwrite a file that is already there.
 * [onProgress] may be called from any thread.
 *
 * When cancelled, [download] stops its work and removes its partial files
 * before the cancellation goes on. If some cannot be removed, it throws
 * [DownloadCancelledWithLeftovers] instead of the plain cancellation.
 */
interface MediaDownloader {
    suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult
}

/**
 * A cancelled download that could not remove all its temporary files: they
 * are left in [folder], for the user to delete. It is still a cancellation, so
 * coroutines treat it as one.
 */
class DownloadCancelledWithLeftovers(val folder: String) :
    CancellationException("The download was cancelled, but $folder could not be removed.")

/** Outcome of [DownloaderUpdater.update]. Both texts are meant for the user. */
sealed interface UpdateResult {
    data class Updated(val message: String) : UpdateResult
    data class Failed(val reason: String) : UpdateResult
}

/**
 * Port: updates the download engine in place, because sites such as YouTube
 * change often and an old engine stops working with them.
 */
interface DownloaderUpdater {
    suspend fun update(): UpdateResult
}
