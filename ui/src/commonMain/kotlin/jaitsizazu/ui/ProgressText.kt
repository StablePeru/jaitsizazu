package jaitsizazu.ui

import androidx.compose.runtime.Composable
import jaitsizazu.core.DownloadProgress
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.decimal_separator
import jaitsizazu.ui.resources.progress_downloading
import jaitsizazu.ui.resources.progress_finishing
import jaitsizazu.ui.resources.progress_percent
import jaitsizazu.ui.resources.progress_starting
import jaitsizazu.ui.resources.progress_time_left
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * The words [progressText] uses, in one language. [percent] and [timeLeft]
 * put a number or a time into its sentence, such as `42%` or `%42`.
 */
data class ProgressWords(
    val starting: String,
    val downloading: String,
    val finishing: String,
    val percent: (String) -> String,
    val timeLeft: (String) -> String,
    val decimalSeparator: String = ".",
)

/** The [ProgressWords] of the app's language, from the string resources. */
@Composable
fun progressWords(): ProgressWords {
    val percent = stringResource(Res.string.progress_percent)
    val timeLeft = stringResource(Res.string.progress_time_left)
    return ProgressWords(
        starting = stringResource(Res.string.progress_starting),
        downloading = stringResource(Res.string.progress_downloading),
        finishing = stringResource(Res.string.progress_finishing),
        percent = { fillTemplate(percent, it) },
        timeLeft = { fillTemplate(timeLeft, it) },
        decimalSeparator = stringResource(Res.string.decimal_separator),
    )
}

/** Puts [value] where [template] has `%1$s`, as Compose resources do with their arguments. */
internal fun fillTemplate(template: String, value: String): String = template.replace("%1\$s", value)

/** One short line about [progress] for the user, such as `42% · 1.5 MB/s · 1:05 left`. */
fun progressText(progress: DownloadProgress?, words: ProgressWords): String = when (progress) {
    null -> words.starting
    DownloadProgress.Processing -> words.finishing
    is DownloadProgress.Downloading -> listOfNotNull(
        progress.percent?.let { words.percent("${floor(it).toInt()}") },
        progress.bytesPerSecond?.let { "${formatBytes(it, words.decimalSeparator)}/s" },
        progress.etaSeconds?.let { words.timeLeft(formatDuration(it)) },
    ).joinToString(" · ").ifEmpty { words.downloading }
}

/** [bytes] in B, KB, MB, GB or TB (powers of 1024), with one decimal above bytes. */
fun formatBytes(bytes: Double, decimalSeparator: String = "."): String {
    var value = bytes
    var unit = 0
    while (value >= 1024 && unit < BYTE_UNITS.lastIndex) {
        value /= 1024
        unit++
    }
    if (unit == 0) return "${value.roundToLong()} B"
    val tenths = (value * 10).roundToLong()
    return "${tenths / 10}$decimalSeparator${tenths % 10} ${BYTE_UNITS[unit]}"
}

/** [seconds] as `m:ss`, or `h:mm:ss` from one hour on. */
fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    val secs = (seconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$secs" else "$minutes:$secs"
}

private val BYTE_UNITS = listOf("B", "KB", "MB", "GB", "TB")
