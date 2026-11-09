package jaitsizazu.engine.android

import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Kills the processes that a process started, on Android, which has no
 * `Process.descendants()`. For SystemProcessRunner's `killDescendants`: when
 * a download is cancelled, yt-dlp runs as Python, and Python may have started
 * ffmpeg; a child left running would keep the output pipes open and the
 * merged file half written.
 *
 * It finds the children by reading `/proc/<pid>/stat` under [procDir], where
 * Android shows an app its own processes, and kills them with [kill], the
 * deepest first, before the caller kills the process itself. [pidOf] gives
 * the PID of the process. Every step is best effort: a child that cannot be
 * found or killed is skipped.
 */
class AndroidProcessTree(
    private val procDir: File = File("/proc"),
    private val pidOf: (Process) -> Int? = ::androidPid,
    private val kill: (Int) -> Unit = { Os.kill(it, OsConstants.SIGKILL) },
) {
    fun killDescendants(process: Process) {
        val root = pidOf(process) ?: return
        for (pid in descendantsOf(root, readStats())) {
            runCatching { kill(pid) }
        }
    }

    /** Every process under [procDir], as its PID and the contents of its `stat` file. */
    private fun readStats(): Map<Int, String> =
        procDir.listFiles().orEmpty().mapNotNull { entry ->
            val pid = entry.name.toIntOrNull() ?: return@mapNotNull null
            // A process can end between the listing and the read.
            runCatching { File(entry, "stat").readText() }.getOrNull()?.let { pid to it }
        }.toMap()
}

/**
 * Every process below [root], deepest first, as the parent PIDs in [stats]
 * (PID to the contents of its `/proc/<pid>/stat`) link them. [root] itself is
 * not in the list.
 */
internal fun descendantsOf(root: Int, stats: Map<Int, String>): List<Int> {
    val children = stats.entries
        .mapNotNull { (pid, stat) -> parentPid(stat)?.let { parent -> parent to pid } }
        .groupBy({ it.first }, { it.second })
    val found = mutableListOf<Int>()
    val seen = mutableSetOf(root)
    var level = listOf(root)
    while (level.isNotEmpty()) {
        level = level.flatMap { children[it].orEmpty().sorted() }.filter(seen::add)
        found += level
    }
    return found.asReversed()
}

/**
 * The parent PID in [stat], the contents of a `/proc/<pid>/stat` file, or null
 * when it is malformed. The file reads `pid (name) state ppid ...`; the name
 * can hold spaces and parentheses, so the fields are read after its last `)`.
 */
internal fun parentPid(stat: String): Int? {
    val end = stat.lastIndexOf(')')
    if (end < 0) return null
    val fields = stat.substring(end + 1).trim().split(' ')
    return fields.getOrNull(1)?.toIntOrNull()
}

/**
 * The PID of [process], started by Android's `ProcessBuilder`, or null when it
 * cannot be read. Android's process class prints it (`Process[pid=1234, ...]`);
 * otherwise it is read from that class's private `pid` field.
 */
internal fun androidPid(process: Process): Int? =
    PRINTED_PID.find(process.toString())?.groupValues?.get(1)?.toIntOrNull()
        ?: runCatching {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        }.getOrNull()

private val PRINTED_PID = Regex("""^Process\[pid=(\d+)""")
