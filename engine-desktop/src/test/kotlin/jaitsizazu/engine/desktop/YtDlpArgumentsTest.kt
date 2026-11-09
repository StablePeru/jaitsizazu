package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YtDlpArgumentsTest {

    private fun build(quality: DownloadQuality, url: String = "https://example.org/clip", deno: String? = null) =
        YtDlpArguments.download(url, quality, "/home/me/Downloads", "/home/me/Downloads/.jaitsizazu-1", "/opt/ffmpeg", deno)

    /** The value right after [option], or null when [option] is missing. */
    private fun List<String>.valueOf(option: String): String? =
        indexOf(option).takeIf { it >= 0 }?.let { getOrNull(it + 1) }

    private fun List<String>.valuesOf(option: String): List<String> =
        withIndex().filter { it.value == option }.mapNotNull { getOrNull(it.index + 1) }

    @Test
    fun endsWithTheUrlAfterADoubleDash() {
        val args = build(DownloadQuality.AUDIO_MP3, url = "-https://example.org")

        assertEquals(listOf("--", "-https://example.org"), args.takeLast(2))
    }

    @Test
    fun ignoresUserConfigurationPluginsAndRemoteCode() {
        val args = build(DownloadQuality.VIDEO_MP4)

        for (flag in listOf("--ignore-config", "--no-plugin-dirs", "--no-remote-components", "--no-playlist", "--no-overwrites")) {
            assertTrue(flag in args, "missing $flag in $args")
        }
        assertFalse(args.any { it.startsWith("--exec") || it == "--use-postprocessor" }, "$args")
    }

    @Test
    fun savesInTheChosenFolderThroughAPrivateTemporaryFolder() {
        val args = build(DownloadQuality.AUDIO_MP3)

        assertEquals(listOf("home:/home/me/Downloads", "temp:/home/me/Downloads/.jaitsizazu-1"), args.valuesOf("-P"))
        assertEquals("%(title).150B [%(id)s].%(ext)s", args.valueOf("-o"))
    }

    @Test
    fun usesTheLocatedFfmpegAndDenoWhenThereIsOne() {
        assertEquals("/opt/ffmpeg", build(DownloadQuality.AUDIO_MP3).valueOf("--ffmpeg-location"))
        assertEquals(null, build(DownloadQuality.AUDIO_MP3).valueOf("--js-runtimes"))
        assertEquals("deno:/opt/deno", build(DownloadQuality.AUDIO_MP3, deno = "/opt/deno").valueOf("--js-runtimes"))
    }

    @Test
    fun printsMachineReadableProgressAndTheSavedFile() {
        val args = build(DownloadQuality.AUDIO_MP3)

        assertTrue("--newline" in args && "--progress" in args, "$args")
        val templates = args.valuesOf("--progress-template")
        assertTrue(templates.any { it.startsWith("download:${YtDlpOutput.PROGRESS} ") }, "$templates")
        assertTrue(templates.any { it.startsWith("postprocess:${YtDlpOutput.PROCESSING} ") }, "$templates")
        assertEquals("after_move:${YtDlpOutput.SAVED} %(filepath)s", args.valueOf("--print"))
    }

    @Test
    fun extractsMp3ForAudio() {
        val args = build(DownloadQuality.AUDIO_MP3)

        assertTrue("-x" in args)
        assertEquals("mp3", args.valueOf("--audio-format"))
        assertFalse("--merge-output-format" in args)
    }

    @Test
    fun mergesMp4UpTo1080pForVideo() {
        val args = build(DownloadQuality.VIDEO_MP4)

        assertFalse("-x" in args)
        assertEquals("mp4", args.valueOf("--merge-output-format"))
        assertTrue(args.valueOf("-S").orEmpty().startsWith("res:1080"), "$args")
    }

    @Test
    fun updatesWithoutReadingUserConfiguration() {
        assertEquals(listOf("--ignore-config", "-U"), YtDlpArguments.update())
    }
}
