package jaitsizazu.desktop

import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.engine.desktop.ToolLookup
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

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

    private val noYtDlp = Problem.ToolNotFound(Tool.YT_DLP, "JAITSIZAZU_YTDLP", "bin/yt-dlp")
    private val noDeno = Problem.ToolNotFound(Tool.DENO, "JAITSIZAZU_DENO", "bin/deno")

    @Test
    fun warnsAboutEveryMissingTool() {
        val warnings = downloadSetupWarnings(
            ytDlp = ToolLookup.NotFound(noYtDlp),
            ffmpeg = found,
            deno = ToolLookup.NotFound(noDeno),
        )

        assertEquals(listOf(noYtDlp, noDeno), warnings)
    }

    @Test
    fun saysNothingWhenEveryToolIsThere() {
        assertEquals(emptyList(), downloadSetupWarnings(found, found, found))
    }

    @Test
    fun warnsWhenOnlyDenoIsMissing() {
        assertEquals(listOf<Problem>(noDeno), downloadSetupWarnings(found, found, ToolLookup.NotFound(noDeno)))
    }
}
