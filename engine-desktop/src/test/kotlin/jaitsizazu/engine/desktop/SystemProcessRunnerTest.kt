package jaitsizazu.engine.desktop

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Runs the current `java` binary as a child process, so it works on any OS. */
class SystemProcessRunnerTest {

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
