package jaitsizazu.engine.desktop

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FfmpegLocatorTest {

    private fun locator(
        env: Map<String, String> = emptyMap(),
        appDirectory: String? = null,
        isWindows: Boolean = false,
        files: Set<String> = emptySet(),
    ) = FfmpegLocator(
        environment = env::get,
        appDirectory = appDirectory?.let(Path::of),
        isWindows = isWindows,
        isExecutableFile = { it.toString() in files },
    )

    @Test
    fun prefersTheEnvironmentVariable() {
        val result = locator(
            env = mapOf("JAITSIZAZU_FFMPEG" to "/custom/ffmpeg", "PATH" to "/usr/bin"),
            appDirectory = "/opt/app",
            files = setOf("/custom/ffmpeg", "/opt/app/bin/ffmpeg", "/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(FfmpegLookup.Found("/custom/ffmpeg"), result)
    }

    @Test
    fun reportsAnEnvironmentVariableThatPointsNowhere() {
        val result = locator(
            env = mapOf("JAITSIZAZU_FFMPEG" to "/custom/ffmpeg", "PATH" to "/usr/bin"),
            files = setOf("/usr/bin/ffmpeg"),
        ).locate()

        assertIs<FfmpegLookup.NotFound>(result)
        assertTrue("JAITSIZAZU_FFMPEG" in result.message)
        assertTrue("/custom/ffmpeg" in result.message)
    }

    @Test
    fun ignoresABlankEnvironmentVariable() {
        val result = locator(
            env = mapOf("JAITSIZAZU_FFMPEG" to " ", "PATH" to "/usr/bin"),
            files = setOf("/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(FfmpegLookup.Found("/usr/bin/ffmpeg"), result)
    }

    @Test
    fun prefersTheBinFolderNextToTheAppOverPath() {
        val result = locator(
            env = mapOf("PATH" to "/usr/bin"),
            appDirectory = "/opt/app",
            files = setOf("/opt/app/bin/ffmpeg", "/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(FfmpegLookup.Found("/opt/app/bin/ffmpeg"), result)
    }

    @Test
    fun looksForTheExeNextToTheAppOnWindows() {
        val result = locator(
            appDirectory = "/opt/app",
            isWindows = true,
            files = setOf("/opt/app/bin/ffmpeg", "/opt/app/bin/ffmpeg.exe"),
        ).locate()

        assertEquals(FfmpegLookup.Found("/opt/app/bin/ffmpeg.exe"), result)
    }

    @Test
    fun searchesPathInOrder() {
        val result = locator(
            env = mapOf("PATH" to "/nothing:/usr/local/bin:/usr/bin"),
            files = setOf("/usr/local/bin/ffmpeg", "/usr/bin/ffmpeg"),
        ).locate()

        assertEquals(FfmpegLookup.Found("/usr/local/bin/ffmpeg"), result)
    }

    @Test
    fun splitsPathOnSemicolonsAndDropsQuotesOnWindows() {
        val result = locator(
            env = mapOf("PATH" to "/a;;\"/tools/ffmpeg\""),
            isWindows = true,
            files = setOf("/tools/ffmpeg/ffmpeg.exe"),
        ).locate()

        assertEquals(FfmpegLookup.Found("/tools/ffmpeg/ffmpeg.exe"), result)
    }

    @Test
    fun explainsEveryOptionWhenNothingIsFound() {
        val result = locator(env = mapOf("PATH" to "/usr/bin"), appDirectory = "/opt/app").locate()

        assertIs<FfmpegLookup.NotFound>(result)
        assertTrue("PATH" in result.message)
        assertTrue("JAITSIZAZU_FFMPEG" in result.message)
        assertTrue("bin" in result.message)
    }

    @Test
    fun worksWithoutAnAppDirectoryOrPath() {
        assertIs<FfmpegLookup.NotFound>(locator().locate())
    }
}
