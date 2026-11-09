package jaitsizazu.core

/** Audio formats the converter can produce. [extension] has no leading dot. */
enum class AudioFormat(val extension: String) {
    WAV("wav"),
    MP3("mp3"),
    FLAC("flac"),
    OGG_VORBIS("ogg"),
}
