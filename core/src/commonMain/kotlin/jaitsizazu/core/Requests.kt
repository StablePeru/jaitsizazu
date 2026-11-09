package jaitsizazu.core

/** Convert the local file at [inputPath] to [target]. */
data class ConversionRequest(
    val inputPath: String,
    val target: AudioFormat,
)

/** Download the media at [url] with the chosen [quality]. */
data class DownloadRequest(
    val url: String,
    val quality: DownloadQuality,
)

/** Quality choice for a download. Each engine adapter maps it to its own options. */
enum class DownloadQuality {
    AUDIO_ONLY,
    BEST_VIDEO,
}
