package jaitsizazu.desktop

import jaitsizazu.core.Problem
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
 * The startup warnings of the download screen: the problem of every tool that
 * was not found, or none when all are there. The screen says what each tool is
 * needed for, so a missing deno reads as a YouTube-only problem.
 */
fun downloadSetupWarnings(ytDlp: ToolLookup, ffmpeg: ToolLookup, deno: ToolLookup): List<Problem> =
    listOf(ytDlp, ffmpeg, deno).filterIsInstance<ToolLookup.NotFound>().map { it.problem }
