package jaitsizazu.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadMediaTest {

    private class FakeDownloader : MediaDownloader {
        val requests = mutableListOf<DownloadRequest>()

        override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
            requests += request
            onProgress(DownloadProgress.Downloading(50.0, 1000.0, 2))
            return MediaResult.Success("${request.outputDirectory}/clip.mp3")
        }
    }

    private val downloads = "/home/me/Downloads"
    private val downloader = FakeDownloader()
    private val downloadMedia = DownloadMedia(downloader) { it == downloads }

    private fun request(url: String, folder: String = downloads) = DownloadRequest(url, DownloadQuality.AUDIO_MP3, folder)

    @Test
    fun passesATrimmedWebAddressAndTheProgressOn() = runTest {
        val progress = mutableListOf<DownloadProgress>()

        val result = downloadMedia(request("  https://example.org/clip  "), progress::add)

        assertEquals(MediaResult.Success("$downloads/clip.mp3"), result)
        assertEquals(listOf(request("https://example.org/clip")), downloader.requests)
        assertEquals(listOf<DownloadProgress>(DownloadProgress.Downloading(50.0, 1000.0, 2)), progress)
    }

    @Test
    fun asksForAnAddressWhenItIsBlank() = runTest {
        val result = downloadMedia(request("   "))

        assertEquals(MediaResult.Failure(Problem.NoWebAddress), result)
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun refusesWhatIsNotAWebAddress() = runTest {
        for (url in listOf("file:///etc/passwd", "ftp://example.org/a", "--exec rm", "example.org/clip")) {
            val result = downloadMedia(request(url))

            assertEquals(MediaResult.Failure(Problem.NotAWebAddress(url)), result, url)
        }
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun refusesAMissingFolder() = runTest {
        val blank = downloadMedia(request("https://example.org/clip", folder = " "))
        val missing = downloadMedia(request("https://example.org/clip", folder = "/nowhere"))

        assertEquals(MediaResult.Failure(Problem.NoFolderChosen), blank)
        assertEquals(MediaResult.Failure(Problem.FolderNotFound("/nowhere")), missing)
        assertTrue(downloader.requests.isEmpty())
    }

    @Test
    fun recognisesWebAddresses() {
        assertTrue(isWebAddress("https://www.youtube.com/watch?v=abc"))
        assertTrue(isWebAddress("HTTP://example.org"))
        assertFalse(isWebAddress("https://"))
        assertFalse(isWebAddress("https://example.org/a b"))
        assertFalse(isWebAddress("javascript:alert(1)"))
    }
}
