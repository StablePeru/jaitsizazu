package jaitsizazu.desktop

import jaitsizazu.engine.desktop.ToolLookup
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadSetupTest {

    private val found = ToolLookup.Found("/usr/bin/tool")

    @Test
    fun savesIntoTheDownloadsFolderOfTheUser() {
        val downloads = Path.of("/home/me", "Downloads")

        assertEquals(downloads, defaultDownloadDirectory(home = "/home/me", isDirectory = { it == downloads }))
    }

    @Test
    fun fallsBackOnTheHomeFolderWithoutADownloadsFolder() {
        assertEquals(Path.of("/home/me"), defaultDownloadDirectory(home = "/home/me", isDirectory = { false }))
    }

    @Test
    fun warnsAboutEveryMissingTool() {
        val warning = downloadSetupWarning(
            ytDlp = ToolLookup.NotFound("yt-dlp was not found."),
            ffmpeg = found,
            deno = ToolLookup.NotFound("deno was not found."),
        )

        assertEquals("yt-dlp was not found.\n\ndeno was not found.", warning)
    }

    @Test
    fun saysNothingWhenEveryToolIsThere() {
        assertNull(downloadSetupWarning(found, found, found))
    }

    @Test
    fun saysThatOnlyYouTubeNeedsDeno() {
        val warning = downloadSetupWarning(found, found, ToolLookup.NotFound("deno was not found."))

        assertTrue(warning != null && "deno was not found." in warning, "$warning")
    }
}
