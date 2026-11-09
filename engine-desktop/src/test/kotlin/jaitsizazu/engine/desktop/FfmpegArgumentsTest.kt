package jaitsizazu.engine.desktop

import jaitsizazu.core.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FfmpegArgumentsTest {

    private fun argsFor(format: AudioFormat) = FfmpegArguments.build("in.ogg", "out.x", format)

    /** The arguments after `-c:a`, without the output path at the end. */
    private fun codecPart(format: AudioFormat): List<String> {
        val args = argsFor(format)
        return args.subList(args.indexOf("-c:a"), args.lastIndex)
    }

    @Test
    fun buildsTheFullListForWav() {
        assertEquals(
            listOf(
                "-hide_banner", "-nostdin", "-loglevel", "error", "-n",
                "-i", "in.ogg", "-vn", "-c:a", "pcm_s16le", "out.x",
            ),
            argsFor(AudioFormat.WAV),
        )
    }

    @Test
    fun usesTheCodecOfEachFormat() {
        assertEquals(listOf("-c:a", "pcm_s16le"), codecPart(AudioFormat.WAV))
        assertEquals(listOf("-c:a", "libmp3lame", "-q:a", "2"), codecPart(AudioFormat.MP3))
        assertEquals(listOf("-c:a", "flac"), codecPart(AudioFormat.FLAC))
        assertEquals(listOf("-c:a", "libvorbis", "-q:a", "5"), codecPart(AudioFormat.OGG_VORBIS))
    }

    @Test
    fun neverOverwritesAndDropsVideoForEveryFormat() {
        for (format in AudioFormat.entries) {
            val args = argsFor(format)
            assertTrue("-n" in args, "$format: -n missing")
            assertFalse("-y" in args, "$format: -y present")
            assertTrue("-vn" in args, "$format: -vn missing")
            assertTrue("-nostdin" in args, "$format: -nostdin missing")
        }
    }

    @Test
    fun takesTheInputAfterDashIAndPutsTheOutputLast() {
        for (format in AudioFormat.entries) {
            val args = FfmpegArguments.build("C:\\voice notes\\memo.ogg", "C:\\voice notes\\memo.wav", format)
            assertEquals("C:\\voice notes\\memo.ogg", args[args.indexOf("-i") + 1])
            assertEquals("C:\\voice notes\\memo.wav", args.last())
        }
    }
}
