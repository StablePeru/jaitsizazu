package jaitsizazu.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConvertMediaTest {

    /** Records every call and answers with [result], or with Success on the output path. */
    private class FakeConverter(private val result: MediaResult? = null) : MediaConverter {
        val calls = mutableListOf<Pair<ConversionRequest, String>>()

        override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
            calls += request to outputPath
            return result ?: MediaResult.Success(outputPath)
        }
    }

    private fun existing(vararg paths: String): (String) -> Boolean = { it in paths }

    @Test
    fun convertsNextToTheInputWithTheTargetExtension() = runTest {
        val converter = FakeConverter()
        val convert = ConvertMedia(converter, existing("/voice/memo.ogg"))

        val result = convert(ConversionRequest("/voice/memo.ogg", AudioFormat.WAV))

        assertEquals(MediaResult.Success("/voice/memo.wav"), result)
        assertEquals(
            listOf(ConversionRequest("/voice/memo.ogg", AudioFormat.WAV) to "/voice/memo.wav"),
            converter.calls,
        )
    }

    @Test
    fun neverOverwritesAnExistingFile() = runTest {
        val converter = FakeConverter()
        val convert = ConvertMedia(converter, existing("/voice/memo.ogg", "/voice/memo.wav"))

        val result = convert(ConversionRequest("/voice/memo.ogg", AudioFormat.WAV))

        assertEquals(MediaResult.Success("/voice/memo (1).wav"), result)
    }

    @Test
    fun neverOverwritesTheInputWhenTheFormatIsTheSame() = runTest {
        val converter = FakeConverter()
        val convert = ConvertMedia(converter, existing("/music/song.wav"))

        convert(ConversionRequest("/music/song.wav", AudioFormat.WAV))

        assertEquals("/music/song (1).wav", converter.calls.single().second)
    }

    @Test
    fun rejectsABlankInputWithoutCallingTheConverter() = runTest {
        val converter = FakeConverter()
        val convert = ConvertMedia(converter, existing())

        val result = convert(ConversionRequest("  ", AudioFormat.WAV))

        assertIs<MediaResult.Failure>(result)
        assertTrue(converter.calls.isEmpty())
    }

    @Test
    fun rejectsAMissingInputWithoutCallingTheConverter() = runTest {
        val converter = FakeConverter()
        val convert = ConvertMedia(converter, existing())

        val result = convert(ConversionRequest("/voice/gone.ogg", AudioFormat.WAV))

        assertIs<MediaResult.Failure>(result)
        assertTrue(result.reason.contains("gone.ogg"))
        assertTrue(converter.calls.isEmpty())
    }

    @Test
    fun rejectsAPathWithNoFileName() = runTest {
        val converter = FakeConverter()
        val convert = ConvertMedia(converter, existing("/voice/"))

        val result = convert(ConversionRequest("/voice/", AudioFormat.WAV))

        assertIs<MediaResult.Failure>(result)
        assertTrue(converter.calls.isEmpty())
    }

    @Test
    fun failsWhenNoFreeNameIsLeft() = runTest {
        val converter = FakeConverter()
        val convert = ConvertMedia(converter) { true }

        val result = convert(ConversionRequest("/voice/memo.ogg", AudioFormat.WAV))

        assertIs<MediaResult.Failure>(result)
        assertTrue(converter.calls.isEmpty())
    }

    @Test
    fun passesTheConverterFailureThrough() = runTest {
        val failure = MediaResult.Failure("ffmpeg failed")
        val convert = ConvertMedia(FakeConverter(failure), existing("/voice/memo.ogg"))

        assertEquals(failure, convert(ConversionRequest("/voice/memo.ogg", AudioFormat.MP3)))
    }
}
