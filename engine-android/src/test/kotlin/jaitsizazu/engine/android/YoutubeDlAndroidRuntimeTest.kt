package jaitsizazu.engine.android

import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.engine.desktop.ToolLookup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class YoutubeDlAndroidRuntimeTest {

    private val lib = "/data/app/~~x/io.github.stableperu.jaitsizazu-y/lib/arm64"
    private val noBackup = "/data/user/0/io.github.stableperu.jaitsizazu/no_backup"
    private val pkgs = "$noBackup/youtubedl-android/packages"

    private fun runtime(existing: Set<String>? = null, systemPath: String? = "/system/bin") = YoutubeDlAndroidRuntime(
        nativeLibraryDir = File(lib),
        noBackupFilesDir = File(noBackup),
        cacheDir = File("/data/user/0/io.github.stableperu.jaitsizazu/cache"),
        systemPath = systemPath,
        exists = { existing == null || it.path in existing },
    )

    @Test
    fun findsTheFilesWhereYoutubeDlAndroidPutsThem() {
        val runtime = runtime()

        assertEquals("$lib/libpython.so", runtime.python.path)
        assertEquals("$noBackup/youtubedl-android/yt-dlp/yt-dlp", runtime.ytDlp.path)
        assertEquals("$lib/libffmpeg.so", runtime.ffmpeg.path)
        assertEquals("$lib/libqjs.so", runtime.quickJs.path)
        assertEquals(ToolLookup.Found(runtime.ytDlp.path), runtime.locateYtDlp())
        assertEquals(ToolLookup.Found(runtime.ffmpeg.path), runtime.locateFfmpeg())
        assertEquals(ToolLookup.Found(runtime.quickJs.path), runtime.locateQuickJs())
        assertEquals(emptyList(), runtime.problems())
    }

    @Test
    fun setsTheEnvironmentOfYoutubeDlAndroid() {
        assertEquals(
            mapOf(
                "LD_LIBRARY_PATH" to "$pkgs/python/usr/lib:$pkgs/ffmpeg/usr/lib:$pkgs/aria2c/usr/lib",
                "SSL_CERT_FILE" to "$pkgs/python/usr/etc/tls/cert.pem",
                "PATH" to "/system/bin:$lib",
                "PYTHONHOME" to "$pkgs/python/usr",
                "HOME" to "$pkgs/python/usr",
                "TMPDIR" to "/data/user/0/io.github.stableperu.jaitsizazu/cache",
            ),
            runtime().environment,
        )
        assertEquals(lib, runtime(systemPath = null).environment["PATH"])
    }

    @Test
    fun runsYtDlpThroughPythonAndAnythingElseAsItIs() {
        val runtime = runtime()

        assertEquals(
            listOf("$lib/libpython.so", runtime.ytDlp.path, "--ignore-config", "--", "https://example.org"),
            runtime.launchCommand(listOf(runtime.ytDlp.path, "--ignore-config", "--", "https://example.org")),
        )
        assertEquals(
            listOf(runtime.ffmpeg.path, "-i", "in.opus"),
            runtime.launchCommand(listOf(runtime.ffmpeg.path, "-i", "in.opus")),
        )
    }

    @Test
    fun namesTheFirstMissingFileOfEachTool() {
        val runtime = runtime(existing = setOf("$lib/libpython.so", "$pkgs/python/usr", "$lib/libffmpeg.so"))

        val noYtDlp = Problem.ToolNotReadyInApp(Tool.YT_DLP, runtime.ytDlp.path, null)
        val noFfmpegLibs = Problem.ToolNotReadyInApp(Tool.FFMPEG, "$pkgs/ffmpeg/usr/lib", null)
        val noQuickJs = Problem.ToolNotReadyInApp(Tool.QUICKJS, "$lib/libqjs.so", null)
        assertEquals(ToolLookup.NotFound(noYtDlp), runtime.locateYtDlp())
        assertEquals(listOf(noYtDlp, noFfmpegLibs, noQuickJs), runtime.problems())
    }

    @Test
    fun needsPythonForYtDlp() {
        val runtime = runtime(existing = emptySet())

        assertEquals(
            ToolLookup.NotFound(Problem.ToolNotReadyInApp(Tool.YT_DLP, "$lib/libpython.so", null)),
            runtime.locateYtDlp(),
        )
    }
}
