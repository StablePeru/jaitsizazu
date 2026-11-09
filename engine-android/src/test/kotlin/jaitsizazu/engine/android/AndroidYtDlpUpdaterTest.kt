package jaitsizazu.engine.android

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.core.UpdateResult
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidYtDlpUpdaterTest {

    private var updates = 0

    private fun updater(
        status: () -> YoutubeDL.UpdateStatus?,
        version: String? = "2026.11.01",
        ready: Problem? = null,
    ) = AndroidYtDlpUpdater(
        ready = { ready },
        update = {
            updates++
            status()
        },
        version = { version },
    )

    @Test
    fun reportsANewVersionInYtDlpsOwnWords() = runTest {
        assertEquals(UpdateResult.Updated("Updated yt-dlp to 2026.11.01"), updater({ YoutubeDL.UpdateStatus.DONE }).update())
        assertEquals(UpdateResult.Updated("Updated yt-dlp"), updater({ YoutubeDL.UpdateStatus.DONE }, version = null).update())
    }

    @Test
    fun reportsAnUpToDateYtDlp() = runTest {
        assertEquals(
            UpdateResult.Updated("yt-dlp is up to date (2026.11.01)"),
            updater({ YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE }).update(),
        )
    }

    @Test
    fun saysItIsUnclearWhenNoStatusComesBack() = runTest {
        assertEquals(UpdateResult.Unclear(null), updater({ null }).update())
    }

    @Test
    fun reportsAFailedDownloadWithItsCause() = runTest {
        val failure = YoutubeDLException("failed to update youtube-dl", IOException("Unable to resolve host api.github.com"))

        val result = updater({ throw failure }).update()

        assertEquals(
            UpdateResult.Failed(
                Problem.UpdateNotDownloaded("failed to update youtube-dl: Unable to resolve host api.github.com"),
            ),
            result,
        )
    }

    @Test
    fun doesNotUpdateAYtDlpThatIsNotReady() = runTest {
        val missing = Problem.ToolNotReadyInApp(Tool.YT_DLP, "/data/no_backup/youtubedl-android/yt-dlp/yt-dlp", null)

        val result = updater({ YoutubeDL.UpdateStatus.DONE }, ready = missing).update()

        assertEquals(UpdateResult.Failed(missing), result)
        assertEquals(0, updates)
    }
}
