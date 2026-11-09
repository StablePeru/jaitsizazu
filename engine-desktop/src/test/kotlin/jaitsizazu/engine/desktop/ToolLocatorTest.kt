package jaitsizazu.engine.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Paths are written with forward slashes and always go through [Path.of],
 * on both sides of each comparison, so the tests pass on Windows hosts too.
 */
class ToolLocatorTest {

    private fun locator(
        env: Map<String, String> = emptyMap(),
        appDirectory: String? = null,
        isWindows: Boolean = false,
        files: Set<String> = emptySet(),
        tool: ExternalTool = ExternalTool.FFMPEG,
    ) = ToolLocator(
        tool,
        environment = env::get,
        appDirectory = appDirectory?.let(Path::of),
        isWindows = isWindows,
        isExecutableFile = { it in files.map(Path::of) },
    )

    /** What the locator returns for [path], in the separators of the current OS. */
    private fun found(path: String) = ToolLookup.Found(Path.of(path).toString())

    @Test
    fun prefersTheEnvironmentVariable() {
        val result = locator(
            env = mapOf("JAITSIZAZU_FFMPEG" to "/custom/ffmpeg", "PATH" to "/usr/bin"),
            appDirectory = "/opt/app",
            files = setOf("/custom/ffmpeg", "/opt/app/bin/ffmpeg", "/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(found("/custom/ffmpeg"), result)
    }

    @Test
    fun reportsAnEnvironmentVariableThatPointsNowhere() {
        val result = locator(
            env = mapOf("JAITSIZAZU_FFMPEG" to "/custom/ffmpeg", "PATH" to "/usr/bin"),
            files = setOf("/usr/bin/ffmpeg"),
        ).locate()

        assertIs<ToolLookup.NotFound>(result)
        assertTrue("JAITSIZAZU_FFMPEG" in result.message)
        assertTrue("/custom/ffmpeg" in result.message)
    }

    @Test
    fun ignoresABlankEnvironmentVariable() {
        val result = locator(
            env = mapOf("JAITSIZAZU_FFMPEG" to " ", "PATH" to "/usr/bin"),
            files = setOf("/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(found("/usr/bin/ffmpeg"), result)
    }

    @Test
    fun prefersTheBinFolderNextToTheAppOverPath() {
        val result = locator(
            env = mapOf("PATH" to "/usr/bin"),
            appDirectory = "/opt/app",
            files = setOf("/opt/app/bin/ffmpeg", "/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(found("/opt/app/bin/ffmpeg"), result)
    }

    @Test
    fun looksForTheExeNextToTheAppOnWindows() {
        val result = locator(
            appDirectory = "/opt/app",
            isWindows = true,
            files = setOf("/opt/app/bin/ffmpeg", "/opt/app/bin/ffmpeg.exe"),
        ).locate()

        assertEquals(found("/opt/app/bin/ffmpeg.exe"), result)
    }

    @Test
    fun searchesPathInOrder() {
        val result = locator(
            env = mapOf("PATH" to "/nothing:/usr/local/bin:/usr/bin"),
            files = setOf("/usr/local/bin/ffmpeg", "/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(found("/usr/local/bin/ffmpeg"), result)
    }

    @Test
    fun splitsPathOnSemicolonsAndDropsQuotesOnWindows() {
        val result = locator(
            env = mapOf("PATH" to "/a;;\"/tools/ffmpeg\""),
            isWindows = true,
            files = setOf("/tools/ffmpeg/ffmpeg.exe"),
        ).locate()

        assertEquals(found("/tools/ffmpeg/ffmpeg.exe"), result)
    }

    @Test
    fun explainsEveryOptionWhenNothingIsFound() {
        val result = locator(env = mapOf("PATH" to "/usr/bin"), appDirectory = "/opt/app").locate()

        assertIs<ToolLookup.NotFound>(result)
        assertTrue("PATH" in result.message)
        assertTrue("JAITSIZAZU_FFMPEG" in result.message)
        assertTrue("bin" in result.message)
        // The converter looks for ffmpeg again on every conversion.
        assertTrue("try again" in result.message, result.message)
    }

    @Test
    fun worksWithoutAnAppDirectoryOrPath() {
        assertIs<ToolLookup.NotFound>(locator().locate())
    }

    @Test
    fun findsYtDlpThroughItsOwnVariableAndBinaryName() {
        val fromEnv = locator(
            env = mapOf("JAITSIZAZU_YTDLP" to "/custom/yt-dlp", "JAITSIZAZU_FFMPEG" to "/custom/ffmpeg"),
            files = setOf("/custom/yt-dlp", "/custom/ffmpeg"),
            tool = ExternalTool.YT_DLP,
        ).locate()
        val nextToTheApp = locator(
            appDirectory = "/opt/app",
            isWindows = true,
            files = setOf("/opt/app/bin/ffmpeg.exe", "/opt/app/bin/yt-dlp.exe"),
            tool = ExternalTool.YT_DLP,
        ).locate()

        assertEquals(found("/custom/yt-dlp"), fromEnv)
        assertEquals(found("/opt/app/bin/yt-dlp.exe"), nextToTheApp)
    }

    @Test
    fun findsDenoOnPath() {
        val result = locator(env = mapOf("PATH" to "/usr/bin"), files = setOf("/usr/bin/deno"), tool = ExternalTool.DENO)
            .locate()

        assertEquals(found("/usr/bin/deno"), result)
    }

    @Test
    fun namesTheToolItsVariableAndWhyItIsNeeded() {
        val result = locator(env = mapOf("PATH" to "/usr/bin"), tool = ExternalTool.YT_DLP).locate()

        assertIs<ToolLookup.NotFound>(result)
        assertTrue(result.message.startsWith("yt-dlp was not found."), result.message)
        assertTrue("JAITSIZAZU_YTDLP" in result.message, result.message)
        assertTrue("bin/yt-dlp" in result.message, result.message)
        assertTrue("download" in result.message, result.message)
    }
}
