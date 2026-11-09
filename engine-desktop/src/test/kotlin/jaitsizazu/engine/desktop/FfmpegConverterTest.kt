package jaitsizazu.engine.desktop

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
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
    private val claimed = mutableListOf<String>()
    private val deleted = mutableListOf<String>()

    private val hanging = object : ProcessRunner {
        override suspend fun run(command: List<String>): ProcessOutcome = awaitCancellation()
    }

    private fun converter(
        runner: ProcessRunner,
        claimFile: (String) -> Unit = { claimed += it },
        deleteFile: (String) -> Unit = { deleted += it },
    ) = FfmpegConverter(found, runner, claimFile = claimFile, deleteFile = deleteFile)

    /** Starts a conversion with [converter], cancels it and returns what its caller caught. */
    private suspend fun TestScope.cancelledConversion(converter: FfmpegConverter): Throwable? {
        var thrown: Throwable? = null
        val job = launch {
            try {
                converter.convert(request, "/voice/memo.wav")
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        testScheduler.advanceUntilIdle()
        job.cancel()
        job.join()
        return thrown
    }

    @Test
    fun runsFfmpegWithTheBuiltArguments() = runTest {
        val runner = FakeRunner { ProcessOutcome(0, "") }

        val result = converter(runner).convert(request, "/voice/memo.wav")

        assertEquals(MediaResult.Success("/voice/memo.wav"), result)
        assertEquals(
            listOf("/usr/bin/ffmpeg") + FfmpegArguments.build("/voice/memo.ogg", "/voice/memo.wav", AudioFormat.WAV),
            runner.commands.single(),
        )
    }

    @Test
    fun claimsTheOutputBeforeRunningFfmpeg() = runTest {
        val events = mutableListOf<String>()
        val runner = object : ProcessRunner {
            override suspend fun run(command: List<String>): ProcessOutcome {
                events += "run"
                return ProcessOutcome(0, "")
            }
        }

        converter(runner, claimFile = { events += "claim $it" }).convert(request, "/voice/memo.wav")

        assertEquals(listOf("claim /voice/memo.wav", "run"), events)
    }

    @Test
    fun mapsANonZeroExitToAFailureWithTheStderrTail() = runTest {
        val runner = FakeRunner { ProcessOutcome(1, "memo.ogg: Invalid data found when processing input") }

        val result = converter(runner).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("exit code 1" in result.reason, result.reason)
        assertTrue("Invalid data found" in result.reason, result.reason)
    }

    @Test
    fun reportsAMissingFfmpegWithoutClaimingOrRunningAnything() = runTest {
        val runner = FakeRunner { error("must not run") }
        val missing = { FfmpegLookup.NotFound("ffmpeg was not found.") }

        val result = FfmpegConverter(missing, runner, claimFile = { claimed += it }).convert(request, "/voice/memo.wav")

        assertEquals(MediaResult.Failure("ffmpeg was not found."), result)
        assertTrue(runner.commands.isEmpty())
        assertTrue(claimed.isEmpty())
    }

    @Test
    fun refusesAnOutputThatAlreadyExistsWithoutRunningOrDeletingAnything() = runTest {
        val runner = FakeRunner { error("must not run") }

        val result = converter(runner, claimFile = { throw FileAlreadyExistsException(it) })
            .convert(request, "/voice/memo.wav")

        assertEquals(MediaResult.Failure("/voice/memo.wav already exists. Nothing was overwritten."), result)
        assertTrue(runner.commands.isEmpty())
        assertTrue(deleted.isEmpty(), "a file that was already there is not ours to delete")
    }

    @Test
    fun reportsAnOutputThatCannotBeCreatedWithoutRunningOrDeletingAnything() = runTest {
        val runner = FakeRunner { error("must not run") }

        val result = converter(runner, claimFile = { throw AccessDeniedException(it) })
            .convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("Could not create /voice/memo.wav" in result.reason, result.reason)
        assertTrue(runner.commands.isEmpty())
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun reportsAProcessThatCannotStartAndRemovesTheClaimedOutput() = runTest {
        val runner = FakeRunner { throw IOException("error=13, Permission denied") }

        val result = converter(runner).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("Could not start ffmpeg" in result.reason, result.reason)
        assertTrue("Permission denied" in result.reason, result.reason)
        assertEquals(listOf("/voice/memo.wav"), deleted)
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
    fun deletesThePartialOutputAndRethrowsWhenCancelled() = runTest {
        val thrown = cancelledConversion(converter(hanging))

        assertIs<CancellationException>(thrown)
        assertEquals(listOf("/voice/memo.wav"), deleted)
    }

    @Test
    fun aFailedDeleteIsAttachedToTheCancellation() = runTest {
        val thrown = cancelledConversion(converter(hanging, deleteFile = { throw IOException("busy") }))

        assertIs<CancellationException>(thrown)
        val suppressed = thrown.suppressed.single()
        assertIs<IOException>(suppressed)
        assertEquals("busy", suppressed.message)
    }

    @Test
    fun aFailedDeleteIsReportedWithoutHidingTheConversionFailure() = runTest {
        val runner = FakeRunner { ProcessOutcome(1, "Error while decoding stream") }

        val result = converter(runner, deleteFile = { throw IOException("busy") }).convert(request, "/voice/memo.wav")

        assertIs<MediaResult.Failure>(result)
        assertTrue("exit code 1" in result.reason, result.reason)
        assertTrue("/voice/memo.wav could not be removed: busy" in result.reason, result.reason)
    }
}
