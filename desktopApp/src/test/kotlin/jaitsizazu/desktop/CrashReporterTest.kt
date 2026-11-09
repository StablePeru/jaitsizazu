package jaitsizazu.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.concurrent.thread
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
    private val now = Instant.parse("2026-11-09T10:00:00Z")

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** A reporter that runs as if every crash happened on the event thread. */
    private fun reporter(logFile: Path, showError: (String) -> Unit = { dialogs += it }) =
        CrashReporter(logFile, showError, clock = { now }, isEventThread = { true }, runOnEventThread = { it() })

    /** A single thread that stands in for the AWT event thread. */
    private class FakeEventThread : AutoCloseable {
        private val executor = Executors.newSingleThreadExecutor { Thread(it, "fake-event-thread").apply { isDaemon = true } }
        private val eventThread: Thread = executor.submit<Thread> { Thread.currentThread() }.get()

        fun isCurrent(): Boolean = Thread.currentThread() === eventThread

        fun post(task: () -> Unit) = executor.execute(task)

        /** Runs [task] on the event thread and waits for it, like `EventQueue.invokeAndWait`. */
        fun runAndWait(task: () -> Unit) {
            executor.submit(task).get()
        }

        override fun close() {
            executor.shutdownNow()
        }
    }

    @Test
    fun logsUnderLocalAppDataOnWindows() {
        val env = mapOf("LOCALAPPDATA" to "/users/me/AppData/Local")

        assertEquals(
            Path.of("/users/me/AppData/Local", "Jaitsizazu"),
            logDirectory(env::get, home = "/users/me", osName = "Windows 11"),
        )
    }

    @Test
    fun logsUnderTheUserProfileOnWindowsWithoutLocalAppData() {
        val expected = Path.of("/users/me", "AppData", "Local", "Jaitsizazu")

        assertEquals(expected, logDirectory({ null }, home = "/users/me", osName = "Windows 11"))
        assertEquals(expected, logDirectory({ "  " }, home = "/users/me", osName = "Windows 11"))
    }

    @Test
    fun logsUnderLibraryLogsOnMacOs() {
        assertEquals(
            Path.of("/Users/me", "Library", "Logs", "Jaitsizazu"),
            logDirectory({ null }, home = "/Users/me", osName = "Mac OS X"),
        )
    }

    @Test
    fun logsUnderTheXdgStateHomeOnLinux() {
        val env = mapOf("XDG_STATE_HOME" to "/home/me/state", "LOCALAPPDATA" to "/elsewhere")

        assertEquals(Path.of("/home/me/state", "jaitsizazu"), logDirectory(env::get, home = "/home/me", osName = "Linux"))
    }

    @Test
    fun logsUnderLocalStateOnLinuxWithoutAnAbsoluteXdgStateHome() {
        val expected = Path.of("/home/me", ".local", "state", "jaitsizazu")

        for (xdgStateHome in listOf(null, "  ", "relative/state")) {
            val env = mapOf("XDG_STATE_HOME" to xdgStateHome)
            assertEquals(expected, logDirectory(env::get, home = "/home/me", osName = "Linux"), "XDG_STATE_HOME=$xdgStateHome")
        }
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
    fun createsTheLogFolderForTheOwnerOnly() {
        if ("posix" !in dir.fileSystem.supportedFileAttributeViews()) return
        val log = dir.resolve("state").resolve("jaitsizazu").resolve("crash.log")

        reporter(log).uncaughtException(thread, IllegalStateException("boom"))

        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(log.parent))
    }

    @Test
    fun refusesToWriteThroughASymlinkedLog() {
        val target = dir.resolve("someone-elses-file").apply { writeText("keep me") }
        val log = Files.createSymbolicLink(dir.resolve("crash.log"), target)

        reporter(log).uncaughtException(thread, IllegalStateException("boom"))

        assertEquals("keep me", target.readText())
        assertTrue("could not be saved" in dialogs.single(), dialogs.single())
    }

    @Test
    fun refusesALogFolderThatIsASymlink() {
        val elsewhere = Files.createDirectory(dir.resolve("elsewhere"))
        val folder = Files.createSymbolicLink(dir.resolve("Jaitsizazu"), elsewhere)

        reporter(folder.resolve("crash.log")).uncaughtException(thread, IllegalStateException("boom"))

        assertFalse(Files.exists(elsewhere.resolve("crash.log")))
        assertTrue("could not be saved" in dialogs.single(), dialogs.single())
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
    fun saysItInTheTextsItWasGiven() {
        val log = dir.resolve("crash.log")
        val texts = CrashTexts(intro = "Errorea:", detailsSaved = "Gordeta: $log", detailsNotSaved = "Ez da gorde: $log")
        val reporter = CrashReporter(log, { dialogs += it }, texts, clock = { now }, isEventThread = { true }, runOnEventThread = { it() })

        reporter.uncaughtException(thread, IllegalStateException("first"))

        val message = dialogs.single()
        assertTrue(message.startsWith("Errorea:\n"), message)
        assertTrue("first" in message, message)
        assertTrue(message.endsWith("Gordeta: $log"), message)
    }

    @Test
    fun showsTheDialogOfACrashElsewhereLaterOnTheEventThread() = FakeEventThread().use { events ->
        val shownOnEventThread = CopyOnWriteArrayList<Boolean>()
        val shown = CountDownLatch(1)
        val reporter = CrashReporter(
            dir.resolve("crash.log"),
            showError = {
                shownOnEventThread += events.isCurrent()
                shown.countDown()
            },
            clock = { now },
            isEventThread = events::isCurrent,
            runOnEventThread = events::post,
        )

        reporter.uncaughtException(Thread("worker"), IllegalStateException("boom"))

        assertTrue(shown.await(10, SECONDS), "the dialog was never shown")
        assertEquals(listOf(true), shownOnEventThread)
    }

    /**
     * A worker thread crashes while the event thread is busy, and then the event
     * thread crashes too. The worker must not hold the reporter while it waits for
     * the event thread, or the two threads wait for each other forever.
     */
    @Test
    fun aCrashOnTheEventThreadDoesNotDeadlockWithAnEarlierCrashElsewhere() = FakeEventThread().use { events ->
        val workerInHandler = CountDownLatch(1)
        val workerDone = CountDownLatch(1)
        val eventThreadDone = CountDownLatch(1)
        val dialogs = CopyOnWriteArrayList<String>()
        // A modal dialog belongs on the event thread: called elsewhere it waits for it.
        val showDialog = { message: String ->
            if (events.isCurrent()) dialogs += message else events.runAndWait { dialogs += message }
        }
        val reporter = CrashReporter(
            dir.resolve("crash.log"),
            showError = showDialog,
            clock = {
                if (Thread.currentThread().name == "worker") workerInHandler.countDown()
                now
            },
            isEventThread = events::isCurrent,
            runOnEventThread = events::post,
        )

        events.post {
            workerInHandler.await(10, SECONDS)
            reporter.uncaughtException(Thread.currentThread(), IllegalStateException("event thread crash"))
            eventThreadDone.countDown()
        }
        thread(isDaemon = true, name = "worker") {
            reporter.uncaughtException(Thread.currentThread(), IllegalStateException("worker crash"))
            workerDone.countDown()
        }

        assertTrue(workerDone.await(5, SECONDS) && eventThreadDone.await(5, SECONDS), "the two crashes deadlocked")
        events.runAndWait {} // lets a dialog posted by the worker run
        assertEquals(1, dialogs.size, dialogs.toString())
        val text = dir.resolve("crash.log").readText()
        assertTrue("worker crash" in text && "event thread crash" in text, text)
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
