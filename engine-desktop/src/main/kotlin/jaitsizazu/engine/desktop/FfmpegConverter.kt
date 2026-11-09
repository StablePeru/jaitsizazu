package jaitsizazu.engine.desktop

import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop adapter for [MediaConverter]: runs ffmpeg as a subprocess.
 *
 * [locateFfmpeg] is asked on every conversion, so a missing ffmpeg becomes a
 * [MediaResult.Failure] that tells the user how to provide it, and ffmpeg
 * installed while the app runs is picked up on the next try. An output that
 * already exists is never overwritten: [fileExists] is checked first, and
 * ffmpeg runs with `-n` in case the file appears in the meantime.
 *
 * When ffmpeg fails or the coroutine is cancelled, the partial output is
 * removed with [deleteFile], so it does not sit next to the original. A file
 * that ffmpeg refused to overwrite is not ours and is never removed.
 */
class FfmpegConverter(
    private val locateFfmpeg: () -> FfmpegLookup,
    private val runner: ProcessRunner,
    private val fileExists: (String) -> Boolean = { Files.exists(Path.of(it)) },
    private val deleteFile: (String) -> Unit = { Files.deleteIfExists(Path.of(it)) },
) : MediaConverter {

    override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
        val ffmpeg = when (val lookup = locateFfmpeg()) {
            is FfmpegLookup.Found -> lookup.path
            is FfmpegLookup.NotFound -> return MediaResult.Failure(lookup.message)
        }
        if (fileExists(outputPath)) return alreadyExists(outputPath)

        val command = listOf(ffmpeg) + FfmpegArguments.build(request.inputPath, outputPath, request.target)
        val outcome = try {
            runner.run(command)
        } catch (e: IOException) {
            return MediaResult.Failure("Could not start ffmpeg ($ffmpeg): ${e.message}")
        } catch (e: CancellationException) {
            removePartialOutput(outputPath)
            throw e
        }
        return when {
            // With -n, ffmpeg 6.1 exits with 0 even when it refuses to overwrite.
            REFUSED_TO_OVERWRITE in outcome.stderrTail -> alreadyExists(outputPath)
            outcome.exitCode == 0 -> MediaResult.Success(outputPath)
            else -> {
                removePartialOutput(outputPath)
                MediaResult.Failure(failureReason(outcome))
            }
        }
    }

    /** Best effort: a file that cannot be removed must not hide why the conversion stopped. */
    private fun removePartialOutput(outputPath: String) {
        try {
            deleteFile(outputPath)
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        }
    }

    private fun alreadyExists(outputPath: String) =
        MediaResult.Failure("$outputPath already exists. Nothing was overwritten.")

    private fun failureReason(outcome: ProcessOutcome): String {
        val summary = "The conversion failed (ffmpeg exit code ${outcome.exitCode})."
        return if (outcome.stderrTail.isBlank()) summary else "$summary\n${outcome.stderrTail}"
    }

    private companion object {
        /** What ffmpeg prints when `-n` stops it from overwriting the output. */
        const val REFUSED_TO_OVERWRITE = "already exists. Exiting."
    }
}
