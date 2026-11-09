package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloaderUpdater
import jaitsizazu.core.UpdateResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop adapter for [DownloaderUpdater]: runs `yt-dlp -U`, which replaces the
 * yt-dlp binary with the latest stable release, in place.
 *
 * It updates exactly the binary that downloads use, because both ask the same
 * [locateYtDlp]. yt-dlp writes the new binary next to the old one, so the file
 * and its folder must both be writable; when one is not (a system-wide install,
 * for example), the update is refused with an explanation, before yt-dlp runs.
 */
class YtDlpUpdater(
    private val locateYtDlp: () -> ToolLookup,
    private val runner: ProcessRunner,
    private val isWritable: (Path) -> Boolean = Files::isWritable,
) : DownloaderUpdater {

    override suspend fun update(): UpdateResult {
        val binary = when (val lookup = locateYtDlp()) {
            is ToolLookup.Found -> Path.of(lookup.path)
            is ToolLookup.NotFound -> return UpdateResult.Failed(lookup.message)
        }
        val folder = binary.toAbsolutePath().parent
        if (!isWritable(binary) || folder == null || !isWritable(folder)) {
            return UpdateResult.Failed(
                "yt-dlp at $binary is read-only for your user, so Jaitsizazu cannot update it. " +
                    "Update it the way it was installed (for example, with your package manager), " +
                    "or put a copy you can write to in the app's bin folder.",
            )
        }

        val output = ArrayDeque<String>() // The runner calls back one line at a time.
        val outcome = try {
            runner.run(listOf(binary.toString()) + YtDlpArguments.update()) { line ->
                if (line.isNotBlank()) {
                    if (output.size == KEPT_LINES) output.removeFirst()
                    output.addLast(line.trim())
                }
            }
        } catch (e: IOException) {
            return UpdateResult.Failed("Could not start yt-dlp ($binary): ${e.message}")
        }

        if (outcome.exitCode == 0) {
            // The runner mixes stdout and stderr, so a warning may come after yt-dlp's own result line.
            val result = output.lastOrNull { it.startsWith("Updated yt-dlp") || it.startsWith("yt-dlp is up to date") }
            return UpdateResult.Updated(result ?: output.lastOrNull() ?: "yt-dlp is up to date.")
        }
        val details = outcome.stderrTail.ifBlank { output.joinToString("\n") }
        val summary = "yt-dlp could not update itself (exit code ${outcome.exitCode})."
        return UpdateResult.Failed(if (details.isBlank()) summary else "$summary\n$details")
    }

    private companion object {
        const val KEPT_LINES = 5
    }
}
