package jaitsizazu.engine.desktop

import jaitsizazu.core.AudioFormat

/** Builds ffmpeg command-line arguments. Pure: it runs nothing. */
object FfmpegArguments {

    /**
     * Arguments that convert [inputPath] into [outputPath] as [format], without the binary itself.
     *
     * Video streams (such as cover art) are dropped. `-n` makes ffmpeg fail
     * instead of overwriting [outputPath]; the caller picks a free name first.
     */
    fun build(inputPath: String, outputPath: String, format: AudioFormat): List<String> =
        listOf("-hide_banner", "-nostdin", "-loglevel", "error", "-n") +
            listOf("-i", inputPath, "-vn") +
            codecArguments(format) +
            outputPath

    private fun codecArguments(format: AudioFormat): List<String> = when (format) {
        AudioFormat.WAV -> listOf("-c:a", "pcm_s16le")
        AudioFormat.MP3 -> listOf("-c:a", "libmp3lame", "-q:a", "2")
        AudioFormat.FLAC -> listOf("-c:a", "flac")
        AudioFormat.OGG_VORBIS -> listOf("-c:a", "libvorbis", "-q:a", "5")
    }
}
