package jaitsizazu.engine.desktop

import jaitsizazu.core.AudioFormat

/** Builds ffmpeg command-line arguments. Pure: it runs nothing. */
object FfmpegArguments {

    /**
     * Arguments that convert [inputPath] into [outputPath] as [format], without the binary itself.
     *
     * Video streams (such as cover art) are dropped. `-y` lets ffmpeg write
     * into [outputPath] although it exists: the caller must first claim that
     * path as a new, empty file of its own, as [FfmpegConverter] does, so no
     * file of anyone else is ever overwritten.
     * `-protocol_whitelist file` lets the input read local files only, also when
     * it is a playlist (HLS, concat) that points at other files or at URLs:
     * a crafted input cannot make ffmpeg reach the network.
     */
    fun build(inputPath: String, outputPath: String, format: AudioFormat): List<String> =
        listOf("-hide_banner", "-nostdin", "-loglevel", "error", "-y") +
            listOf("-protocol_whitelist", "file", "-i", inputPath, "-vn") +
            codecArguments(format) +
            outputPath

    private fun codecArguments(format: AudioFormat): List<String> = when (format) {
        AudioFormat.WAV -> listOf("-c:a", "pcm_s16le")
        AudioFormat.MP3 -> listOf("-c:a", "libmp3lame", "-q:a", "2")
        AudioFormat.FLAC -> listOf("-c:a", "flac")
        AudioFormat.OGG_VORBIS -> listOf("-c:a", "libvorbis", "-q:a", "5")
    }
}
