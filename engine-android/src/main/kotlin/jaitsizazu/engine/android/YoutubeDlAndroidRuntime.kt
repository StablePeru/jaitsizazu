package jaitsizazu.engine.android

import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.engine.desktop.ToolLookup
import java.io.File

/**
 * Where youtubedl-android puts Python, yt-dlp, ffmpeg and QuickJS, and how it
 * starts yt-dlp. Jaitsizazu runs yt-dlp and ffmpeg itself, through the same
 * subprocess adapters as the desktop app, so downloads keep their arguments,
 * progress, cancellation and cleanup. youtubedl-android only unpacks the files
 * (see [YoutubeDlAndroidSetup]) and updates yt-dlp.
 *
 * Everything here mirrors `YoutubeDL.kt` and `FFmpeg.kt` of youtubedl-android
 * **0.18.1**: the constants `baseName`, `packagesRoot`, `pythonDirName`,
 * `ffmpegDirName`, `aria2cDirName`, `ytdlpDirName`, `ytdlpBin`,
 * `pythonBinName`, `ffmpegBinName` and `quickJsBinName`, and the environment
 * that `executeImpl` sets. Recheck it whenever that version changes.
 *
 * The binaries live in [nativeLibraryDir], the only place where Android lets
 * an app run its own programs. yt-dlp is a Python zip app in
 * [noBackupFilesDir], so it runs through Python. [exists] is injected for tests.
 */
class YoutubeDlAndroidRuntime(
    nativeLibraryDir: File,
    noBackupFilesDir: File,
    cacheDir: File,
    systemPath: String?,
    private val exists: (File) -> Boolean = File::exists,
) {
    private val base = File(noBackupFilesDir, "youtubedl-android")
    private val packages = File(base, "packages")
    private val pythonUsr = File(packages, "python/usr")
    private val ffmpegUsr = File(packages, "ffmpeg/usr")
    private val aria2cUsr = File(packages, "aria2c/usr")

    /** Python, which runs yt-dlp. */
    val python = File(nativeLibraryDir, "libpython.so")

    /** yt-dlp itself. The updater replaces this file. */
    val ytDlp = File(base, "yt-dlp/yt-dlp")

    val ffmpeg = File(nativeLibraryDir, "libffmpeg.so")

    /** The JavaScript runtime that YouTube downloads need. */
    val quickJs = File(nativeLibraryDir, "libqjs.so")

    /** The environment every process gets, on top of the app's own, as youtubedl-android sets it. */
    val environment: Map<String, String> = mapOf(
        "LD_LIBRARY_PATH" to listOf(pythonUsr, ffmpegUsr, aria2cUsr).joinToString(":") { File(it, "lib").path },
        "SSL_CERT_FILE" to File(pythonUsr, "etc/tls/cert.pem").path,
        "PATH" to listOfNotNull(systemPath?.takeIf { it.isNotEmpty() }, nativeLibraryDir.path).joinToString(":"),
        "PYTHONHOME" to pythonUsr.path,
        "HOME" to pythonUsr.path,
        "TMPDIR" to cacheDir.path,
    )

    /** [command] as Android must start it: yt-dlp through Python, anything else as it is. */
    fun launchCommand(command: List<String>): List<String> =
        if (command.firstOrNull() == ytDlp.path) listOf(python.path) + command else command

    /** Starts [command] with [environment]. For SystemProcessRunner's `startProcess`. */
    fun startProcess(command: List<String>): Process =
        ProcessBuilder(launchCommand(command)).apply { environment().putAll(environment) }.start()

    fun locateYtDlp(): ToolLookup = locate(Tool.YT_DLP, ytDlp, python, pythonUsr, ytDlp)

    fun locateFfmpeg(): ToolLookup = locate(Tool.FFMPEG, ffmpeg, ffmpeg, File(ffmpegUsr, "lib"))

    fun locateQuickJs(): ToolLookup = locate(Tool.QUICKJS, quickJs, quickJs)

    /** The problem of every tool that is not ready, for the startup warnings. */
    fun problems(): List<Problem> =
        listOf(locateYtDlp(), locateFfmpeg(), locateQuickJs()).filterIsInstance<ToolLookup.NotFound>().map { it.problem }

    /** [binary] of [tool], when every file in [needed] is there; otherwise the first one missing. */
    private fun locate(tool: Tool, binary: File, vararg needed: File): ToolLookup {
        val missing = needed.firstOrNull { !exists(it) } ?: return ToolLookup.Found(binary.path)
        return ToolLookup.NotFound(Problem.ToolNotReadyInApp(tool, missing.path, detail = null))
    }
}
