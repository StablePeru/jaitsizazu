package jaitsizazu.engine.desktop

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.ConvertMedia
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the real ffmpeg. The Docker build image installs it, so this runs in
 * `./gradlew check`. Without ffmpeg on the machine, these tests fail on purpose.
 */
class FfmpegIntegrationTest {

    private lateinit var dir: File
    private lateinit var voiceNote: File
    private lateinit var ffmpeg: FfmpegLookup.Found
    private lateinit var converter: FfmpegConverter

    @BeforeTest
    fun setUp() = runBlocking {
        dir = Files.createTempDirectory("jaitsizazu-ffmpeg").toFile()
        val lookup = FfmpegLocator().locate()
        if (lookup !is FfmpegLookup.Found) fail("These tests need ffmpeg. ${(lookup as FfmpegLookup.NotFound).message}")
        ffmpeg = lookup
        converter = FfmpegConverter({ ffmpeg }, SystemProcessRunner())

        // A one-second Ogg Opus file, like a WhatsApp voice note.
        voiceNote = File(dir, "voice.ogg")
        val made = SystemProcessRunner().run(
            listOf(
                lookup.path, "-hide_banner", "-nostdin", "-loglevel", "error",
                "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
                "-c:a", "libopus", voiceNote.path,
            ),
        )
        assertEquals(0, made.exitCode, made.stderrTail)
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun convert(format: AudioFormat, output: File): MediaResult = runBlocking {
        converter.convert(ConversionRequest(voiceNote.path, format), output.path)
    }

    private fun File.startsWith(magic: String) =
        readBytes().copyOfRange(0, magic.length).decodeToString() == magic

    @Test
    fun convertsAVoiceNoteToWav() {
        val wav = File(dir, "voice.wav")

        assertEquals(MediaResult.Success(wav.path), convert(AudioFormat.WAV, wav))

        val bytes = wav.readBytes()
        assertTrue(wav.startsWith("RIFF"))
        assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())
        assertTrue(bytes.size > 44, "only ${bytes.size} bytes")
    }

    @Test
    fun convertsAVoiceNoteToMp3FlacAndOggVorbis() {
        for (format in listOf(AudioFormat.MP3, AudioFormat.FLAC, AudioFormat.OGG_VORBIS)) {
            val output = File(dir, "out.${format.extension}")

            assertEquals(MediaResult.Success(output.path), convert(format, output), "$format")
            assertTrue(output.length() > 0, "$format output is empty")
        }
        assertTrue(File(dir, "out.flac").startsWith("fLaC"))
        assertTrue(File(dir, "out.ogg").startsWith("OggS"))
    }

    @Test
    fun refusesToOverwriteAnExistingFile() {
        val taken = File(dir, "voice.wav").apply { writeText("keep me") }

        val result = convert(AudioFormat.WAV, taken)

        assertIs<MediaResult.Failure>(result)
        assertEquals("keep me", taken.readText())
    }

    @Test
    fun ffmpegItselfRefusesToOverwriteWhenTheCheckMissesTheFile() = runBlocking {
        // Simulates a file that appears between the check and the conversion.
        val blind = FfmpegConverter({ ffmpeg }, SystemProcessRunner(), fileExists = { false })
        val taken = File(dir, "voice.wav").apply { writeText("keep me") }

        val result = blind.convert(ConversionRequest(voiceNote.path, AudioFormat.WAV), taken.path)

        assertIs<MediaResult.Failure>(result)
        assertEquals("keep me", taken.readText())
    }

    @Test
    fun picksAFreeNameThroughTheUseCase() = runBlocking {
        val convertMedia = ConvertMedia(converter) { File(it).exists() }
        val request = ConversionRequest(voiceNote.path, AudioFormat.WAV)

        assertEquals(MediaResult.Success(File(dir, "voice.wav").path), convertMedia(request))
        assertEquals(MediaResult.Success(File(dir, "voice (1).wav").path), convertMedia(request))
    }
}
