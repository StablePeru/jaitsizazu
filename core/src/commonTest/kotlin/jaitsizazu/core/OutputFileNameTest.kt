package jaitsizazu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OutputFileNameTest {

    @Test
    fun replacesTheExtension() {
        assertEquals("memo.wav", outputFileName("memo.opus", AudioFormat.WAV))
    }

    @Test
    fun usesTheTargetExtension() {
        assertEquals("song.mp3", outputFileName("song.flac", AudioFormat.MP3))
        assertEquals("song.flac", outputFileName("song.wav", AudioFormat.FLAC))
        assertEquals("song.ogg", outputFileName("song.wav", AudioFormat.OGG_VORBIS))
    }

    @Test
    fun appendsTheExtensionWhenThereIsNone() {
        assertEquals("memo.wav", outputFileName("memo", AudioFormat.WAV))
    }

    @Test
    fun replacesOnlyTheLastExtensionWhenThereAreSeveralDots() {
        assertEquals("my.voice.note.wav", outputFileName("my.voice.note.opus", AudioFormat.WAV))
        assertEquals("archive.tar.mp3", outputFileName("archive.tar.gz", AudioFormat.MP3))
    }

    @Test
    fun treatsALeadingDotAsPartOfTheName() {
        assertEquals(".hidden.wav", outputFileName(".hidden", AudioFormat.WAV))
    }

    @Test
    fun dropsATrailingDot() {
        assertEquals("memo.wav", outputFileName("memo.", AudioFormat.WAV))
    }

    @Test
    fun normalisesTheCaseOfTheSameExtension() {
        assertEquals("memo.wav", outputFileName("memo.WAV", AudioFormat.WAV))
    }

    @Test
    fun rejectsABlankName() {
        assertFailsWith<IllegalArgumentException> { outputFileName("  ", AudioFormat.WAV) }
    }

    @Test
    fun rejectsAPathWithSlashes() {
        assertFailsWith<IllegalArgumentException> { outputFileName("v1.2/memo", AudioFormat.WAV) }
    }

    @Test
    fun rejectsAPathWithBackslashes() {
        assertFailsWith<IllegalArgumentException> { outputFileName("v1.2\\memo", AudioFormat.WAV) }
    }
}
