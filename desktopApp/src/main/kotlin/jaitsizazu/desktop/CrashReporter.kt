package jaitsizazu.desktop

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.time.Instant

/**
 * The folder for the launcher log and crash reports: `%LOCALAPPDATA%\Jaitsizazu`,
 * or `Jaitsizazu` in [tempDir] when `LOCALAPPDATA` is unset (outside Windows).
 * The Windows launcher writes `launch.log` to the same folder.
 */
fun logDirectory(
    environment: (String) -> String? = System::getenv,
    tempDir: String = System.getProperty("java.io.tmpdir"),
): Path {
    val base = environment("LOCALAPPDATA")?.trim()?.takeIf { it.isNotEmpty() }
    val fromEnvironment = base?.let {
        try {
            Path.of(it, APP_NAME)
        } catch (_: InvalidPathException) {
            null
        }
    }
    return fromEnvironment ?: Path.of(tempDir, APP_NAME)
}

/**
 * Default uncaught exception handler. It writes the stack trace to stderr and to
 * [logFile], and calls [showError] once with a short message for the user, so a
 * crash leaves a trace even when the app runs without a console.
 *
 * [logFile] starts empty on the first crash of each launch and keeps every crash
 * of that launch. The handler never throws: a log or dialog that fails is skipped.
 */
class CrashReporter(
    private val logFile: Path,
    private val showError: (String) -> Unit,
    private val clock: () -> Instant = Instant::now,
) : Thread.UncaughtExceptionHandler {

    private var firstCrash = true
    private var dialogShown = false

    @Synchronized
    override fun uncaughtException(thread: Thread, error: Throwable) {
        val report = "${clock()} Uncaught exception in thread \"${thread.name}\"\n${error.stackTraceToString()}"
        runCatching { System.err.print(report) }
        val saved = runCatching { write(report) }.isSuccess

        if (dialogShown) return
        dialogShown = true
        val details = if (saved) "Details were saved to $logFile." else "Details could not be saved to $logFile."
        runCatching { showError("$APP_NAME ran into an unexpected error:\n$error\n\n$details") }
    }

    private fun write(report: String) {
        Files.createDirectories(logFile.parent)
        val mode = if (firstCrash) TRUNCATE_EXISTING else APPEND
        Files.writeString(logFile, report, CREATE, WRITE, mode)
        firstCrash = false
    }
}
