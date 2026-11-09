package jaitsizazu.desktop

import jaitsizazu.engine.desktop.ToolLookup
import java.nio.file.Files
import java.nio.file.Path

/**
 * Where downloads go until the user picks another folder: `Downloads` in [home],
 * or [home] itself when there is no such folder. On Windows a Downloads folder
 * moved elsewhere in Explorer is not followed; the user can still pick it.
 */
fun defaultDownloadDirectory(
    home: String = System.getProperty("user.home"),
    isDirectory: (Path) -> Boolean = Files::isDirectory,
): Path {
    val downloads = Path.of(home, "Downloads")
    return if (isDirectory(downloads)) downloads else Path.of(home)
}

/**
 * The startup warning of the download screen: the message of every tool that
 * was not found, or null when all are there. Each message already says what
 * the tool is needed for, so a missing deno reads as a YouTube-only problem.
 */
fun downloadSetupWarning(ytDlp: ToolLookup, ffmpeg: ToolLookup, deno: ToolLookup): String? =
    listOf(ytDlp, ffmpeg, deno)
        .filterIsInstance<ToolLookup.NotFound>()
        .joinToString("\n\n") { it.message }
        .ifEmpty { null }
