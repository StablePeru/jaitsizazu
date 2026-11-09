package jaitsizazu.engine.desktop

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FfmpegConverterTest {

    private class FakeRunner(private val answer: () -> ProcessOutcome) : ProcessRunner {
        val commands = mutableListOf<List<String>>()

        override suspend fun run(command: List<String>): ProcessOutcome {
            commands += command
            return answer()
        }
    }

    private val found = { FfmpegLookup.Found("/usr/bin/ffmpeg") }
    private val request = ConversionRequest("/voice/memo.ogg", AudioFormat.WAV)

    @Test
    fun runsFfmpegWithTheBuiltArguments() = runTest {
        val runner = FakeRunner { ProcessOutcome(0, "") }

        val result = FfmpegConverter(found, runner).convert(request, "/voice/memo.wav")

        assertEquals(MediaResult.Success("/voice/memo.wav"), result)
        assertEquals(
            listOf("/usr/bin/ffmpeg") + FfmpegArguments.build("/voice/memo.ogg", "/voice/memo.wav", AudioFormat.WAV),
            runner.commands.single(),
        )
    }

    @Test
    fun mapsANonZeroExitToAFailureWithTheStderrTail() = runTest {
        val runner = FakeRunner { ProcessOutcome(1, "memo.ogg: Invalid data found when processing input") }

        val result = FfmpegConverter(found, runner).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("exit code 1" in result.reason, result.reason)
        assertTrue("Invalid data found" in result.reason, result.reason)
    }

    @Test
    fun reportsAMissingFfmpegWithoutRunningAnything() = runTest {
        val runner = FakeRunner { error("must not run") }
        val missing = { FfmpegLookup.NotFound("ffmpeg was not found.") }

        val result = FfmpegConverter(missing, runner).convert(request, "/voice/memo.wav")

        assertEquals(MediaResult.Failure("ffmpeg was not found."), result)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun refusesAnOutputThatAlreadyExistsWithoutRunningAnything() = runTest {
        val runner = FakeRunner { error("must not run") }

        val result = FfmpegConverter(found, runner, fileExists = { true }).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("already exists" in result.reason, result.reason)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun treatsFfmpegRefusingToOverwriteAsAFailureEvenWithExitCodeZero() = runTest {
        // ffmpeg 6.1 with -n exits with 0 when the output appeared in the meantime.
        val runner = FakeRunner { ProcessOutcome(0, "File '/voice/memo.wav' already exists. Exiting.") }

        val result = FfmpegConverter(found, runner).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("already exists" in result.reason, result.reason)
    }

    @Test
    fun reportsAProcessThatCannotStart() = runTest {
        val runner = FakeRunner { throw IOException("error=13, Permission denied") }

        val result = FfmpegConverter(found, runner).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("Could not start ffmpeg" in result.reason, result.reason)
        assertTrue("Permission denied" in result.reason, result.reason)
    }
}
