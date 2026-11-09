package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class YtDlpArgumentsTest {

    private fun build(quality: DownloadQuality, url: String = "https://example.org/clip", jsRuntime: JsRuntime? = null) =
        YtDlpArguments.download(url, quality, "/home/me/Downloads", "/home/me/Downloads/.jaitsizazu-1", "/opt/ffmpeg", jsRuntime)

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
        assertEquals(
            "deno:/opt/deno",
            build(DownloadQuality.AUDIO_MP3, jsRuntime = JsRuntime(JsRuntime.DENO, "/opt/deno")).valueOf("--js-runtimes"),
        )
    }

    @Test
    fun namesQuickJsForYtDlpOnAndroid() {
        val args = build(DownloadQuality.AUDIO_MP3, jsRuntime = JsRuntime(JsRuntime.QUICKJS, "/data/app/lib/libqjs.so"))

        assertEquals(listOf("quickjs:/data/app/lib/libqjs.so"), args.valuesOf("--js-runtimes"))
        assertTrue("--no-remote-components" in args, "$args")
    }

    /** The whole desktop command, as it was before the JavaScript runtime could be anything but deno. */
    @Test
    fun keepsTheDesktopArgumentsExactly() {
        val args = build(DownloadQuality.AUDIO_MP3, jsRuntime = JsRuntime(JsRuntime.DENO, "/opt/deno"))

        assertEquals(
            listOf(
                "--ignore-config", "--no-plugin-dirs", "--no-remote-components",
                "--no-playlist", "--no-overwrites", "--encoding", "utf-8",
                "--ffmpeg-location", "/opt/ffmpeg",
                "--js-runtimes", "deno:/opt/deno",
                "-P", "home:/home/me/Downloads", "-P", "temp:/home/me/Downloads/.jaitsizazu-1",
                "-o", "%(title).150B [%(id)s].%(ext)s",
                "--newline", "--progress", "--no-simulate",
                "--progress-template",
                "download:jaitsizazu-progress %(progress.downloaded_bytes)s %(progress.total_bytes)s " +
                    "%(progress.total_bytes_estimate)s %(progress.speed)s %(progress.eta)s",
                "--progress-template", "postprocess:jaitsizazu-processing %(progress.status)s %(progress.postprocessor)s",
                "--print", "after_move:jaitsizazu-saved %(filepath)s",
                "-f", "bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", "2",
                "--", "https://example.org/clip",
            ),
            args,
        )
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

    /** The arguments that only [quality] decides: everything between the fixed part and `--`. */
    private fun qualityPart(quality: DownloadQuality): List<String> {
        val args = build(quality)
        val afterPrint = args.indexOf("--print") + 2
        return args.subList(afterPrint, args.indexOf("--"))
    }

    @Test
    fun extractsMp3ForAudio() {
        assertEquals(
            listOf("-f", "bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", "2"),
            qualityPart(DownloadQuality.AUDIO_MP3),
        )
    }

    @Test
    fun extractsLosslessWavForEditing() {
        assertEquals(
            listOf("-f", "bestaudio/best", "-x", "--audio-format", "wav"),
            qualityPart(DownloadQuality.AUDIO_WAV),
        )
    }

    @Test
    fun keepsTheOriginalAudioStreamWithoutReEncoding() {
        // `best` copies AAC, Opus, Vorbis, MP3, FLAC and ALAC streams as they are,
        // only into an audio file of their own (.m4a, .opus, .ogg, ...).
        assertEquals(
            listOf("-f", "bestaudio/best", "-x", "--audio-format", "best"),
            qualityPart(DownloadQuality.AUDIO_ORIGINAL),
        )
    }

    @Test
    fun mergesMp4UpTo1080pForVideo() {
        assertEquals(
            listOf(
                "-f", "bestvideo*+bestaudio/best", "-S", "res:1080,ext:mp4:m4a",
                "--merge-output-format", "mp4", "--remux-video", "mp4",
            ),
            qualityPart(DownloadQuality.VIDEO_MP4),
        )
    }

    @Test
    fun mergesTheBestVideoUpTo4kIntoMp4OrElseMkv() {
        val args = qualityPart(DownloadQuality.VIDEO_BEST)

        assertEquals(
            listOf("-f", "bestvideo*+bestaudio/best", "-S", "res:2160", "--merge-output-format", "mp4/mkv"),
            args,
        )
        // Remuxing to MP4 would fail for VP9 or AV1 with Opus: MKV takes any codec.
        assertFalse("--remux-video" in args, "$args")
    }

    @Test
    fun onlyTheAudioOptionsExtractAudio() {
        for (quality in DownloadQuality.entries) {
            val isAudio = quality.name.startsWith("AUDIO_")
            assertEquals(isAudio, "-x" in build(quality), "$quality")
            assertEquals(!isAudio, "--merge-output-format" in build(quality), "$quality")
        }
    }

    @Test
    fun updatesWithoutReadingUserConfiguration() {
        assertEquals(listOf("--ignore-config", "-U"), YtDlpArguments.update())
    }
}
