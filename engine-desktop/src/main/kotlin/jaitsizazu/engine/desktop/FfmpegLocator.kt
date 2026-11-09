package jaitsizazu.engine.desktop

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/** Where ffmpeg is, or why it was not found. */
sealed interface FfmpegLookup {
    data class Found(val path: String) : FfmpegLookup

    /** [message] is meant for the user and says how to provide ffmpeg. */
    data class NotFound(val message: String) : FfmpegLookup
}

/**
 * Finds the ffmpeg binary, in this order:
 * 1. the path in the [ENV_VAR] environment variable;
 * 2. `bin/ffmpeg` (`bin\ffmpeg.exe` on Windows) inside [appDirectory];
 * 3. `ffmpeg` on the `PATH`.
 *
 * Every lookup is injected, so tests need no real files.
 */
class FfmpegLocator(
    private val environment: (String) -> String? = System::getenv,
    private val appDirectory: Path? = null,
    private val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
    private val isExecutableFile: (Path) -> Boolean = { Files.isRegularFile(it) && Files.isExecutable(it) },
) {
    private val binaryName = if (isWindows) "ffmpeg.exe" else "ffmpeg"

    fun locate(): FfmpegLookup {
        val configured = environment(ENV_VAR)?.trim()
        if (!configured.isNullOrEmpty()) {
            val path = toPath(configured)
            return if (path != null && isExecutableFile(path)) {
                FfmpegLookup.Found(path.toString())
            } else {
                FfmpegLookup.NotFound("$ENV_VAR is set to $configured, but there is no executable ffmpeg there.")
            }
        }

        val candidates = listOfNotNull(appDirectory?.resolve("bin")?.resolve(binaryName)) +
            pathEntries().map { it.resolve(binaryName) }
        val found = candidates.firstOrNull(isExecutableFile)
            ?: return FfmpegLookup.NotFound(notFoundMessage())
        return FfmpegLookup.Found(found.toString())
    }

    private fun pathEntries(): List<Path> {
        val separator = if (isWindows) ';' else ':'
        return environment("PATH").orEmpty()
            .split(separator)
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .mapNotNull(::toPath)
    }

    private fun toPath(value: String): Path? =
        try {
            Path.of(value)
        } catch (_: InvalidPathException) {
            null
        }

    private fun notFoundMessage(): String {
        val bundled = if (isWindows) "bin\\ffmpeg.exe" else "bin/ffmpeg"
        return "ffmpeg was not found. Jaitsizazu needs it to convert files. " +
            "Install ffmpeg so that it is on your PATH, " +
            "or set $ENV_VAR to the full path of the ffmpeg binary, " +
            "or put it in the app folder as $bundled. Then restart Jaitsizazu."
    }

    companion object {
        const val ENV_VAR = "JAITSIZAZU_FFMPEG"
    }
}
