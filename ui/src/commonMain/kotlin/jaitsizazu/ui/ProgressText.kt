package jaitsizazu.ui

import jaitsizazu.core.DownloadProgress
import kotlin.math.floor
import kotlin.math.roundToLong

/** One short line about [progress] for the user, such as `42% · 1.5 MB/s · 1:05 left`. */
fun progressText(progress: DownloadProgress?): String = when (progress) {
    null -> "Starting…"
    DownloadProgress.Processing -> "Finishing the file…"
    is DownloadProgress.Downloading -> listOfNotNull(
        progress.percent?.let { "${floor(it).toInt()}%" },
        progress.bytesPerSecond?.let { "${formatBytes(it)}/s" },
        progress.etaSeconds?.let { "${formatDuration(it)} left" },
    ).joinToString(" · ").ifEmpty { "Downloading…" }
}

/** [bytes] in B, KB, MB, GB or TB (powers of 1024), with one decimal above bytes. */
fun formatBytes(bytes: Double): String {
    var value = bytes
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    if (unit == 0) return "${value.roundToLong()} B"
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}.${tenths % 10} ${BYTE_UNITS[unit]}"
}

/** [seconds] as `m:ss`, or `h:mm:ss` from one hour on. */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val secs = (seconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$secs" else "$minutes:$secs"
}

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB", "TB")
