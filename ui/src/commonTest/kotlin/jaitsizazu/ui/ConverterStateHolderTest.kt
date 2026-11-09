package jaitsizazu.ui

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.ConvertMedia
import jaitsizazu.core.Leftover
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class ConverterStateHolderTest {

    private val noFfmpeg = Problem.ToolNotFound(Tool.FFMPEG, "JAITSIZAZU_FFMPEG", "bin/ffmpeg")

    /** Waits for [gate] before answering, so tests can look at the Converting state. */
    private class FakeConverter(
        private val gate: CompletableDeferred<Unit>? = null,
        private val answer: (String) -> MediaResult = { MediaResult.Success(it) },
    ) : MediaConverter {
        val requests = mutableListOf<ConversionRequest>()

        override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
            requests += request
            gate?.await()
            return answer(outputPath)
        }
    }

    /** Hangs on its first [hangs] calls until cancelled, like ffmpeg on an unreachable share. */
    private class HangingConverter(private val hangs: Int = 1) : MediaConverter {
        var calls = 0
        var cancelled = false

        override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
            if (++calls > hangs) return MediaResult.Success(outputPath)
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
    }

    private val memo = "/voice/memo.ogg"

    private fun TestScope.holder(converter: MediaConverter, workContext: CoroutineContext = EmptyCoroutineContext) =
        ConverterStateHolder(ConvertMedia(converter) { it == memo }, this, workContext)

    @Test
    fun startsWithNoFileWavAndIdle() = runTest {
        val state = holder(FakeConverter()).state.value

        assertEquals(ConverterState(selectedFile = null, format = AudioFormat.WAV, status = ConversionStatus.Idle), state)
        assertFalse(state.canConvert)
    }

    @Test
    fun doesNothingWithoutAFile() = runTest {
        val converter = FakeConverter()
        val holder = holder(converter)

        holder.convert()
        advanceUntilIdle()

        assertTrue(converter.requests.isEmpty())
        assertEquals(ConversionStatus.Idle, holder.state.value.status)
    }

    @Test
    fun convertsTheSelectedFileToTheSelectedFormat() = runTest {
        val gate = CompletableDeferred<Unit>()
        val converter = FakeConverter(gate)
        val holder = holder(converter)
        holder.selectFile(memo)
        holder.selectFormat(AudioFormat.MP3)

        holder.convert()
        advanceUntilIdle()
        assertEquals(ConversionStatus.Converting, holder.state.value.status)
        assertFalse(holder.state.value.canConvert)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(ConversionStatus.Done("/voice/memo.mp3"), holder.state.value.status)
        assertEquals(listOf(ConversionRequest(memo, AudioFormat.MP3)), converter.requests)
    }

    @Test
    fun showsTheFailureReason() = runTest {
        val leftover = Leftover.UnfinishedFile("/voice/memo.wav", "busy")
        val holder = holder(FakeConverter { MediaResult.Failure(Problem.ConversionFailed(1, "ffmpeg failed"), leftover) })
        holder.selectFile(memo)

        holder.convert()
        advanceUntilIdle()

        assertEquals(ConversionStatus.Failed(Problem.ConversionFailed(1, "ffmpeg failed"), leftover), holder.state.value.status)
    }

    @Test
    fun turnsAnUnexpectedErrorIntoAFailure() = runTest {
        val holder = holder(FakeConverter { error("boom") })
        holder.selectFile(memo)

        holder.convert()
        advanceUntilIdle()

        assertEquals(ConversionStatus.Failed(Problem.Unexpected("boom")), holder.state.value.status)
    }

    @Test
    fun ignoresActionsWhileConverting() = runTest {
        val gate = CompletableDeferred<Unit>()
        val converter = FakeConverter(gate)
        val holder = holder(converter)
        holder.selectFile(memo)
        holder.convert()
        advanceUntilIdle()

        holder.convert()
        holder.selectFile("/voice/other.ogg")
        holder.selectFormat(AudioFormat.FLAC)
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, converter.requests.size)
        assertEquals(memo, holder.state.value.selectedFile)
        assertEquals(AudioFormat.WAV, holder.state.value.format)
    }

    @Test
    fun choosingAnotherFileClearsTheLastResult() = runTest {
        val holder = holder(FakeConverter())
        holder.selectFile(memo)
        holder.convert()
        advanceUntilIdle()

        holder.selectFile("/voice/other.ogg")

        assertEquals(ConversionStatus.Idle, holder.state.value.status)
        assertEquals("/voice/other.ogg", holder.state.value.selectedFile)
    }

    @Test
    fun stillConvertsWhenFfmpegLookedMissingAtStartup() = runTest {
        // The converter looks for ffmpeg again, so the user can fix it without a restart.
        val converter = FakeConverter()
        val holder = holder(converter)
        holder.showSetupWarnings(listOf(noFfmpeg))
        holder.selectFile(memo)
        assertEquals(listOf(noFfmpeg), holder.state.value.setupWarnings)
        assertTrue(holder.state.value.canConvert)

        holder.convert()
        advanceUntilIdle()

        assertEquals(1, converter.requests.size)
        assertEquals(ConversionStatus.Done("/voice/memo.wav"), holder.state.value.status)
        assertEquals(emptyList(), holder.state.value.setupWarnings, "a conversion that worked proves ffmpeg is there")
    }

    @Test
    fun keepsTheSetupWarningWhenTheConversionFails() = runTest {
        val holder = holder(FakeConverter { MediaResult.Failure(noFfmpeg) })
        holder.showSetupWarnings(listOf(noFfmpeg))
        holder.selectFile(memo)

        holder.convert()
        advanceUntilIdle()

        assertEquals(listOf(noFfmpeg), holder.state.value.setupWarnings)
    }

    @Test
    fun cancelStopsAStuckConversion() = runTest {
        val converter = HangingConverter()
        val holder = holder(converter)
        holder.selectFile(memo)
        holder.convert()
        advanceUntilIdle()
        assertEquals(ConversionStatus.Converting, holder.state.value.status)

        holder.cancel()
        assertEquals(ConversionStatus.Cancelled, holder.state.value.status)
        advanceUntilIdle()

        assertTrue(converter.cancelled, "the converter was not cancelled")
        assertEquals(ConversionStatus.Cancelled, holder.state.value.status)
        assertTrue(holder.state.value.canConvert)
    }

    @Test
    fun convertsAgainAfterCancelling() = runTest {
        val converter = HangingConverter(hangs = 1)
        val holder = holder(converter)
        holder.selectFile(memo)
        holder.convert()
        advanceUntilIdle()
        holder.cancel()
        advanceUntilIdle()

        holder.convert()
        advanceUntilIdle()

        assertEquals(2, converter.calls)
        assertEquals(ConversionStatus.Done("/voice/memo.wav"), holder.state.value.status)
    }

    @Test
    fun aResultThatArrivesAfterCancelIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        // Ignores cancellation, like an engine that finishes anyway.
        val stubborn = object : MediaConverter {
            override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
                withContext(NonCancellable) { gate.await() }
                return MediaResult.Success(outputPath)
            }
        }
        val holder = holder(stubborn)
        holder.selectFile(memo)
        holder.convert()
        advanceUntilIdle()

        holder.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(ConversionStatus.Cancelled, holder.state.value.status)
    }

    @Test
    fun cancelDoesNothingWhenNotConverting() = runTest {
        val holder = holder(FakeConverter())
        holder.selectFile(memo)
        holder.convert()
        advanceUntilIdle()

        holder.cancel()

        assertEquals(ConversionStatus.Done("/voice/memo.wav"), holder.state.value.status)
    }

    @Test
    fun runsTheConversionInTheWorkContext() = runTest {
        // Main passes Dispatchers.IO, so file checks and ffmpeg lookups stay off the UI thread.
        var seen: String? = null
        val converter = object : MediaConverter {
            override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
                seen = currentCoroutineContext()[CoroutineName]?.name
                return MediaResult.Success(outputPath)
            }
        }
        val holder = holder(converter, workContext = CoroutineName("work"))
        holder.selectFile(memo)

        holder.convert()
        advanceUntilIdle()

        assertEquals("work", seen)
    }
}
