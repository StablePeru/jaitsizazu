package jaitsizazu.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import jaitsizazu.core.DownloadProgress
import jaitsizazu.ui.DownloadStatus
import jaitsizazu.ui.DownloaderScreen
import jaitsizazu.ui.DownloaderState
import jaitsizazu.ui.UpdateStatus
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Smoke test: the screen composes and renders off-screen in every status, and draws something. */
class DownloaderScreenRenderTest {

    private val width = 640
    private val height = 720

    private fun renderedColours(state: DownloaderState): Int {
        val image = ImageComposeScene(width, height) {
            MaterialTheme {
                DownloaderScreen(
                    state,
                    onUrlChange = {},
                    onQualitySelected = {},
                    onChooseFolder = {},
                    onDownload = {},
                    onCancel = {},
                    onUpdate = {},
                )
            }
        }.use { it.render() }
        assertEquals(width to height, image.width to image.height)

        val bitmap = Bitmap.makeFromImage(image)
        val colours = mutableSetOf<Int>()
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) colours += bitmap.getColor(x, y)
        }
        return colours.size
    }

    @Test
    fun rendersEveryStatus() {
        val ready = DownloaderState(url = "https://example.org/clip", outputDirectory = "/home/me/Downloads")
        val states = listOf(
            DownloaderState(),
            ready.copy(status = DownloadStatus.Downloading()),
            ready.copy(status = DownloadStatus.Downloading(DownloadProgress.Downloading(42.0, 2048.0, 65))),
            ready.copy(status = DownloadStatus.Downloading(DownloadProgress.Processing)),
            ready.copy(status = DownloadStatus.Done("/home/me/Downloads/Clip [abc].mp3")),
            ready.copy(status = DownloadStatus.Failed("yt-dlp failed")),
            ready.copy(status = DownloadStatus.Cancelling),
            ready.copy(status = DownloadStatus.Cancelled()),
            ready.copy(status = DownloadStatus.Cancelled(leftoverFolder = "/home/me/Downloads/.jaitsizazu-123")),
            ready.copy(update = UpdateStatus.Updating),
            ready.copy(update = UpdateStatus.Done("yt-dlp is up to date")),
            ready.copy(update = UpdateStatus.Failed("yt-dlp is read-only")),
            ready.copy(setupWarning = "yt-dlp was not found."),
        )
        for (state in states) {
            // A blank or uniform image means nothing was drawn.
            assertTrue(renderedColours(state) > 1, "nothing drawn for $state")
        }
    }
}
