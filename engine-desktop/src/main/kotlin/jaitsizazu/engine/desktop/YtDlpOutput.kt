package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadProgress

/**
 * Reads the lines that [YtDlpArguments] makes yt-dlp print. Pure: it runs nothing.
 *
 * - `jaitsizazu-progress <downloaded> <total> <estimated total> <bytes/s> <eta s>`,
 *   with `NA` for a value yt-dlp does not know;
 * - `jaitsizazu-processing <status> <postprocessor>`;
 * - `jaitsizazu-saved <path of the saved file>`.
 *
 * Every other line is yt-dlp's own and is ignored. yt-dlp may print these
 * lines on stdout or on stderr: with `--print` it runs in quiet mode, which
 * sends the post-processing progress to stderr.
 */
object YtDlpOutput {
    /** Every line of ours starts with this. */
    const val PREFIX = "jaitsizazu-"
    const val PROGRESS = "jaitsizazu-progress"
    const val PROCESSING = "jaitsizazu-processing"
    const val SAVED = "jaitsizazu-saved"

    /** The progress in [line], or null when the line holds none. */
    fun progress(line: String): DownloadProgress? {
        if (line.startsWith("$PROCESSING ")) return DownloadProgress.Processing
        if (!line.startsWith("$PROGRESS ")) return null
        val fields = line.removePrefix("$PROGRESS ").split(' ')
        if (fields.size != 5) return null

        val (downloaded, total, estimate, speed, eta) = fields.map { it.toDoubleOrNull() }
        val size = (total ?: estimate)?.takeIf { it > 0 }
        val percent = if (downloaded != null && size != null) (downloaded / size * 100).coerceIn(0.0, 100.0) else null
        return DownloadProgress.Downloading(percent, speed, eta?.toLong())
    }

    /** The path of the saved file in [line], or null when the line names none. */
    fun savedFile(line: String): String? =
        line.takeIf { it.startsWith("$SAVED ") }?.removePrefix("$SAVED ")?.takeIf { it.isNotBlank() }
}
