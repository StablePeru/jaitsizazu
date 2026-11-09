package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YtDlpOutputTest {

    @Test
    fun readsTheDownloadProgress() {
        assertEquals(
            DownloadProgress.Downloading(percent = 25.0, bytesPerSecond = 256.5, etaSeconds = 6),
            YtDlpOutput.progress("jaitsizazu-progress 512 2048 NA 256.5 6"),
        )
    }

    @Test
    fun fallsBackOnTheEstimatedSize() {
        assertEquals(
            DownloadProgress.Downloading(percent = 25.0, bytesPerSecond = null, etaSeconds = 3),
            YtDlpOutput.progress("jaitsizazu-progress 100 NA 400.0 NA 3.0"),
        )
    }

    @Test
    fun leavesUnknownValuesEmptyAndCapsThePercent() {
        val unknown = DownloadProgress.Downloading(null, null, null)

        assertEquals(unknown, YtDlpOutput.progress("jaitsizazu-progress NA NA NA NA NA"))
        assertEquals(unknown, YtDlpOutput.progress("jaitsizazu-progress 10 0 NA NA NA"))
        val capped = YtDlpOutput.progress("jaitsizazu-progress 900 800 NA NA NA") as DownloadProgress.Downloading
        assertEquals(100.0, capped.percent)
    }

    @Test
    fun readsTheProcessingStep() {
        assertEquals(DownloadProgress.Processing, YtDlpOutput.progress("jaitsizazu-processing started ExtractAudio"))
    }

    @Test
    fun readsTheSavedFileWithSpacesInItsName() {
        assertEquals("/dl/A song [abc].mp3", YtDlpOutput.savedFile("jaitsizazu-saved /dl/A song [abc].mp3"))
    }

    @Test
    fun ignoresOtherLines() {
        for (line in listOf("", "[download] Destination: x", "jaitsizazu-progress", "jaitsizazu-saved ", "WARNING: x")) {
            assertNull(YtDlpOutput.progress(line), line)
            assertNull(YtDlpOutput.savedFile(line), line)
        }
    }
}
