package jaitsizazu.engine.desktop

import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.Leftover
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * Desktop adapter for [MediaConverter]: runs ffmpeg as a subprocess.
 *
 * [locateFfmpeg] is asked on every conversion, so a missing ffmpeg becomes a
 * [MediaResult.Failure] that the UI turns into how to provide it, and ffmpeg
 * installed while the app runs is picked up on the next try.
 *
 * Before ffmpeg runs, the output path is claimed with [claimFile], which must
 * create a new, empty file and fail if one already exists (the default uses
 * [Files.createFile]). The check and the creation are one atomic step, so an
 * existing file is never overwritten, and ffmpeg (run with `-y`) only ever
 * writes into the file this conversion created.
 *
 * When ffmpeg cannot start, fails, the coroutine is cancelled or anything else
 * throws, that claimed file is removed with [deleteFile], so no partial output
 * sits next to the original. A file that could not be removed is reported: as
 * the failure's leftover, or as a suppressed exception of what was thrown.
 */
class FfmpegConverter(
    private val locateFfmpeg: () -> ToolLookup,
    private val runner: ProcessRunner,
    // Paths.get, not Path.of: Android only has Path.of from API level 34.
    private val claimFile: (String) -> Unit = { Files.createFile(Paths.get(it)) },
    private val deleteFile: (String) -> Unit = { Files.deleteIfExists(Paths.get(it)) },
) : MediaConverter {

    override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
        val ffmpeg = when (val lookup = locateFfmpeg()) {
            is ToolLookup.Found -> lookup.path
            is ToolLookup.NotFound -> return MediaResult.Failure(lookup.problem)
        }
        val command = listOf(ffmpeg) + FfmpegArguments.build(request.inputPath, outputPath, request.target)
        claim(outputPath)?.let { return it }

        val outcome = try {
            runner.run(command)
        } catch (e: IOException) {
            return failureAfterCleanup(Problem.ToolDidNotStart(Tool.FFMPEG, ffmpeg, e.message), outputPath)
        } catch (e: Throwable) {
            // A cancellation, or an error nobody expected. On cancellation SystemProcessRunner
            // kills ffmpeg and waits a bounded time for it to exit, so on Windows a slow
            // ffmpeg may still hold the file: then the delete fails and is attached to `e`.
            removeClaimedOutput(outputPath)?.let(e::addSuppressed)
            throw e
        }
        return if (outcome.exitCode == 0) {
            MediaResult.Success(outputPath)
        } else {
            failureAfterCleanup(Problem.ConversionFailed(outcome.exitCode, outcome.stderrTail), outputPath)
        }
    }

    /** Creates [outputPath] for this conversion. Returns why it could not, or null when it is ours. */
    private fun claim(outputPath: String): MediaResult.Failure? =
        try {
            claimFile(outputPath)
            null
        } catch (_: FileAlreadyExistsException) {
            MediaResult.Failure(Problem.FileAlreadyExists(outputPath))
        } catch (e: IOException) {
            MediaResult.Failure(Problem.CannotCreateFile(outputPath, e.message))
        } catch (e: SecurityException) {
            MediaResult.Failure(Problem.CannotCreateFile(outputPath, e.message))
        } catch (e: InvalidPathException) {
            MediaResult.Failure(Problem.CannotCreateFile(outputPath, e.message))
        }

    /** Deletes the output this conversion claimed. Returns why it could not, or null. */
    private fun removeClaimedOutput(outputPath: String): Exception? =
        try {
            deleteFile(outputPath)
            null
        } catch (e: IOException) {
            e
        } catch (e: SecurityException) {
            e
        }

    /** Removes the claimed output; a file that stays behind is reported next to [problem], never hides it. */
    private fun failureAfterCleanup(problem: Problem, outputPath: String): MediaResult.Failure {
        val leftover = removeClaimedOutput(outputPath) ?: return MediaResult.Failure(problem)
        return MediaResult.Failure(problem, Leftover.UnfinishedFile(outputPath, leftover.message))
    }
}
