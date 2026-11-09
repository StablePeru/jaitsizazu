package jaitsizazu.engine.android

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import java.io.File

/** Prepares youtubedl-android in an app [Context]. */
object YoutubeDlAndroidSetup {

    /** The runtime of this app's youtubedl-android files. */
    fun runtime(context: Context): YoutubeDlAndroidRuntime =
        YoutubeDlAndroidRuntime(
            nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
            noBackupFilesDir = context.noBackupFilesDir,
            cacheDir = context.cacheDir,
            systemPath = System.getenv("PATH"),
        )

    /**
     * Unpacks Python, yt-dlp and ffmpeg on the first start (and after an app
     * update that changes them); later starts only check. It takes seconds
     * the first time, so call it off the main thread. Returns the setup
     * warnings: why a tool could not be unpacked, then any tool still missing.
     */
    fun prepare(context: Context, runtime: YoutubeDlAndroidRuntime): List<Problem> {
        val failed = listOfNotNull(
            attempt(Tool.YT_DLP, runtime.ytDlp) { YoutubeDL.getInstance().init(context) },
            attempt(Tool.FFMPEG, runtime.ffmpeg) { FFmpeg.getInstance().init(context) },
        )
        val failedTools = failed.map { it.tool }.toSet()
        return failed + runtime.problems().filterNot { it is Problem.ToolNotReadyInApp && it.tool in failedTools }
    }

    private fun attempt(tool: Tool, file: File, init: () -> Unit): Problem.ToolNotReadyInApp? =
        try {
            init()
            null
        } catch (e: Exception) {
            Problem.ToolNotReadyInApp(tool, file.path, describe(e))
        }
}

/** The message of [e] and of its cause, which youtubedl-android wraps, each once. */
internal fun describe(e: Throwable): String {
    val message = e.message
    val cause = e.cause?.message?.takeUnless { message != null && it in message }
    return listOfNotNull(message, cause).joinToString(": ").ifEmpty { e::class.java.simpleName }
}
