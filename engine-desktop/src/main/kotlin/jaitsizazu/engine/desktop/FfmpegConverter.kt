package jaitsizazu.engine.desktop

import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * Desktop adapter for [MediaConverter]: runs ffmpeg as a subprocess.
 *
 * [locateFfmpeg] is asked on every conversion, so a missing ffmpeg becomes a
 * [MediaResult.Failure] that tells the user how to provide it, and ffmpeg
 * installed while the app runs is picked up on the next try.
 *
 * Before ffmpeg runs, the output path is claimed with [claimFile], which must
 * create a new, empty file and fail if one already exists (the default uses
 * [Files.createFile]). The check and the creation are one atomic step, so an
 * existing file is never overwritten, and ffmpeg (run with `-y`) only ever
 * writes into the file this conversion created.
 *
 * When ffmpeg cannot start, fails or the coroutine is cancelled, that claimed
 * file is removed with [deleteFile], so no partial output sits next to the
 * original. A file that could not be removed is reported: in the failure
 * reason, or as a suppressed exception of the cancellation.
 */
class FfmpegConverter(
    private val locateFfmpeg: () -> FfmpegLookup,
    private val runner: ProcessRunner,
    private val claimFile: (String) -> Unit = { Files.createFile(Path.of(it)) },
    private val deleteFile: (String) -> Unit = { Files.deleteIfExists(Path.of(it)) },
) : MediaConverter {

    override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
        val ffmpeg = when (val lookup = locateFfmpeg()) {
            is FfmpegLookup.Found -> lookup.path
            is FfmpegLookup.NotFound -> return MediaResult.Failure(lookup.message)
        }
        claim(outputPath)?.let { return it }

        val command = listOf(ffmpeg) + FfmpegArguments.build(request.inputPath, outputPath, request.target)
        val outcome = try {
            runner.run(command)
        } catch (e: IOException) {
            return failureAfterCleanup("Could not start ffmpeg ($ffmpeg): ${e.message}", outputPath)
        } catch (e: CancellationException) {
            // The runner returns only once ffmpeg has exited, so the file is no longer in use.
            removeClaimedOutput(outputPath)?.let(e::addSuppressed)
            throw e
        }
        return if (outcome.exitCode == 0) {
            MediaResult.Success(outputPath)
        } else {
            failureAfterCleanup(failureReason(outcome), outputPath)
        }
    }

    /** Creates [outputPath] for this conversion. Returns why it could not, or null when it is ours. */
    private fun claim(outputPath: String): MediaResult.Failure? =
        try {
            claimFile(outputPath)
            null
        } catch (_: FileAlreadyExistsException) {
            MediaResult.Failure("$outputPath already exists. Nothing was overwritten.")
        } catch (e: IOException) {
            MediaResult.Failure("Could not create $outputPath: ${e.message}")
        } catch (e: SecurityException) {
            MediaResult.Failure("Could not create $outputPath: ${e.message}")
        } catch (e: InvalidPathException) {
            MediaResult.Failure("Could not create $outputPath: ${e.message}")
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

    /** Removes the claimed output; a file that stays behind is added to [reason], never hides it. */
    private fun failureAfterCleanup(reason: String, outputPath: String): MediaResult.Failure {
        val leftover = removeClaimedOutput(outputPath) ?: return MediaResult.Failure(reason)
        return MediaResult.Failure("$reason\nThe unfinished file $outputPath could not be removed: ${leftover.message}")
    }

    private fun failureReason(outcome: ProcessOutcome): String {
        val summary = "The conversion failed (ffmpeg exit code ${outcome.exitCode})."
        return if (outcome.stderrTail.isBlank()) summary else "$summary\n${outcome.stderrTail}"
    }
}
