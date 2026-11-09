package jaitsizazu.engine.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** How a finished process ended. [stderrTail] holds its last stderr lines. */
data class ProcessOutcome(val exitCode: Int, val stderrTail: String)

/** Runs an external command to completion. */
interface ProcessRunner {
    /**
     * Runs [command] (binary first, then its arguments). Throws an IOException if it cannot start.
     * When [onOutputLine] is set, it gets every line of stdout and of stderr, on a
     * background thread and one line at a time; otherwise stdout is thrown away.
     * Lines of one stream keep their order; the two streams are not ordered between them.
     */
    suspend fun run(command: List<String>, onOutputLine: ((String) -> Unit)? = null): ProcessOutcome
}

/**
 * [ProcessRunner] on [ProcessBuilder]. It runs on [dispatcher], off the main thread,
 * and reads stdout and stderr to the end so a chatty process cannot block on a
 * full pipe. Only the last [maxStderrLines] stderr lines are kept.
 *
 * Cancelling the coroutine kills the process and every process it started
 * (yt-dlp, for example, runs as a launcher plus a child, and starts ffmpeg),
 * then waits once, up to [exitTimeout], for the process to exit before the
 * cancellation goes on. On Windows a running process keeps its output file
 * locked, so the caller can only remove that file once the process is gone.
 * [startProcess] starts the process; tests replace it.
 */
class SystemProcessRunner(
    private val maxStderrLines: Int = 20,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val exitTimeout: Duration = 5.seconds,
    private val startProcess: (List<String>) -> Process = { ProcessBuilder(it).start() },
) : ProcessRunner {

    override suspend fun run(command: List<String>, onOutputLine: ((String) -> Unit)?): ProcessOutcome =
        withContext(dispatcher) {
            val process = startProcess(command)
            process.outputStream.close()
            try {
                coroutineScope {
                    val forward = onOutputLine?.let(::oneLineAtATime)
                    launch { readStdout(process, forward) }
                    val tail = async {
                        process.errorStream.bufferedReader().useLines { lines ->
                            lastLines(if (forward == null) lines else lines.onEach { forward(it.take(MAX_LINE_LENGTH)) })
                        }
                    }
                    val exitCode = try {
                        runInterruptible { process.waitFor() }
                    } catch (e: CancellationException) {
                        // Killing the processes closes their pipes, so the readers
                        // above finish and this scope can complete. `finally` waits
                        // for the exit.
                        killTree(process)
                        throw e
                    }
                    ProcessOutcome(exitCode, tail.await())
                }
            } finally {
                killAndAwaitExit(process)
            }
        }

    /** Wraps [callback] so the stdout and stderr readers never call it at the same time. */
    private fun oneLineAtATime(callback: (String) -> Unit): (String) -> Unit {
        val lock = Any()
        return { line -> synchronized(lock) { callback(line) } }
    }

    private fun readStdout(process: Process, onOutputLine: ((String) -> Unit)?) {
        if (onOutputLine == null) {
            process.inputStream.use { it.copyTo(OutputStream.nullOutputStream()) }
        } else {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { onOutputLine(it.take(MAX_STDOUT_LINE_LENGTH)) }
            }
        }
    }

    /**
     * Kills the processes [process] started, then [process]. The children are
     * listed first, while they still hang from [process]. A child that kept
     * running would also keep the pipes open, and the readers would never end.
     */
    private fun killTree(process: Process) {
        // A test double that is not an OS process cannot list its children.
        runCatching { process.descendants().forEach { it.destroyForcibly() } }
        process.destroyForcibly()
    }

    /**
     * Kills [process] and its children if it still runs, and blocks until it
     * exits, for [exitTimeout] at most. It does not suspend, so a cancelled
     * coroutine still waits here.
     */
    private fun killAndAwaitExit(process: Process) {
        if (!process.isAlive) return
        killTree(process)
        try {
            process.waitFor(exitTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun lastLines(lines: Sequence<String>): String {
        val tail = ArrayDeque<String>(maxStderrLines)
        for (line in lines) {
            if (tail.size == maxStderrLines) tail.removeFirst()
            tail.addLast(line.take(MAX_LINE_LENGTH))
        }
        return tail.joinToString("\n")
    }

    private companion object {
        const val MAX_LINE_LENGTH = 500

        /** Long enough for a full file path, which a stdout line may carry. */
        const val MAX_STDOUT_LINE_LENGTH = 8192
    }
}
