package jaitsizazu.engine.desktop

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
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
    private val deleted = mutableListOf<String>()

    private fun converter(runner: ProcessRunner) =
        FfmpegConverter(found, runner, fileExists = { false }, deleteFile = { deleted += it })

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

    @Test
    fun deletesThePartialOutputWhenFfmpegFails() = runTest {
        val runner = FakeRunner { ProcessOutcome(1, "Error while decoding stream") }

        val result = converter(runner).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertEquals(listOf("/voice/memo.wav"), deleted)
    }

    @Test
    fun keepsTheOutputWhenTheConversionSucceeds() = runTest {
        val result = converter(FakeRunner { ProcessOutcome(0, "") }).convert(request, "/voice/memo.wav")

        assertEquals(MediaResult.Success("/voice/memo.wav"), result)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun neverDeletesAFileThatFfmpegRefusedToOverwrite() = runTest {
        // That file belongs to someone else: it appeared before ffmpeg started.
        for (exitCode in listOf(0, 1)) {
            val runner = FakeRunner { ProcessOutcome(exitCode, "File '/voice/memo.wav' already exists. Exiting.") }

            assertIs<MediaResult.Failure>(converter(runner).convert(request, "/voice/memo.wav"))
        }
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun deletesNothingWhenFfmpegCannotStart() = runTest {
        val runner = FakeRunner { throw IOException("error=2, No such file or directory") }

        assertIs<MediaResult.Failure>(converter(runner).convert(request, "/voice/memo.wav"))
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun deletesThePartialOutputAndRethrowsWhenCancelled() = runTest {
        val hanging = object : ProcessRunner {
            override suspend fun run(command: List<String>): ProcessOutcome = awaitCancellation()
        }
        var thrown: Throwable? = null
        val job = launch {
            try {
                converter(hanging).convert(request, "/voice/memo.wav")
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        testScheduler.advanceUntilIdle()

        job.cancel()
        job.join()

        assertIs<CancellationException>(thrown)
        assertEquals(listOf("/voice/memo.wav"), deleted)
    }

    @Test
    fun aFailedDeleteDoesNotHideTheConversionFailure() = runTest {
        val runner = FakeRunner { ProcessOutcome(1, "Error while decoding stream") }
        val converter = FfmpegConverter(found, runner, fileExists = { false }, deleteFile = { throw IOException("busy") })

        val result = converter.convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("exit code 1" in result.reason, result.reason)
    }
}
