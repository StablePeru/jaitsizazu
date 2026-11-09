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
    /** Runs [command] (binary first, then its arguments). Throws an IOException if it cannot start. */
    suspend fun run(command: List<String>): ProcessOutcome
}

/**
 * [ProcessRunner] on [ProcessBuilder]. It runs on [dispatcher], off the main thread,
 * and reads stdout and stderr to the end so a chatty process cannot block on a
 * full pipe. Only the last [maxStderrLines] stderr lines are kept.
 *
 * Cancelling the coroutine kills the process and waits once, up to [exitTimeout],
 * for it to exit before the cancellation goes on. On Windows a running process
 * keeps its output file locked, so the caller can only remove that file once
 * the process is gone. [startProcess] starts the process; tests replace it.
 */
class SystemProcessRunner(
    private val maxStderrLines: Int = 20,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val exitTimeout: Duration = 5.seconds,
    private val startProcess: (List<String>) -> Process = { ProcessBuilder(it).start() },
) : ProcessRunner {

    override suspend fun run(command: List<String>): ProcessOutcome = withContext(dispatcher) {
        val process = startProcess(command)
        process.outputStream.close()
        try {
            coroutineScope {
                launch { process.inputStream.use { it.copyTo(OutputStream.nullOutputStream()) } }
                val tail = async { process.errorStream.bufferedReader().useLines(::lastLines) }
                val exitCode = try {
                    runInterruptible { process.waitFor() }
                } catch (e: CancellationException) {
                    // Killing the process closes its pipes, so the readers above
                    // finish and this scope can complete. `finally` waits for the exit.
                    process.destroyForcibly()
                    throw e
                }
                ProcessOutcome(exitCode, tail.await())
            }
        } finally {
            killAndAwaitExit(process)
        }
    }

    /**
     * Kills [process] if it still runs and blocks until it exits, for
     * [exitTimeout] at most. It does not suspend, so a cancelled coroutine
     * still waits here.
     */
    private fun killAndAwaitExit(process: Process) {
        if (!process.isAlive) return
        process.destroyForcibly()
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
    }
}
