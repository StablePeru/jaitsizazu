package jaitsizazu.engine.desktop

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * An external program the desktop engine runs. [envVar] may hold its full path,
 * and [purpose] completes "Jaitsizazu needs it to ..." in the not-found message.
 */
enum class ExternalTool(val binaryName: String, val envVar: String, val purpose: String) {
    FFMPEG("ffmpeg", "JAITSIZAZU_FFMPEG", "convert files and to finish downloads"),
    YT_DLP("yt-dlp", "JAITSIZAZU_YTDLP", "download"),
    DENO("deno", "JAITSIZAZU_DENO", "download from YouTube"),
}

/** Where a tool is, or why it was not found. */
sealed interface ToolLookup {
    data class Found(val path: String) : ToolLookup

    /** [message] is meant for the user and says how to provide the tool. */
    data class NotFound(val message: String) : ToolLookup
}

/**
 * Finds the binary of [tool], in this order:
 * 1. the path in the tool's environment variable, such as `JAITSIZAZU_FFMPEG`;
 * 2. `bin/<name>` (`bin\<name>.exe` on Windows) inside [appDirectory];
 * 3. `<name>` on the `PATH`.
 *
 * Every lookup is injected, so tests need no real files.
 */
class ToolLocator(
    private val tool: ExternalTool,
    private val environment: (String) -> String? = System::getenv,
    private val appDirectory: Path? = null,
    private val isWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true),
    private val isExecutableFile: (Path) -> Boolean = { Files.isRegularFile(it) && Files.isExecutable(it) },
) {
    private val binaryName = if (isWindows) "${tool.binaryName}.exe" else tool.binaryName

    fun locate(): ToolLookup {
        val configured = environment(tool.envVar)?.trim()
        if (!configured.isNullOrEmpty()) {
            val path = toPath(configured)
            return if (path != null && isExecutableFile(path)) {
                ToolLookup.Found(path.toString())
            } else {
                ToolLookup.NotFound(
                    "${tool.envVar} is set to $configured, but there is no executable ${tool.binaryName} there.",
                )
            }
        }

        val candidates = listOfNotNull(appDirectory?.resolve("bin")?.resolve(binaryName)) +
            pathEntries().map { it.resolve(binaryName) }
        val found = candidates.firstOrNull(isExecutableFile)
            ?: return ToolLookup.NotFound(notFoundMessage())
        return ToolLookup.Found(found.toString())
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
        val name = tool.binaryName
        val bundled = if (isWindows) "bin\\$binaryName" else "bin/$binaryName"
        return "$name was not found. Jaitsizazu needs it to ${tool.purpose}. " +
            "Install $name so that it is on your PATH, " +
            "or set ${tool.envVar} to the full path of the $name binary, " +
            "or put it in the app folder as $bundled. Then try again. " +
            "If you changed PATH or ${tool.envVar}, restart Jaitsizazu first."
    }
}
