package jaitsizazu.engine.desktop

import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * An external program the desktop engine runs, as the domain [Tool] it is.
 * [envVar] may hold its full path.
 */
enum class ExternalTool(val tool: Tool, val envVar: String) {
    FFMPEG(Tool.FFMPEG, "JAITSIZAZU_FFMPEG"),
    YT_DLP(Tool.YT_DLP, "JAITSIZAZU_YTDLP"),
    DENO(Tool.DENO, "JAITSIZAZU_DENO"),
    ;

    /** The binary name without any extension, as users know it (`ffmpeg`, not `ffmpeg.exe`). */
    val binaryName: String get() = tool.displayName
}

/** Where a tool is, or why it was not found. */
sealed interface ToolLookup {
    data class Found(val path: String) : ToolLookup

    /**
     * [problem] is [Problem.ToolNotFound] or [Problem.ToolNotAtConfiguredPath]:
     * the UI turns it into words that say how to provide the tool.
     */
    data class NotFound(val problem: Problem) : ToolLookup
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
    /** The file to look for: [ExternalTool.binaryName], plus `.exe` on Windows. */
    private val fileName = if (isWindows) "${tool.binaryName}.exe" else tool.binaryName

    fun locate(): ToolLookup {
        val configured = environment(tool.envVar)?.trim()
        if (!configured.isNullOrEmpty()) {
            val path = toPath(configured)
            return if (path != null && isExecutableFile(path)) {
                ToolLookup.Found(path.toString())
            } else {
                ToolLookup.NotFound(Problem.ToolNotAtConfiguredPath(tool.tool, tool.envVar, configured))
            }
        }

        val candidates = listOfNotNull(appDirectory?.resolve("bin")?.resolve(fileName)) +
            pathEntries().map { it.resolve(fileName) }
        val found = candidates.firstOrNull(isExecutableFile)
            ?: return ToolLookup.NotFound(notFound())
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

    private fun notFound(): Problem {
        val bundled = if (isWindows) "bin\\$fileName" else "bin/$fileName"
        return Problem.ToolNotFound(tool.tool, tool.envVar, bundled)
    }
}
