package jaitsizazu.desktop

import java.awt.EventQueue
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The per-user folder for crash reports, never a shared one such as the temp folder:
 *
 * - Windows: `%LOCALAPPDATA%\Jaitsizazu`, or `AppData\Local\Jaitsizazu` in [home]
 *   when `LOCALAPPDATA` is unset. The Windows launcher writes `launch.log` there too.
 * - macOS: `~/Library/Logs/Jaitsizazu`.
 * - Others: `$XDG_STATE_HOME/jaitsizazu`, or `~/.local/state/jaitsizazu` when
 *   `XDG_STATE_HOME` is unset or not an absolute path.
 */
fun logDirectory(
    environment: (String) -> String? = System::getenv,
    home: String = System.getProperty("user.home"),
    osName: String = System.getProperty("os.name"),
): Path {
    fun absolutePathIn(variable: String): Path? {
        val value = environment(variable)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            Path.of(value).takeIf { it.isAbsolute }
        } catch (_: InvalidPathException) {
            null
        }
    }

    return when {
        osName.startsWith("Windows") ->
            (absolutePathIn("LOCALAPPDATA") ?: Path.of(home, "AppData", "Local")).resolve(APP_NAME)
        osName.startsWith("Mac") -> Path.of(home, "Library", "Logs", APP_NAME)
        else -> (absolutePathIn("XDG_STATE_HOME") ?: Path.of(home, ".local", "state")).resolve(APP_NAME.lowercase())
    }
}

/**
 * The words of the crash dialog. They are loaded once, before anything can
 * crash, so the crash handler never reads resources: [intro] comes before the
 * error, then [detailsSaved] or [detailsNotSaved], which name the log file.
 */
data class CrashTexts(val intro: String, val detailsSaved: String, val detailsNotSaved: String) {
    companion object {
        /** The English texts, built in, for when the translated ones cannot be loaded. */
        fun fallback(logFile: Path) = CrashTexts(
            intro = "$APP_NAME ran into an unexpected error:",
            detailsSaved = "Details were saved to $logFile.",
            detailsNotSaved = "Details could not be saved to $logFile.",
        )
    }
}

/**
 * Default uncaught exception handler. It writes the stack trace to stderr and to
 * [logFile], and calls [showError] once with a short message for the user, in
 * [texts], so a crash leaves a trace even when the app runs without a console.
 *
 * [logFile] starts empty on the first crash of each launch and keeps every crash
 * of that launch. Its folder is created for the owner only where the file system
 * has POSIX permissions, and a folder or log that is a symbolic link is never
 * written through. The handler never throws: a log or dialog that fails is skipped.
 *
 * [showError] always runs on the event thread: right away when the crash
 * happened there, otherwise later through [runOnEventThread]. The handler never
 * waits for the event thread, because the event thread may be the next thread
 * to crash and would then wait for this handler.
 */
class CrashReporter(
    private val logFile: Path,
    private val showError: (String) -> Unit,
    private val texts: CrashTexts = CrashTexts.fallback(logFile),
    private val clock: () -> Instant = Instant::now,
    private val isEventThread: () -> Boolean = EventQueue::isDispatchThread,
    private val runOnEventThread: (() -> Unit) -> Unit = { EventQueue.invokeLater(it) },
) : Thread.UncaughtExceptionHandler {

    private var firstCrash = true
    private val dialogShown = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, error: Throwable) {
        val saved = log(thread, error)
        if (!dialogShown.compareAndSet(false, true)) return

        val details = if (saved) texts.detailsSaved else texts.detailsNotSaved
        val message = "${texts.intro}\n$error\n\n$details"
        val show = {
            runCatching { showError(message) }
            Unit
        }
        runCatching { if (isEventThread()) show() else runOnEventThread(show) }
    }

    /** Writes the report of [error]. Returns whether [logFile] was saved. One crash at a time. */
    @Synchronized
    private fun log(thread: Thread, error: Throwable): Boolean {
        val report = "${clock()} Uncaught exception in thread \"${thread.name}\"\n${error.stackTraceToString()}"
        runCatching { System.err.print(report) }
        return runCatching { write(report) }.isSuccess
    }

    private fun write(report: String) {
        val folder = logFile.parent
        if ("posix" in folder.fileSystem.supportedFileAttributeViews()) {
            Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(OWNER_ONLY))
        } else {
            Files.createDirectories(folder)
        }
        if (Files.isSymbolicLink(folder) || Files.isSymbolicLink(logFile)) {
            throw IOException("$logFile or its folder is a symbolic link")
        }
        val mode = if (firstCrash) TRUNCATE_EXISTING else APPEND
        // NOFOLLOW_LINKS also refuses a link that appears after the check above.
        Files.writeString(logFile, report, CREATE, WRITE, mode, NOFOLLOW_LINKS)
        firstCrash = false
    }

    private companion object {
        val OWNER_ONLY = PosixFilePermissions.fromString("rwx------")
    }
}
