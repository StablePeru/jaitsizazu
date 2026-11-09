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

/**
 * What to keep from a download. Each engine adapter maps it to its own options.
 * The entries go from the most compatible file to the best quality.
 */
enum class DownloadQuality {
    /** The audio only, as an MP3 file: plays everywhere. */
    AUDIO_MP3,

    /** The audio only, as a WAV file: no further loss, ready for editing. */
    AUDIO_WAV,

    /**
     * The audio only, as the site serves it, without re-encoding: usually an
     * Opus (`.opus`) or AAC (`.m4a`) file.
     */
    AUDIO_ORIGINAL,

    /** The video with its audio, at most 1080p high, as an MP4 file. */
    VIDEO_MP4,

    /**
     * The video with its audio at the best quality, up to 4K (2160p): MP4 when
     * the codecs allow it, otherwise MKV. Files can be very large.
     */
    VIDEO_BEST,
}
