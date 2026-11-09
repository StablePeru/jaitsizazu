package jaitsizazu.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OutputPathTest {

    @Test
    fun keepsTheDirectoryAndReplacesTheExtension() {
        assertEquals("/home/me/memo.wav", outputPath("/home/me/memo.opus", AudioFormat.WAV))
    }

    @Test
    fun handlesWindowsSeparators() {
        assertEquals("C:\\Users\\me\\memo.mp3", outputPath("C:\\Users\\me\\memo.ogg", AudioFormat.MP3))
    }

    @Test
    fun splitsOnTheLastSeparatorOfEitherKind() {
        assertEquals("a\\b/memo.flac", outputPath("a\\b/memo.ogg", AudioFormat.FLAC))
        assertEquals("a/b\\memo.flac", outputPath("a/b\\memo.ogg", AudioFormat.FLAC))
    }

    @Test
    fun ignoresDotsInTheDirectory() {
        assertEquals("v1.2/memo.wav", outputPath("v1.2/memo", AudioFormat.WAV))
        assertEquals("C:\\my.music\\memo.wav", outputPath("C:\\my.music\\memo", AudioFormat.WAV))
    }

    @Test
    fun treatsALeadingDotInsideADirectoryAsPartOfTheName() {
        assertEquals("a/.hidden.wav", outputPath("a/.hidden", AudioFormat.WAV))
    }

    @Test
    fun acceptsABareFileName() {
        assertEquals("memo.wav", outputPath("memo.opus", AudioFormat.WAV))
    }

    @Test
    fun rejectsAPathThatEndsInASeparator() {
        assertFailsWith<IllegalArgumentException> { outputPath("music/", AudioFormat.WAV) }
    }

    @Test
    fun fileNameOfReturnsTheLastSegment() {
        assertEquals("memo.opus", fileNameOf("/home/me/memo.opus"))
        assertEquals("memo.opus", fileNameOf("C:\\Users\\me\\memo.opus"))
        assertEquals("memo.opus", fileNameOf("memo.opus"))
    }
}

class FirstFreePathTest {

    @Test
    fun keepsThePathWhenItIsFree() {
        assertEquals("dir/memo.wav", firstFreePath("dir/memo.wav") { false })
    }

    @Test
    fun addsACounterBeforeTheExtensionWhenThePathIsTaken() {
        val taken = setOf("dir/memo.wav")
        assertEquals("dir/memo (1).wav", firstFreePath("dir/memo.wav") { it in taken })
    }

    @Test
    fun countsUpUntilAPathIsFree() {
        val taken = setOf("dir/memo.wav", "dir/memo (1).wav", "dir/memo (2).wav")
        assertEquals("dir/memo (3).wav", firstFreePath("dir/memo.wav") { it in taken })
    }

    @Test
    fun ignoresDotsInTheDirectory() {
        val taken = setOf("v1.2/memo.wav")
        assertEquals("v1.2/memo (1).wav", firstFreePath("v1.2/memo.wav") { it in taken })
    }

    @Test
    fun keepsAHiddenFileHidden() {
        val taken = setOf("a/.hidden.wav")
        assertEquals("a/.hidden (1).wav", firstFreePath("a/.hidden.wav") { it in taken })
    }

    @Test
    fun givesUpWhenEveryCandidateIsTaken() {
        assertNull(firstFreePath("memo.wav") { true })
    }
}
