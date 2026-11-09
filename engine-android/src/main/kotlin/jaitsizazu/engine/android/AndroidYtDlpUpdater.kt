package jaitsizazu.engine.android

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import jaitsizazu.core.DownloaderUpdater
import jaitsizazu.core.Problem
import jaitsizazu.core.UpdateResult
import jaitsizazu.engine.desktop.ToolLookup
import kotlinx.coroutines.CancellationException

/**
 * Android adapter for [DownloaderUpdater]: youtubedl-android downloads the
 * latest stable yt-dlp from GitHub and puts it where downloads run it
 * ([YoutubeDlAndroidRuntime.ytDlp]), so yt-dlp updates without a new app.
 *
 * [ready] says why yt-dlp is not there, or null. [update] does the update and
 * blocks; [version] is the release now installed, as youtubedl-android
 * recorded it, or null. The report repeats yt-dlp's own `-U` wording, so the
 * screen reads the same as on desktop.
 */
class AndroidYtDlpUpdater(
    private val ready: () -> Problem?,
    private val update: () -> YoutubeDL.UpdateStatus?,
    private val version: () -> String?,
) : DownloaderUpdater {

    override suspend fun update(): UpdateResult {
        ready()?.let { return UpdateResult.Failed(it) }
        val status = try {
            update.invoke()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return UpdateResult.Failed(Problem.UpdateNotDownloaded(describe(e)))
        }
        val installed = version()
        return when (status) {
            YoutubeDL.UpdateStatus.DONE ->
                UpdateResult.Updated(if (installed != null) "Updated yt-dlp to $installed" else "Updated yt-dlp")
            YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE ->
                UpdateResult.Updated(if (installed != null) "yt-dlp is up to date ($installed)" else "yt-dlp is up to date")
            null -> UpdateResult.Unclear(null)
        }
    }

    companion object {
        /** The updater of this app: init is a no-op once it worked, and tries again if it failed at startup. */
        fun create(context: Context, runtime: YoutubeDlAndroidRuntime) = AndroidYtDlpUpdater(
            ready = { (runtime.locateYtDlp() as? ToolLookup.NotFound)?.problem },
            update = {
                YoutubeDL.getInstance().init(context)
                YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            },
            version = { YoutubeDL.getInstance().version(context) },
        )
    }
}
