package jaitsizazu.engine.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Runs the current `java` binary as a child process, so it works on any OS. */
class SystemProcessRunnerTest {

    /**
     * A process that needs [dieAfterMillis] to exit once it is killed, like a
     * Windows process that still holds its files open for a moment. Its pipes
     * close as soon as it is killed.
     */
    private class SlowToDieProcess(private val dieAfterMillis: Long) : Process() {
        private val killed = CountDownLatch(1)
        private val exited = CountDownLatch(1)

        @Volatile
        var waiting = false
            private set

        private val pipe = object : InputStream() {
            override fun read(): Int {
                killed.await()
                return -1
            }
        }

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = pipe
        override fun getErrorStream(): InputStream = pipe

        override fun waitFor(): Int {
            waiting = true
            exited.await()
            return KILLED
        }

        /** How many times someone waited for this process with a timeout. */
        @Volatile
        var timedWaits = 0
            private set

        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean {
            timedWaits++
            return exited.await(timeout, unit)
        }

        override fun exitValue(): Int =
            if (isAlive) throw IllegalThreadStateException("still running") else KILLED

        override fun isAlive(): Boolean = exited.count > 0

        override fun destroy() {
            destroyForcibly()
        }

        override fun destroyForcibly(): Process {
            if (killed.count > 0) {
                killed.countDown()
                thread(isDaemon = true) {
                    Thread.sleep(dieAfterMillis)
                    exited.countDown()
                }
            }
            return this
        }

        companion object {
            const val KILLED = 137
        }
    }

    private suspend fun cancelWhileWaiting(process: SlowToDieProcess, runner: SystemProcessRunner) =
        withTimeout(30_000) {
            val job = launch(Dispatchers.Default) { runner.run(listOf("ignored")) }
            while (!process.waiting) delay(10)
            job.cancelAndJoin()
        }

    @Test
    fun waitsForAKilledProcessToExitBeforeTheCancellationGoesOn() = runBlocking {
        val process = SlowToDieProcess(dieAfterMillis = 300)

        cancelWhileWaiting(process, SystemProcessRunner(startProcess = { process }))

        assertFalse(process.isAlive, "the cancellation went on while the killed process was still running")
    }

    @Test
    fun waitsForAKilledProcessOnlyForABoundedTime() = runBlocking {
        val process = SlowToDieProcess(dieAfterMillis = 60_000)
        val runner = SystemProcessRunner(startProcess = { process }, exitTimeout = 200.milliseconds)

        cancelWhileWaiting(process, runner)

        assertTrue(process.isAlive)
    }

    @Test
    fun waitsForAKilledProcessOnlyOnce() = runBlocking {
        val process = SlowToDieProcess(dieAfterMillis = 60_000)
        val runner = SystemProcessRunner(startProcess = { process }, exitTimeout = 100.milliseconds)

        cancelWhileWaiting(process, runner)

        assertEquals(1, process.timedWaits, "a cancellation must not wait out the exit timeout twice")
    }

    private val java = ProcessHandle.current().info().command().orElseThrow()

    @Test
    fun reportsTheExitCodeAndTheStderrTail() = runBlocking {
        val outcome = SystemProcessRunner().run(listOf(java, "-version"))

        assertEquals(0, outcome.exitCode)
        assertTrue("version" in outcome.stderrTail, outcome.stderrTail)
    }

    @Test
    fun reportsAFailure() = runBlocking {
        val outcome = SystemProcessRunner().run(listOf(java, "-XX:+JaitsizazuNoSuchOption", "-version"))

        assertNotEquals(0, outcome.exitCode)
        assertTrue("JaitsizazuNoSuchOption" in outcome.stderrTail, outcome.stderrTail)
    }

    @Test
    fun keepsOnlyTheLastStderrLines() = runBlocking {
        // `java -version` writes three lines to stderr; the first one says "version".
        val outcome = SystemProcessRunner(maxStderrLines = 2).run(listOf(java, "-version"))

        assertEquals(2, outcome.stderrTail.lines().size, outcome.stderrTail)
        assertFalse(" version " in outcome.stderrTail, outcome.stderrTail)
    }

    @Test
    fun drainsALargeStdoutWithoutBlocking() = runBlocking {
        // Prints hundreds of kilobytes, far more than a pipe buffer holds.
        val outcome = withTimeout(60_000) {
            SystemProcessRunner().run(listOf(java, "-XX:+PrintFlagsFinal", "-version"))
        }

        assertEquals(0, outcome.exitCode)
    }

    @Test
    fun throwsWhenTheBinaryDoesNotExist() {
        assertFailsWith<IOException> {
            runBlocking { SystemProcessRunner().run(listOf("/no/such/jaitsizazu-binary")) }
        }
    }
}
