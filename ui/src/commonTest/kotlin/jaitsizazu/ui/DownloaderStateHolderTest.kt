package jaitsizazu.ui

import jaitsizazu.core.DownloadMedia
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.DownloaderUpdater
import jaitsizazu.core.MediaDownloader
import jaitsizazu.core.MediaResult
import jaitsizazu.core.UpdateResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle
class DownloaderStateHolderTest {

    /** Reports [progress], then waits for [gate] before answering. */
    private class FakeDownloader(
        private val gate: CompletableDeferred<Unit>? = null,
        private val progress: DownloadProgress? = null,
        private val answer: (DownloadRequest) -> MediaResult = { MediaResult.Success("${it.outputDirectory}/clip.mp3") },
    ) : MediaDownloader {
        val requests = mutableListOf<DownloadRequest>()

        override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
            requests += request
            progress?.let(onProgress)
            gate?.await()
            return answer(request)
        }
    }

    /** Hangs until cancelled and keeps its progress callback, like yt-dlp on a slow site. */
    private class HangingDownloader : MediaDownloader {
        var onProgress: ((DownloadProgress) -> Unit)? = null
        var cancelled = false

        override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
            this.onProgress = onProgress
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
    }

    private class FakeUpdater(
        private val gate: CompletableDeferred<Unit>? = null,
        private val answer: () -> UpdateResult = { UpdateResult.Updated("Updated yt-dlp to stable@2026.11.20") },
    ) : DownloaderUpdater {
        var calls = 0

        override suspend fun update(): UpdateResult {
            calls++
            gate?.await()
            return answer()
        }
    }

    private val downloads = "/home/me/Downloads"
    private val music = "/home/me/Music"
    private val url = "https://example.org/clip"

    private fun TestScope.holder(downloader: MediaDownloader = FakeDownloader(), updater: DownloaderUpdater = FakeUpdater()) =
        DownloaderStateHolder(
            DownloadMedia(downloader) { it == downloads || it == music },
            updater,
            this,
            defaultOutputDirectory = downloads,
        )

    @Test
    fun startsEmptyWithAudioAndTheDefaultFolder() = runTest {
        val state = holder().state.value

        assertEquals(DownloaderState(outputDirectory = downloads), state)
        assertEquals(DownloadQuality.AUDIO_MP3, state.quality)
        assertFalse(state.canDownload)
        assertTrue(state.canUpdate)
    }

    @Test
    fun downloadsTheAddressAsTheChosenFormatIntoTheChosenFolder() = runTest {
        val gate = CompletableDeferred<Unit>()
        val downloader = FakeDownloader(gate)
        val holder = holder(downloader)
        holder.setUrl(url)
        holder.selectQuality(DownloadQuality.VIDEO_MP4)
        holder.selectFolder(music)

        holder.download()
        advanceUntilIdle()
        assertEquals(DownloadStatus.Downloading(), holder.state.value.status)
        assertFalse(holder.state.value.canDownload)
        assertFalse(holder.state.value.canUpdate)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(DownloadStatus.Done("$music/clip.mp3"), holder.state.value.status)
        assertEquals(listOf(DownloadRequest(url, DownloadQuality.VIDEO_MP4, music)), downloader.requests)
    }

    @Test
    fun showsTheProgressWhileDownloading() = runTest {
        val progress = DownloadProgress.Downloading(42.0, 2048.0, 5)
        val holder = holder(FakeDownloader(CompletableDeferred(), progress))
        holder.setUrl(url)

        holder.download()
        advanceUntilIdle()

        assertEquals(DownloadStatus.Downloading(progress), holder.state.value.status)
        holder.cancel()
    }

    @Test
    fun showsWhyADownloadFailed() = runTest {
        val holder = holder(FakeDownloader { MediaResult.Failure("yt-dlp failed") })
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()
        assertEquals(DownloadStatus.Failed("yt-dlp failed"), holder.state.value.status)

        holder.setUrl("not an address")
        holder.download()
        advanceUntilIdle()
        val status = holder.state.value.status
        assertTrue(status is DownloadStatus.Failed && "https://" in status.reason, "$status")
    }

    @Test
    fun turnsAnUnexpectedErrorIntoAFailure() = runTest {
        val holder = holder(FakeDownloader { error("boom") })
        holder.setUrl(url)

        holder.download()
        advanceUntilIdle()

        val status = holder.state.value.status
        assertTrue(status is DownloadStatus.Failed && "boom" in status.reason, "$status")
    }

    @Test
    fun ignoresActionsWhileDownloading() = runTest {
        val gate = CompletableDeferred<Unit>()
        val downloader = FakeDownloader(gate)
        val updater = FakeUpdater()
        val holder = holder(downloader, updater)
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()

        holder.download()
        holder.setUrl("https://example.org/other")
        holder.selectQuality(DownloadQuality.VIDEO_MP4)
        holder.selectFolder(music)
        holder.updateDownloader()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, downloader.requests.size)
        assertEquals(0, updater.calls)
        assertEquals(DownloaderState(url, DownloadQuality.AUDIO_MP3, downloads, DownloadStatus.Done("$downloads/clip.mp3")), holder.state.value)
    }

    @Test
    fun cancelStopsTheDownloadAndIgnoresLateProgress() = runTest {
        val downloader = HangingDownloader()
        val holder = holder(downloader)
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()

        holder.cancel()
        advanceUntilIdle()
        downloader.onProgress?.invoke(DownloadProgress.Downloading(99.0, null, null))

        assertTrue(downloader.cancelled)
        assertEquals(DownloadStatus.Cancelled, holder.state.value.status)
        assertTrue(holder.state.value.canDownload)
    }

    @Test
    fun editingAfterAResultClearsIt() = runTest {
        val holder = holder()
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()

        holder.setUrl("https://example.org/next")

        assertEquals(DownloadStatus.Idle, holder.state.value.status)
    }

    @Test
    fun aSuccessfulDownloadClearsTheSetupWarning() = runTest {
        val holder = holder()
        holder.showSetupWarning("deno was not found.")
        holder.setUrl(url)

        holder.download()
        advanceUntilIdle()

        assertNull(holder.state.value.setupWarning)
    }

    @Test
    fun updatesTheDownloaderAndBlocksDownloadsMeanwhile() = runTest {
        val gate = CompletableDeferred<Unit>()
        val downloader = FakeDownloader()
        val holder = holder(downloader, FakeUpdater(gate))
        holder.setUrl(url)

        holder.updateDownloader()
        advanceUntilIdle()
        assertEquals(UpdateStatus.Updating, holder.state.value.update)
        assertFalse(holder.state.value.canDownload)
        holder.download()
        holder.updateDownloader()

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(UpdateStatus.Done("Updated yt-dlp to stable@2026.11.20"), holder.state.value.update)
        assertTrue(downloader.requests.isEmpty())
        assertTrue(holder.state.value.canDownload)
    }

    @Test
    fun showsAFailedUpdate() = runTest {
        val refused = holder(updater = FakeUpdater { UpdateResult.Failed("yt-dlp is read-only.") })
        val broken = holder(updater = FakeUpdater { error("boom") })

        refused.updateDownloader()
        broken.updateDownloader()
        advanceUntilIdle()

        assertEquals(UpdateStatus.Failed("yt-dlp is read-only."), refused.state.value.update)
        val status = broken.state.value.update
        assertIs<UpdateStatus.Failed>(status)
        assertTrue("boom" in status.reason, status.reason)
    }
}
