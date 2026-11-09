package jaitsizazu.engine.android

import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class JobFoldersTest {

    private val root = Files.createTempDirectory("jaitsizazu-jobs-test")

    @AfterTest
    fun cleanUp() = root.deleteRecursively()

    @Test
    fun makesEachJobItsOwnFolderInTheSession() {
        val jobs = JobFolders(root, sessionName = "session-now")

        val first = jobs.newJob("download-")
        val second = jobs.newJob("download-")

        assertTrue(first != second)
        assertEquals(root.resolve("session-now"), first.parent)
        assertTrue(first.name.startsWith("download-"), first.name)
    }

    @Test
    fun removesWhatEarlierRunsLeftButNotThisSession() {
        root.resolve("session-old/download-1").createDirectories().resolve("Clip.part").writeText("x")
        val jobs = JobFolders(root, sessionName = "session-now")
        val current = jobs.newJob("input-")

        jobs.removeOldSessions()

        assertEquals(listOf("session-now"), root.listDirectoryEntries().map { it.name })
        assertTrue(current.exists())
    }

    @Test
    fun hasNothingToRemoveBeforeTheFirstJob() {
        JobFolders(root.resolve("missing")).removeOldSessions()
    }
}
