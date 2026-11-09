package jaitsizazu.engine.desktop

import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaResult
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop adapter for [MediaConverter]: runs ffmpeg as a subprocess.
 *
 * [locateFfmpeg] is asked on every conversion, so a missing ffmpeg becomes a
 * [MediaResult.Failure] that tells the user how to provide it. An output that
 * already exists is never overwritten: [fileExists] is checked first, and
 * ffmpeg runs with `-n` in case the file appears in the meantime.
 */
class FfmpegConverter(
    private val locateFfmpeg: () -> FfmpegLookup,
    private val runner: ProcessRunner,
    private val fileExists: (String) -> Boolean = { Files.exists(Path.of(it)) },
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
        }
        return when {
            // With -n, ffmpeg 6.1 exits with 0 even when it refuses to overwrite.
            REFUSED_TO_OVERWRITE in outcome.stderrTail -> alreadyExists(outputPath)
            outcome.exitCode == 0 -> MediaResult.Success(outputPath)
            else -> MediaResult.Failure(failureReason(outcome))
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
