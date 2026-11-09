package jaitsizazu.core

/** Outcome of an engine operation. */
sealed interface MediaResult {
    /** The operation wrote its output to [outputPath]. */
    data class Success(val outputPath: String) : MediaResult

    /** The operation failed. [reason] is meant for the user. */
    data class Failure(val reason: String) : MediaResult
}

/** Port: converts local media files. Adapters wrap ffmpeg. */
interface MediaConverter {
    suspend fun convert(request: ConversionRequest): MediaResult
}

/** Port: downloads media from a URL. Adapters wrap yt-dlp. */
interface MediaDownloader {
    suspend fun download(request: DownloadRequest): MediaResult
}
