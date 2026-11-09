package jaitsizazu.engine.android

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries

/**
 * The private work folders of the Android app, inside [root] in its cache:
 * one folder per download, and one per file picked for conversion. Each run of
 * the app works in its own session folder, so [removeOldSessions] can clear
 * what an earlier run left (for example when Android stopped the app in the
 * middle of a download) without touching the work of this one.
 */
@OptIn(ExperimentalPathApi::class)
class JobFolders(private val root: Path, sessionName: String = "session-${UUID.randomUUID()}") {
    private val session: Path = root.resolve(sessionName)

    /** A new, empty folder of this session, its name starting with [prefix]. */
    fun newJob(prefix: String): Path {
        Files.createDirectories(session)
        return Files.createTempDirectory(session, prefix)
    }

    /** Removes everything in [root] but this session's folder. Best effort: what fails stays for the next start. */
    fun removeOldSessions() {
        if (!Files.isDirectory(root)) return
        for (entry in root.listDirectoryEntries()) {
            if (entry == session) continue
            runCatching { entry.deleteRecursively() }
        }
    }
}
