package jaitsizazu.engine.android

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FileNamesTest {

    @Test
    fun keepsAnOrdinaryName() {
        assertEquals("WhatsApp Audio 2026-11-09 at 10.15.opus", safeFileName("WhatsApp Audio 2026-11-09 at 10.15.opus"))
        assertEquals("Ahotsa ñ €.m4a", safeFileName("Ahotsa ñ €.m4a"))
    }

    @Test
    fun replacesSeparatorsControlAndForbiddenCharacters() {
        assertEquals("_.._etc_passwd", safeFileName("/../etc/passwd"))
        assertEquals("a_b_c_d_e_f_g_h_i_j.wav", safeFileName("a\\b:c*d?e\"f<g>h|i\u0000j.wav"))
        assertEquals("line_break.mp3", safeFileName("line\nbreak.mp3"))
    }

    @Test
    fun namesAnEmptyOrDotOnlyNameFile() {
        assertEquals("file", safeFileName(null))
        assertEquals("file", safeFileName("   "))
        assertEquals("file", safeFileName(".."))
    }

    @Test
    fun cutsALongNameBeforeItsExtension() {
        val name = safeFileName("ñ".repeat(300) + ".opus")

        assertTrue(name.endsWith(".opus"), name)
        assertTrue(name.toByteArray().size <= MAX_NAME_BYTES, "${name.toByteArray().size} bytes")
        assertEquals("ñ".repeat((MAX_NAME_BYTES - 5) / 2) + ".opus", name)
    }

    @Test
    fun neverCutsAnEmojiInHalf() {
        val name = safeFileName("😀".repeat(100) + ".mp3")

        assertEquals("😀".repeat((MAX_NAME_BYTES - 4) / 4) + ".mp3", name)
    }

    private val folder = Path.of("/storage/emulated/0/Download/Jaitsizazu")

    private fun creator(taken: Set<String>): (Path) -> Unit = { path ->
        if (path.fileName.toString() in taken) throw FileAlreadyExistsException(path.toString())
    }

    @Test
    fun claimsTheNameOrTheFirstFreeVariant() {
        assertEquals(folder.resolve("clip.mp3"), claimFreeFile(folder, "clip.mp3", creator(emptySet())))
        assertEquals(
            folder.resolve("clip (2).mp3"),
            claimFreeFile(folder, "clip.mp3", creator(setOf("clip.mp3", "clip (1).mp3"))),
        )
    }

    @Test
    fun givesUpWhenEveryVariantIsTaken() {
        assertFailsWith<IOException> { claimFreeFile(folder, "clip.mp3") { throw FileAlreadyExistsException(it.toString()) } }
    }
}
