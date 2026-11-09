package jaitsizazu.engine.desktop

import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.core.UpdateResult
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YtDlpUpdaterTest {

    private class FakeRunner(
        private val lines: List<String> = emptyList(),
        private val outcome: () -> ProcessOutcome = { ProcessOutcome(0, "") },
    ) : ProcessRunner {
        val commands = mutableListOf<List<String>>()

        override suspend fun run(command: List<String>, onOutputLine: ((String) -> Unit)?): ProcessOutcome {
            commands += command
            lines.forEach { onOutputLine?.invoke(it) }
            return outcome()
        }
    }

    private val binary = Path.of("/opt/app/bin/yt-dlp")

    private fun updater(
        runner: ProcessRunner,
        writable: (Path) -> Boolean = { true },
        lookup: ToolLookup = ToolLookup.Found(binary.toString()),
    ) = YtDlpUpdater({ lookup }, runner, isWritable = writable)

    @Test
    fun updatesTheBinaryItUsesInPlace() = runTest {
        val runner = FakeRunner(listOf("Current version: stable@2026.10.26", "Updated yt-dlp to stable@2026.11.20", ""))

        val result = updater(runner).update()

        assertEquals(UpdateResult.Updated("Updated yt-dlp to stable@2026.11.20"), result)
        assertEquals(listOf(listOf(binary.toString()) + YtDlpArguments.update()), runner.commands)
    }

    @Test
    fun saysWhenItIsAlreadyUpToDate() = runTest {
        val result = updater(FakeRunner(listOf("yt-dlp is up to date (stable@2026.10.26 from yt-dlp/yt-dlp)"))).update()

        assertEquals(UpdateResult.Updated("yt-dlp is up to date (stable@2026.10.26 from yt-dlp/yt-dlp)"), result)
    }

    @Test
    fun reportsTheUpdateLineRatherThanALaterWarning() = runTest {
        val runner = FakeRunner(listOf("Updated yt-dlp to stable@2026.11.20", "WARNING: something unrelated"))

        assertEquals(UpdateResult.Updated("Updated yt-dlp to stable@2026.11.20"), updater(runner).update())
    }

    @Test
    fun saysWhatToDoWhenYtDlpDoesNotNameItsVersion() = runTest {
        val silent = updater(FakeRunner()).update()
        val unclear = updater(FakeRunner(listOf("Current version: stable@2026.10.26", "WARNING: something unrelated"))).update()

        // The UI says what to do next, and quotes the last line as it is.
        assertEquals(UpdateResult.Unclear(lastLine = null), silent)
        assertEquals(UpdateResult.Unclear(lastLine = "WARNING: something unrelated"), unclear)
    }

    @Test
    fun refusesAReadOnlyBinaryOrFolderWithoutRunningIt() = runTest {
        val runner = FakeRunner()

        val readOnlyFile = updater(runner, writable = { false }).update()
        val readOnlyFolder = updater(runner, writable = { it == binary }).update()

        for (result in listOf(readOnlyFile, readOnlyFolder)) {
            assertEquals(UpdateResult.Failed(Problem.ToolReadOnly(Tool.YT_DLP, binary.toString())), result)
        }
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun reportsAFailedUpdate() = runTest {
        val runner = FakeRunner(outcome = { ProcessOutcome(1, "ERROR: Unable to download latest version") })

        val result = updater(runner).update()

        assertEquals(UpdateResult.Failed(Problem.UpdateFailed(1, "ERROR: Unable to download latest version")), result)
    }

    @Test
    fun reportsAMissingYtDlpOrOneThatCannotStart() = runTest {
        val notFound = Problem.ToolNotFound(Tool.YT_DLP, "JAITSIZAZU_YTDLP", "bin/yt-dlp")
        val missing = updater(FakeRunner(), lookup = ToolLookup.NotFound(notFound)).update()
        val broken = updater(FakeRunner(outcome = { throw IOException("error=13, Permission denied") })).update()

        assertEquals(UpdateResult.Failed(notFound), missing)
        assertEquals(
            UpdateResult.Failed(Problem.ToolDidNotStart(Tool.YT_DLP, binary.toString(), "error=13, Permission denied")),
            broken,
        )
    }
}
