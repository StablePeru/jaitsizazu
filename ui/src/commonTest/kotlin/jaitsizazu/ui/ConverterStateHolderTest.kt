package jaitsizazu.ui

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.ConvertMedia
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class ConverterStateHolderTest {

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

    private val memo = "/voice/memo.ogg"

    private fun TestScope.holder(converter: MediaConverter, setupProblem: String? = null) =
        ConverterStateHolder(ConvertMedia(converter) { it == memo }, this, setupProblem)

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
        val holder = holder(FakeConverter { MediaResult.Failure("ffmpeg failed") })
        holder.selectFile(memo)

        holder.convert()
        advanceUntilIdle()

        assertEquals(ConversionStatus.Failed("ffmpeg failed"), holder.state.value.status)
    }

    @Test
    fun turnsAnUnexpectedErrorIntoAFailure() = runTest {
        val holder = holder(FakeConverter { error("boom") })
        holder.selectFile(memo)

        holder.convert()
        advanceUntilIdle()

        val status = holder.state.value.status
        assertTrue(status is ConversionStatus.Failed && "boom" in status.reason, "$status")
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
    fun cannotConvertWhenTheEngineIsMissing() = runTest {
        val converter = FakeConverter()
        val holder = holder(converter, setupProblem = "ffmpeg was not found.")
        holder.selectFile(memo)

        holder.convert()
        advanceUntilIdle()

        assertEquals("ffmpeg was not found.", holder.state.value.setupProblem)
        assertFalse(holder.state.value.canConvert)
        assertTrue(converter.requests.isEmpty())
    }
}
