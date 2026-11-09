package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.MediaDownloader
import jaitsizazu.core.MediaResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

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
 * case: success, failure or cancellation. A file that was already in the output
 * folder is never replaced (yt-dlp runs with `--no-overwrites`); when yt-dlp
 * names such a file as its result, the download is reported as a failure.
 */
class YtDlpDownloader(
    private val locateYtDlp: () -> ToolLookup,
    private val locateFfmpeg: () -> ToolLookup,
    private val locateDeno: () -> ToolLookup,
    private val runner: ProcessRunner,
    private val createWorkDirectory: (Path) -> Path = { Files.createTempDirectory(it, WORK_DIRECTORY_PREFIX) },
) : MediaDownloader {

    override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
        val ytDlp = when (val lookup = locateYtDlp()) {
            is ToolLookup.Found -> lookup.path
            is ToolLookup.NotFound -> return MediaResult.Failure(lookup.message)
        }
        val ffmpeg = when (val lookup = locateFfmpeg()) {
            is ToolLookup.Found -> lookup.path
            is ToolLookup.NotFound -> return MediaResult.Failure(lookup.message)
        }
        val deno = (locateDeno() as? ToolLookup.Found)?.path

        val output: Path
        val namesBefore: Set<String>
        val work: Path
        try {
            output = Path.of(request.outputDirectory)
            namesBefore = output.listDirectoryEntries().mapTo(HashSet()) { it.name }
            work = createWorkDirectory(output)
        } catch (e: IOException) {
            return MediaResult.Failure("Cannot save into ${request.outputDirectory}: ${e.message ?: e::class.simpleName}")
        } catch (e: InvalidPathException) {
            return MediaResult.Failure("Cannot save into ${request.outputDirectory}: ${e.message}")
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
            removeWorkDirectory(work)
            return MediaResult.Failure("Could not start yt-dlp ($ytDlp): ${e.message}")
        } catch (e: Throwable) {
            // A cancellation, or an error nobody expected: the runner has killed yt-dlp.
            removeWorkDirectory(work)?.let(e::addSuppressed)
            throw e
        }
        val leftover = removeWorkDirectory(work)
        return result(outcome, saved.get(), namesBefore).withLeftover(work, leftover)
    }

    private fun result(outcome: ProcessOutcome, saved: String?, namesBefore: Set<String>): MediaResult {
        if (outcome.exitCode != 0) {
            val summary = "The download failed (yt-dlp exit code ${outcome.exitCode})."
            // In quiet mode yt-dlp writes some of the progress lines we asked for to stderr.
            val errors = outcome.stderrTail.lines().filterNot { it.startsWith(YtDlpOutput.PREFIX) }.joinToString("\n")
            return MediaResult.Failure(if (errors.isBlank()) summary else "$summary\n$errors")
        }
        if (saved == null) return MediaResult.Failure("yt-dlp finished, but it reported no file.")
        val name = Path.of(saved).name
        if (name in namesBefore) {
            return MediaResult.Failure("$name is already in this folder. Nothing was overwritten.")
        }
        return MediaResult.Success(saved)
    }

    /** Deletes [work] and everything in it. Returns why it could not, or null. */
    @OptIn(ExperimentalPathApi::class)
    private fun removeWorkDirectory(work: Path): Exception? =
        try {
            work.deleteRecursively()
            null
        } catch (e: IOException) {
            e
        } catch (e: SecurityException) {
            e
        }

    private fun MediaResult.withLeftover(work: Path, leftover: Exception?): MediaResult {
        if (leftover == null) return this
        val note = "The temporary folder $work could not be removed: ${leftover.message}"
        return when (this) {
            is MediaResult.Success -> this
            is MediaResult.Failure -> MediaResult.Failure("$reason\n$note")
        }
    }

    companion object {
        /** Name start of the work folder each download makes in the output folder. */
        const val WORK_DIRECTORY_PREFIX = ".jaitsizazu-"
    }
}
