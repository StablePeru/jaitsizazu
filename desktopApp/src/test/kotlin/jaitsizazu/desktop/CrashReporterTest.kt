package jaitsizazu.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReporterTest {

    private val dir: Path = createTempDirectory("jaitsizazu-crash")
    private val dialogs = mutableListOf<String>()
    private val thread = Thread("AWT-EventQueue-0")

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun reporter(logFile: Path, showError: (String) -> Unit = { dialogs += it }) =
        CrashReporter(logFile, showError, clock = { Instant.parse("2026-11-09T10:00:00Z") })

    @Test
    fun logsUnderLocalAppData() {
        val env = mapOf("LOCALAPPDATA" to "/users/me/AppData/Local")

        assertEquals(Path.of("/users/me/AppData/Local", "Jaitsizazu"), logDirectory(env::get, tempDir = "/tmp"))
    }

    @Test
    fun logsUnderTheTempDirWithoutLocalAppData() {
        assertEquals(Path.of("/tmp", "Jaitsizazu"), logDirectory({ null }, tempDir = "/tmp"))
        assertEquals(Path.of("/tmp", "Jaitsizazu"), logDirectory({ "  " }, tempDir = "/tmp"))
    }

    @Test
    fun writesTheStackTraceAndCreatesTheFolder() {
        val log = dir.resolve("Jaitsizazu").resolve("crash.log")

        reporter(log).uncaughtException(thread, UnsatisfiedLinkError("no skiko-windows-x64 in java.library.path"))

        val text = log.readText()
        assertTrue("2026-11-09T10:00:00Z" in text, text)
        assertTrue("AWT-EventQueue-0" in text, text)
        assertTrue("java.lang.UnsatisfiedLinkError: no skiko-windows-x64" in text, text)
        assertTrue("CrashReporterTest" in text, "the stack trace is missing:\n$text")
    }

    @Test
    fun startsAFreshLogOnEachLaunchAndKeepsEveryCrashOfThisOne() {
        val log = dir.resolve("crash.log").apply { writeText("crash of an earlier launch") }
        val reporter = reporter(log)

        reporter.uncaughtException(thread, IllegalStateException("first"))
        reporter.uncaughtException(thread, IllegalStateException("second"))

        val text = log.readText()
        assertFalse("earlier launch" in text, text)
        assertTrue("first" in text && "second" in text, text)
    }

    @Test
    fun showsOneDialogThatSaysWhereTheDetailsAre() {
        val log = dir.resolve("crash.log")
        val reporter = reporter(log)

        reporter.uncaughtException(thread, IllegalStateException("first"))
        reporter.uncaughtException(thread, IllegalStateException("second"))

        val message = dialogs.single()
        assertTrue("first" in message, message)
        assertTrue(log.toString() in message, message)
    }

    @Test
    fun neverThrowsWhenTheLogOrTheDialogFail() {
        // The parent of the log is a regular file, so the log cannot be written.
        val blocker = dir.resolve("not-a-folder").apply { writeText("") }
        val log = blocker.resolve("crash.log")

        reporter(log).uncaughtException(thread, IllegalStateException("boom"))
        reporter(log, showError = { error("no display") }).uncaughtException(thread, IllegalStateException("boom"))

        assertTrue("could not be saved" in dialogs.single(), dialogs.single())
        assertFalse(Files.exists(log))
    }
}
