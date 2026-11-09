package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadCancelledWithLeftovers
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.Leftover
import jaitsizazu.core.MediaDownloader
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Desktop adapter for [MediaDownloader]: runs yt-dlp as a subprocess.
 *
 * yt-dlp, ffmpeg (to convert and merge) and deno (the JavaScript runtime that
 * YouTube needs) are looked for on every download, like ffmpeg for the
 * converter. Without deno the download still runs: other sites work without it.
 *
 * Each download gets its own work folder inside the output folder, made by
 * [createWorkDirectory]. yt-dlp writes every partial file there and moves only
 * the finished file out, so the work folder is removed afterwards in every
 * case: success, failure or cancellation. On Windows a file can stay locked for
 * a moment after its process exits, so the removal is tried [cleanupAttempts]
 * times, [cleanupRetryDelay] apart. A folder that still cannot be removed is
 * named as the failure's leftover, or, after a cancellation, through
 * [DownloadCancelledWithLeftovers].
 *
 * A file that was already in the output folder is never replaced. yt-dlp runs
 * with `--no-overwrites`, so it neither downloads over an existing file nor
 * moves its finished file out of the work folder onto one: its move step checks
 * that the target does not exist, through the file system, so a name that
 * differs only in case counts as existing on Windows and macOS. When yt-dlp
 * then names such a file as its result, the download is reported as a failure.
 * The names are compared with [isSameFile] (by default [Files.isSameFile]), not
 * as text, for the same reason. What no check here can rule out is another
 * program creating that file in the instant between yt-dlp's check and its
 * move: on Linux and macOS the move would then replace it.
 */
@OptIn(ExperimentalPathApi::class)
class YtDlpDownloader(
    private val locateYtDlp: () -> ToolLookup,
    private val locateFfmpeg: () -> ToolLookup,
    private val locateDeno: () -> ToolLookup,
    private val runner: ProcessRunner,
    private val createWorkDirectory: (Path) -> Path = { Files.createTempDirectory(it, WORK_DIRECTORY_PREFIX) },
    private val isSameFile: (Path, Path) -> Boolean = ::isSameExistingFile,
    private val deleteRecursively: (Path) -> Unit = { it.deleteRecursively() },
    private val cleanupAttempts: Int = 3,
    private val cleanupRetryDelay: Duration = 500.milliseconds,
) : MediaDownloader {

    override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
        val ytDlp = when (val lookup = locateYtDlp()) {
            is ToolLookup.Found -> lookup.path
            is ToolLookup.NotFound -> return MediaResult.Failure(lookup.problem)
        }
        val ffmpeg = when (val lookup = locateFfmpeg()) {
            is ToolLookup.Found -> lookup.path
            is ToolLookup.NotFound -> return MediaResult.Failure(lookup.problem)
        }
        val deno = (locateDeno() as? ToolLookup.Found)?.path

        val output: Path
        val entriesBefore: List<Path>
        val work: Path
        try {
            output = Path.of(request.outputDirectory)
            entriesBefore = output.listDirectoryEntries()
            work = createWorkDirectory(output)
        } catch (e: IOException) {
            return MediaResult.Failure(Problem.CannotSaveInto(request.outputDirectory, e.message ?: e::class.simpleName))
        } catch (e: InvalidPathException) {
            return MediaResult.Failure(Problem.CannotSaveInto(request.outputDirectory, e.message))
        }

        val saved = AtomicReference<String?>(null)
        val command = listOf(ytDlp) +
            YtDlpArguments.download(request.url, request.quality, output.toString(), work.toString(), ffmpeg, deno)
        val outcome = try {
            runner.run(command) { line ->
                YtDlpOutput.progress(line)?.let(onProgress)
                YtDlpOutput.savedFile(line)?.let(saved::set)
            }
        } catch (e: IOException) {
            val leftover = removeWorkDirectory(work)
            return MediaResult.Failure(Problem.ToolDidNotStart(Tool.YT_DLP, ytDlp, e.message)).withLeftover(work, leftover)
        } catch (e: CancellationException) {
            // The runner has killed yt-dlp and its children and waited for them to exit.
            val leftover = removeWorkDirectory(work) ?: throw e
            throw DownloadCancelledWithLeftovers(work.toString()).apply { addSuppressed(leftover) }
        } catch (e: Throwable) {
            // An error nobody expected: the runner has killed yt-dlp.
            removeWorkDirectory(work)?.let(e::addSuppressed)
            throw e
        }
        val leftover = removeWorkDirectory(work)
        return result(outcome, saved.get(), entriesBefore).withLeftover(work, leftover)
    }

    private fun result(outcome: ProcessOutcome, saved: String?, entriesBefore: List<Path>): MediaResult {
        if (outcome.exitCode != 0) {
            // In quiet mode yt-dlp writes some of the progress lines we asked for to stderr.
            val errors = outcome.stderrTail.lines().filterNot { it.startsWith(YtDlpOutput.PREFIX) }.joinToString("\n")
            return MediaResult.Failure(Problem.DownloadFailed(outcome.exitCode, errors))
        }
        if (saved == null) return MediaResult.Failure(Problem.NoFileReported)
        val savedPath = Path.of(saved)
        val name = savedPath.name
        // Only names that could be the same file on some file system are checked: it
        // saves asking the file system about every file in a large Downloads folder.
        val existing = entriesBefore.firstOrNull { sameNameIgnoringCase(it.name, name) && isSameFile(it, savedPath) }
        if (existing != null) return MediaResult.Failure(Problem.AlreadyInFolder(name, existing.name))
        return MediaResult.Success(saved)
    }

    /**
     * Deletes [work] and everything in it, trying again after [cleanupRetryDelay]
     * when it fails. Returns why the last try failed, or null. It also runs, and
     * waits, after a cancellation.
     */
    private suspend fun removeWorkDirectory(work: Path): Exception? = withContext(NonCancellable) {
        var failure: Exception? = null
        for (attempt in 1..cleanupAttempts) {
            if (attempt > 1) delay(cleanupRetryDelay)
            failure = try {
                deleteRecursively(work)
                return@withContext null
            } catch (e: IOException) {
                e
            } catch (e: SecurityException) {
                e
            }
        }
        failure
    }

    private fun MediaResult.withLeftover(work: Path, leftover: Exception?): MediaResult {
        if (leftover == null) return this
        return when (this) {
            // The download worked: its file is saved. A leftover work folder does not change that.
            is MediaResult.Success -> this
            is MediaResult.Failure -> copy(leftover = Leftover.TemporaryFolder(work.toString(), leftover.message))
        }
    }

    companion object {
        /** Name start of the work folder each download makes in the output folder. */
        const val WORK_DIRECTORY_PREFIX = ".jaitsizazu-"
    }
}

/** Whether [a] and [b] are one file. Two paths of which one no longer exists are not. */
private fun isSameExistingFile(a: Path, b: Path): Boolean =
    try {
        Files.isSameFile(a, b)
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

/** Whether two file names are equal once Unicode-normalised and with case ignored. */
private fun sameNameIgnoringCase(a: String, b: String): Boolean =
    Normalizer.normalize(a, Normalizer.Form.NFC).equals(Normalizer.normalize(b, Normalizer.Form.NFC), ignoreCase = true)
