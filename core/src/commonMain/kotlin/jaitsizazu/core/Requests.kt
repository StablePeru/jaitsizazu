package jaitsizazu.core

/** Convert the local file at [inputPath] to [target]. */
data class ConversionRequest(
    val inputPath: String,
    val target: AudioFormat,
)

/** Download the media at [url] with the chosen [quality] into the folder [outputDirectory]. */
data class DownloadRequest(
    val url: String,
    val quality: DownloadQuality,
    val outputDirectory: String,
)

/** What to keep from a download. Each engine adapter maps it to its own options. */
enum class DownloadQuality {
    /** The audio only, as an MP3 file. */
    AUDIO_MP3,

    /** The video with its audio, at most 1080p high, as an MP4 file. */
    VIDEO_MP4,
}
