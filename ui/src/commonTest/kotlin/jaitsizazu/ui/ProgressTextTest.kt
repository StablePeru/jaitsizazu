package jaitsizazu.ui

import jaitsizazu.core.DownloadProgress
import kotlin.test.Test
import kotlin.test.assertEquals

/** The words are given in English here; StringResourcesTest checks the real ones in both languages. */
class ProgressTextTest {

    private val english = ProgressWords(
        starting = "Starting…",
        downloading = "Downloading…",
        finishing = "Finishing the file…",
        percent = { "$it%" },
        timeLeft = { "$it left" },
    )

    @Test
    fun saysWhatIsKnown() {
        assertEquals("42% · 1.5 KB/s · 1:05 left", progressText(DownloadProgress.Downloading(42.7, 1536.0, 65), english))
        assertEquals("42%", progressText(DownloadProgress.Downloading(42.7, null, null), english))
        assertEquals("Downloading…", progressText(DownloadProgress.Downloading(null, null, null), english))
    }

    @Test
    fun namesTheStepsBeforeAndAfterTheBytes() {
        assertEquals("Starting…", progressText(null, english))
        assertEquals("Finishing the file…", progressText(DownloadProgress.Processing, english))
    }

    @Test
    fun putsThePercentWhereTheLanguageWantsIt() {
        val basqueOrder = english.copy(percent = { "%$it" })

        assertEquals("%42", progressText(DownloadProgress.Downloading(42.7, null, null), basqueOrder))
    }

    @Test
    fun formatsSpeedsInBinaryUnits() {
        assertEquals("5 B", formatBytes(5.0))
        assertEquals("1023 B", formatBytes(1023.0))
        assertEquals("12.3 MB", formatBytes(12.34 * 1024 * 1024))
        assertEquals("2.0 GB", formatBytes(2.0 * 1024 * 1024 * 1024))
        assertEquals("1,5 KB", formatBytes(1536.0, decimalSeparator = ","))
    }

    @Test
    fun formatsTimesAsClockDurations() {
        assertEquals("0:05", formatDuration(5))
        assertEquals("10:00", formatDuration(600))
        assertEquals("1:01:05", formatDuration(3665))
    }
}
