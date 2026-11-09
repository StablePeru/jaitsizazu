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
 * Cancelling the coroutine kills the process.
 */
class SystemProcessRunner(
    private val maxStderrLines: Int = 20,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProcessRunner {

    override suspend fun run(command: List<String>): ProcessOutcome = withContext(dispatcher) {
        val process = ProcessBuilder(command).start()
        process.outputStream.close()
        try {
            coroutineScope {
                launch { process.inputStream.use { it.copyTo(OutputStream.nullOutputStream()) } }
                val tail = async { process.errorStream.bufferedReader().useLines(::lastLines) }
                val exitCode = try {
                    runInterruptible { process.waitFor() }
                } catch (e: CancellationException) {
                    // Killing the process closes its pipes, so the readers above
                    // finish and this scope can complete.
                    process.destroyForcibly()
                    throw e
                }
                ProcessOutcome(exitCode, tail.await())
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
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
