package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadQuality

/** Builds yt-dlp command-line arguments, without the binary itself. Pure: it runs nothing. */
object YtDlpArguments {

    /**
     * Arguments that download [url] as [quality] into [outputDirectory].
     *
     * Hardening:
     * - `--ignore-config` and `--no-plugin-dirs`: no configuration file and no
     *   plugin from the user's machine can add options, `--exec` commands or
     *   post-processors. These arguments add none either.
     * - `--no-remote-components`: yt-dlp never fetches extra code. The official
     *   binaries bundle the YouTube scripts (yt-dlp-ejs) that need it.
     * - `--no-playlist`: one video, even when the URL is also a playlist.
     * - `--no-overwrites`: an existing file is never replaced.
     * - The URL comes after `--`, so it is never read as an option.
     *
     * Every file is first written into [workDirectory], a folder of this
     * download only, and the finished file is then moved into
     * [outputDirectory], so a failed or cancelled download leaves nothing
     * behind once [workDirectory] is removed.
     *
     * yt-dlp prints the lines that [YtDlpOutput] reads: the progress, and the
     * path of the saved file once it is in [outputDirectory].
     * [deno] is the JavaScript runtime that YouTube downloads need, or null to
     * leave yt-dlp's default (deno on the PATH).
     */
    fun download(
        url: String,
        quality: DownloadQuality,
        outputDirectory: String,
        workDirectory: String,
        ffmpeg: String,
        deno: String?,
    ): List<String> =
        listOf("--ignore-config", "--no-plugin-dirs", "--no-remote-components") +
            listOf("--no-playlist", "--no-overwrites", "--encoding", "utf-8") +
            listOf("--ffmpeg-location", ffmpeg) +
            (if (deno != null) listOf("--js-runtimes", "deno:$deno") else emptyList()) +
            listOf("-P", "home:$outputDirectory", "-P", "temp:$workDirectory", "-o", OUTPUT_TEMPLATE) +
            listOf("--newline", "--progress", "--no-simulate") +
            listOf("--progress-template", "download:${YtDlpOutput.PROGRESS} $DOWNLOAD_FIELDS") +
            listOf("--progress-template", "postprocess:${YtDlpOutput.PROCESSING} $PROCESSING_FIELDS") +
            listOf("--print", "after_move:${YtDlpOutput.SAVED} %(filepath)s") +
            qualityArguments(quality) +
            listOf("--", url)

    /** Arguments that make yt-dlp replace its own binary with the latest stable release. */
    fun update(): List<String> = listOf("--ignore-config", "-U")

    private fun qualityArguments(quality: DownloadQuality): List<String> = when (quality) {
        DownloadQuality.AUDIO_MP3 ->
            listOf("-f", "bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", "2")
        // -S prefers the best video up to 1080p (or the smallest above, when
        // there is none), then MP4 video and M4A audio, which merge into MP4
        // without re-encoding.
        DownloadQuality.VIDEO_MP4 ->
            listOf("-f", "bestvideo*+bestaudio/best", "-S", "res:1080,ext:mp4:m4a") +
                listOf("--merge-output-format", "mp4", "--remux-video", "mp4")
    }

    /** The title cut to 150 bytes, so the name stays valid on every file system, and the video ID. */
    private const val OUTPUT_TEMPLATE = "%(title).150B [%(id)s].%(ext)s"

    private const val DOWNLOAD_FIELDS =
        "%(progress.downloaded_bytes)s %(progress.total_bytes)s %(progress.total_bytes_estimate)s " +
            "%(progress.speed)s %(progress.eta)s"

    private const val PROCESSING_FIELDS = "%(progress.status)s %(progress.postprocessor)s"
}
