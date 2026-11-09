package jaitsizazu.engine.android

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidProcessTreeTest {

    private val proc: File = Files.createTempDirectory("jaitsizazu-proc-test").toFile()

    @AfterTest
    fun cleanUp() {
        proc.deleteRecursively()
    }

    /** A process that is not an OS process: only its PID, as [toString] or a field, matters. */
    private open class FakeProcess(private val text: String = "FakeProcess") : Process() {
        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() = Unit
        override fun toString(): String = text
    }

    /** Keeps its PID in a private field named `pid`, like Android's java.lang.UNIXProcess. */
    @Suppress("unused")
    private class ProcessWithPidField(private val pid: Int) : FakeProcess()

    /** The contents of /proc/<pid>/stat for a process named [name] whose parent is [parent]. */
    private fun stat(pid: Int, parent: Int, name: String = "sh") = "$pid ($name) S $parent $pid $pid 0 -1 4194560 120 0 0 0"

    @Test
    fun readsTheParentEvenWhenTheProcessNameHasSpacesAndParentheses() {
        assertEquals(99, parentPid("1234 (my (odd) name) S 99 1234 1234 0 -1"))
        assertEquals(1, parentPid(stat(1234, parent = 1, name = ") S 777 ")))
    }

    @Test
    fun readsNoParentFromAMalformedStat() {
        assertNull(parentPid(""))
        assertNull(parentPid("1234 (sh S 99"))
        assertNull(parentPid("1234 (sh) S"))
        assertNull(parentPid("1234 (sh) S x"))
    }

    @Test
    fun listsEveryDescendantDeepestFirstAndNothingElse() {
        val stats = mapOf(
            1 to stat(1, parent = 0, name = "init"),
            10 to stat(10, parent = 1, name = "python"),
            11 to stat(11, parent = 10, name = "ffmpeg"),
            12 to stat(12, parent = 10, name = "qjs"),
            13 to stat(13, parent = 11),
            14 to stat(14, parent = 13),
            20 to stat(20, parent = 1, name = "other job"),
            21 to stat(21, parent = 20, name = "ffmpeg"),
            30 to "malformed",
        )

        assertEquals(listOf(14, 13, 12, 11), descendantsOf(10, stats))
        assertEquals(emptyList(), descendantsOf(14, stats))
    }

    @Test
    fun survivesAParentLoop() {
        // PIDs are reused, so a /proc read in two steps can show one.
        val stats = mapOf(10 to stat(10, parent = 11), 11 to stat(11, parent = 10))

        assertEquals(listOf(11), descendantsOf(10, stats))
    }

    @Test
    fun readsThePidFromWhatAndroidPrints() {
        assertEquals(4321, androidPid(FakeProcess("Process[pid=4321, hasExited=false]")))
    }

    @Test
    fun readsThePidFromThePrivateFieldOtherwise() {
        assertEquals(4321, androidPid(ProcessWithPidField(4321)))
    }

    @Test
    fun readsNoPidFromAProcessWithoutOne() {
        assertNull(androidPid(FakeProcess()))
    }

    private fun writeStat(pid: Int, parent: Int) {
        File(proc, "$pid").mkdir()
        File(proc, "$pid/stat").writeText(stat(pid, parent))
    }

    @Test
    fun killsTheDescendantsFoundInProcDeepestFirst() {
        writeStat(10, parent = 1)
        writeStat(11, parent = 10)
        writeStat(12, parent = 11)
        writeStat(20, parent = 1)
        // Not processes, or gone before their stat could be read.
        File(proc, "self").mkdir()
        File(proc, "uptime").writeText("1.0 1.0")
        File(proc, "13").mkdir()
        val killed = mutableListOf<Int>()
        val tree = AndroidProcessTree(proc, pidOf = { 10 }, kill = { killed += it })

        tree.killDescendants(FakeProcess())

        assertEquals(listOf(12, 11), killed)
    }

    @Test
    fun keepsKillingWhenOneKillFails() {
        writeStat(11, parent = 10)
        writeStat(12, parent = 10)
        val killed = mutableListOf<Int>()
        val tree = AndroidProcessTree(proc, pidOf = { 10 }, kill = { if (it == 12) error("gone") else killed += it })

        tree.killDescendants(FakeProcess())

        assertEquals(listOf(11), killed)
    }

    @Test
    fun killsNothingWhenThePidIsUnknown() {
        writeStat(11, parent = 10)
        val killed = mutableListOf<Int>()

        AndroidProcessTree(proc, pidOf = { null }, kill = { killed += it }).killDescendants(FakeProcess())

        assertEquals(emptyList(), killed)
    }

    @Test
    fun killsRealChildrenThroughTheRealProc() {
        if (!File("/proc/self/stat").exists()) return // Needs Linux, like Android.
        val shell = ProcessBuilder("sh", "-c", "sleep 300 & sleep 300 & echo started; wait").start()
        try {
            assertEquals("started", shell.inputStream.bufferedReader().readLine())
            val tree = AndroidProcessTree(
                // The host JVM's Process.pid(), which the Android stubs these tests compile against lack.
                pidOf = { (Process::class.java.getMethod("pid").invoke(it) as Long).toInt() },
                kill = { ProcessBuilder("kill", "-9", "$it").start().waitFor() },
            )

            tree.killDescendants(shell)

            // `wait` returns once both children are gone, and the shell exits.
            assertTrue(shell.waitFor(10, TimeUnit.SECONDS), "a child of the shell still runs")
        } finally {
            shell.destroyForcibly()
        }
    }
}
