package jaitsizazu.ui

import jaitsizazu.core.DownloadCancelledWithLeftovers
import jaitsizazu.core.DownloadMedia
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.DownloaderUpdater
import jaitsizazu.core.Leftover
import jaitsizazu.core.MediaDownloader
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.core.UpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class) // advanceUntilIdle, advanceTimeBy, runCurrent
class DownloaderStateHolderTest {

    private companion object {
        /** How long [SlowToStopDownloader] needs to stop once cancelled. */
        val stopTime = 5.seconds
    }

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

    /**
     * Once cancelled, needs [stopTime] (of virtual time) to stop, like yt-dlp
     * on Windows while its processes exit. Then reports [leftoverFolder], if
     * set, as a temporary folder it could not remove.
     */
    private class SlowToStopDownloader(
        private val leftoverFolder: String? = null,
    ) : MediaDownloader {
        var onProgress: ((DownloadProgress) -> Unit)? = null
        var finished = false

        override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
            this.onProgress = onProgress
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                withContext(NonCancellable) { delay(stopTime) }
                finished = true
                if (leftoverFolder != null) throw DownloadCancelledWithLeftovers(leftoverFolder)
                throw e
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

    private fun TestScope.holder(
        downloader: MediaDownloader = FakeDownloader(),
        updater: DownloaderUpdater = FakeUpdater(),
        preparing: Boolean = false,
    ) =
        DownloaderStateHolder(
            DownloadMedia(downloader) { it == downloads || it == music },
            updater,
            this,
            defaultOutputDirectory = downloads,
            preparing = preparing,
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
    fun waitsForTheSetupBeforeDownloadingOrUpdating() = runTest {
        val downloader = FakeDownloader()
        val updater = FakeUpdater()
        val holder = holder(downloader, updater, preparing = true)
        holder.setUrl(url)

        assertTrue(holder.state.value.preparing)
        assertFalse(holder.state.value.canDownload)
        assertFalse(holder.state.value.canUpdate)
        holder.download()
        holder.updateDownloader()
        advanceUntilIdle()
        assertEquals(DownloadStatus.Idle, holder.state.value.status)
        assertEquals(UpdateStatus.Idle, holder.state.value.update)
        assertTrue(downloader.requests.isEmpty())
        assertEquals(0, updater.calls)

        val noQuickJs = Problem.ToolNotReadyInApp(Tool.QUICKJS, "/lib/libqjs.so", detail = null)
        holder.finishSetup(listOf(noQuickJs))
        assertFalse(holder.state.value.preparing)
        assertEquals(listOf(noQuickJs), holder.state.value.setupWarnings)
        holder.download()
        advanceUntilIdle()
        assertEquals(DownloadStatus.Done("$downloads/clip.mp3"), holder.state.value.status)
    }

    @Test
    fun showsTheSetupFailureInsteadOfDownloadingOrUpdating() = runTest {
        val downloader = FakeDownloader()
        val updater = FakeUpdater()
        val holder = holder(downloader, updater, preparing = true)
        val notUnpacked = Problem.ToolNotReadyInApp(Tool.YT_DLP, "/files/yt-dlp", "No space left on device")
        holder.setUrl(url)

        holder.finishSetup(listOf(notUnpacked), failure = notUnpacked)
        assertTrue(holder.state.value.canDownload)
        holder.download()
        holder.updateDownloader()
        advanceUntilIdle()

        assertEquals(DownloadStatus.Failed(notUnpacked), holder.state.value.status)
        assertEquals(UpdateStatus.Failed(notUnpacked), holder.state.value.update)
        assertTrue(downloader.requests.isEmpty())
        assertEquals(0, updater.calls)
        assertEquals(listOf(notUnpacked), holder.state.value.setupWarnings)
    }

    @Test
    fun isReadyAtOnceWhenThereIsNoSetupToWaitFor() = runTest {
        // The desktop app: its tools are looked up on every download.
        val holder = holder()
        holder.setUrl(url)

        assertFalse(holder.state.value.preparing)
        assertTrue(holder.state.value.canDownload)
        assertTrue(holder.state.value.canUpdate)
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
        val failed = Problem.DownloadFailed(1, "ERROR: Unsupported URL")
        val leftover = Leftover.TemporaryFolder("/home/me/Downloads/.jaitsizazu-1", "busy")
        val holder = holder(FakeDownloader { MediaResult.Failure(failed, leftover) })
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()
        assertEquals(DownloadStatus.Failed(failed, leftover), holder.state.value.status)

        holder.setUrl("not an address")
        holder.download()
        advanceUntilIdle()
        assertEquals(DownloadStatus.Failed(Problem.NotAWebAddress("not an address")), holder.state.value.status)
    }

    @Test
    fun turnsAnUnexpectedErrorIntoAFailure() = runTest {
        val holder = holder(FakeDownloader { error("boom") })
        holder.setUrl(url)

        holder.download()
        advanceUntilIdle()

        assertEquals(DownloadStatus.Failed(Problem.Unexpected("boom")), holder.state.value.status)
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
        assertEquals(DownloadStatus.Cancelled(), holder.state.value.status)
        assertTrue(holder.state.value.canDownload)
    }

    @Test
    fun staysCancellingAndBlocksEverythingUntilTheDownloadHasStopped() = runTest {
        val downloader = SlowToStopDownloader()
        val updater = FakeUpdater()
        val holder = holder(downloader, updater)
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()

        holder.cancel()
        runCurrent()
        assertEquals(DownloadStatus.Cancelling, holder.state.value.status)
        assertFalse(holder.state.value.canDownload)
        assertFalse(holder.state.value.canUpdate)
        holder.updateDownloader()
        holder.download()
        holder.setUrl("https://example.org/other")
        advanceTimeBy(stopTime / 2)
        assertEquals(0, updater.calls, "yt-dlp must not be replaced while it may still run")
        assertEquals(DownloadStatus.Cancelling, holder.state.value.status)
        assertEquals(url, holder.state.value.url)
        assertFalse(downloader.finished)

        advanceUntilIdle()
        assertTrue(downloader.finished)
        assertEquals(DownloadStatus.Cancelled(), holder.state.value.status)
        assertTrue(holder.state.value.canUpdate)
        holder.updateDownloader()
        advanceUntilIdle()
        assertEquals(1, updater.calls)
    }

    @Test
    fun ignoresProgressThatArrivesWhileCancellingOrAfterTheEnd() = runTest {
        val downloader = SlowToStopDownloader()
        val holder = holder(downloader)
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()
        val lateProgress = DownloadProgress.Downloading(99.0, null, null)

        holder.cancel()
        runCurrent()
        downloader.onProgress?.invoke(lateProgress)
        assertEquals(DownloadStatus.Cancelling, holder.state.value.status)

        advanceUntilIdle()
        downloader.onProgress?.invoke(lateProgress)
        assertEquals(DownloadStatus.Cancelled(), holder.state.value.status)
    }

    @Test
    fun ignoresProgressFromAnEarlierDownloadInTheNextOne() = runTest {
        val first = SlowToStopDownloader()
        val second = FakeDownloader(CompletableDeferred())
        var calls = 0
        val oneThenTheOther = object : MediaDownloader {
            override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult =
                if (calls++ == 0) first.download(request, onProgress) else second.download(request, onProgress)
        }
        val holder = holder(oneThenTheOther)
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()
        holder.cancel()
        advanceUntilIdle()

        holder.download()
        advanceUntilIdle()
        first.onProgress?.invoke(DownloadProgress.Downloading(99.0, null, null))

        assertEquals(DownloadStatus.Downloading(), holder.state.value.status)
        holder.cancel()
    }

    @Test
    fun ignoresProgressThatArrivesAfterTheResult() = runTest {
        var report: ((DownloadProgress) -> Unit)? = null
        val downloader = object : MediaDownloader {
            override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
                report = onProgress
                return MediaResult.Success("${request.outputDirectory}/clip.mp3")
            }
        }
        val holder = holder(downloader)
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()

        report?.invoke(DownloadProgress.Processing)

        assertEquals(DownloadStatus.Done("$downloads/clip.mp3"), holder.state.value.status)
    }

    @Test
    fun saysWhichTemporaryFolderACancelledDownloadLeftBehind() = runTest {
        val folder = "$downloads/.jaitsizazu-123"
        val holder = holder(SlowToStopDownloader(leftoverFolder = folder))
        holder.setUrl(url)
        holder.download()
        advanceUntilIdle()

        holder.cancel()
        advanceUntilIdle()

        assertEquals(DownloadStatus.Cancelled(leftoverFolder = folder), holder.state.value.status)
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
        holder.showSetupWarnings(listOf(Problem.ToolNotFound(Tool.DENO, "JAITSIZAZU_DENO", "bin/deno")))
        holder.setUrl(url)

        holder.download()
        advanceUntilIdle()

        assertEquals(emptyList(), holder.state.value.setupWarnings)
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
        val readOnly = Problem.ToolReadOnly(Tool.YT_DLP, "/usr/bin/yt-dlp")
        val refused = holder(updater = FakeUpdater { UpdateResult.Failed(readOnly) })
        val broken = holder(updater = FakeUpdater { error("boom") })

        refused.updateDownloader()
        broken.updateDownloader()
        advanceUntilIdle()

        assertEquals(UpdateStatus.Failed(readOnly), refused.state.value.update)
        assertEquals(UpdateStatus.Failed(Problem.Unexpected("boom")), broken.state.value.update)
    }

    @Test
    fun showsAnUpdateThatDidNotSayItsVersion() = runTest {
        val holder = holder(updater = FakeUpdater { UpdateResult.Unclear("WARNING: something") })

        holder.updateDownloader()
        advanceUntilIdle()

        assertEquals(UpdateStatus.Unclear("WARNING: something"), holder.state.value.update)
    }

    @Test
    fun startsWithMp3AndOffersEveryQuality() = runTest {
        val holder = holder()

        assertEquals(DownloadQuality.AUDIO_MP3, holder.state.value.quality)
        holder.selectQuality(DownloadQuality.VIDEO_BEST)
        assertEquals(DownloadQuality.VIDEO_BEST, holder.state.value.quality)
    }
}
