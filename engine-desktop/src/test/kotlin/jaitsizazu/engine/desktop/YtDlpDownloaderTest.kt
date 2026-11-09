package jaitsizazu.engine.desktop

import jaitsizazu.core.DownloadCancelledWithLeftovers
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlin.io.path.ExperimentalPathApi::class)
class YtDlpDownloaderTest {

    private val out: Path = createTempDirectory("jaitsizazu-downloads")
    private val request = DownloadRequest("https://example.org/clip", DownloadQuality.AUDIO_MP3, out.toString())
    private val saved = out.resolve("Clip [abc].mp3")
    private val progress = mutableListOf<DownloadProgress>()

    @AfterTest
    fun tearDown() {
        out.deleteRecursively()
    }

    /** Plays yt-dlp: runs [act] with the command, prints [lines], then exits with [exitCode]. */
    private class FakeYtDlp(
        private val lines: List<String> = emptyList(),
        private val exitCode: Int = 0,
        private val stderr: String = "",
        private val act: suspend (List<String>) -> Unit = {},
    ) : ProcessRunner {
        val commands = mutableListOf<List<String>>()

        override suspend fun run(command: List<String>, onOutputLine: ((String) -> Unit)?): ProcessOutcome {
            commands += command
            act(command)
            lines.forEach { onOutputLine?.invoke(it) }
            return ProcessOutcome(exitCode, stderr)
        }
    }

    private fun downloader(
        runner: ProcessRunner,
        deno: ToolLookup = ToolLookup.NotFound("deno was not found."),
        ytDlp: ToolLookup = ToolLookup.Found("/opt/yt-dlp"),
        ffmpeg: ToolLookup = ToolLookup.Found("/opt/ffmpeg"),
        createWorkDirectory: (Path) -> Path = { Files.createTempDirectory(it, YtDlpDownloader.WORK_DIRECTORY_PREFIX) },
        // Case-sensitive, like Linux, whatever the host. The integration tests use the real check.
        isSameFile: (Path, Path) -> Boolean = { a, b -> a.toString() == b.toString() },
        deleteRecursively: (Path) -> Unit = { it.deleteRecursively() },
    ) = YtDlpDownloader(
        { ytDlp }, { ffmpeg }, { deno }, runner,
        createWorkDirectory = createWorkDirectory,
        isSameFile = isSameFile,
        deleteRecursively = deleteRecursively,
    )

    /** Plays a case-insensitive file system, such as NTFS on Windows or APFS on macOS, on any host. */
    private val caseInsensitive: (Path, Path) -> Boolean = { a, b -> a.toString().equals(b.toString(), ignoreCase = true) }

    /** A delete that always fails, like Windows while a process still holds a file. */
    private var deleteAttempts = 0
    private val lockedDelete: (Path) -> Unit = {
        deleteAttempts++
        throw FileSystemException(it.toString(), null, "The process cannot access the file")
    }

    /** The temporary folder that the command hands to yt-dlp. */
    private fun workDirectoryOf(command: List<String>): Path =
        Path.of(command.single { it.startsWith("temp:") }.removePrefix("temp:"))

    private fun leftovers() = out.listDirectoryEntries().map { it.name }.filter { it.startsWith(".jaitsizazu") }

    private val savesTheClip = FakeYtDlp(
        lines = listOf(
            "[youtube] abc: Downloading webpage",
            "jaitsizazu-progress 1 4 NA 2.0 1",
            "jaitsizazu-processing started ExtractAudio",
            "jaitsizazu-saved $saved",
        ),
        act = { saved.writeText("mp3") },
    )

    @Test
    fun returnsTheSavedFileAndReportsProgress() = runTest {
        val result = downloader(savesTheClip).download(request, progress::add)

        assertEquals(MediaResult.Success(saved.toString()), result)
        assertEquals(listOf(DownloadProgress.Downloading(25.0, 2.0, 1), DownloadProgress.Processing), progress)
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun runsYtDlpWithTheBuiltArgumentsAndAFreshFolderInsideTheOutput() = runTest {
        downloader(savesTheClip, deno = ToolLookup.Found("/opt/deno")).download(request, progress::add)

        val command = savesTheClip.commands.single()
        val work = workDirectoryOf(command)
        assertEquals(out, work.parent)
        assertEquals(
            listOf("/opt/yt-dlp") +
                YtDlpArguments.download(request.url, request.quality, out.toString(), work.toString(), "/opt/ffmpeg", "/opt/deno"),
            command,
        )
    }

    @Test
    fun reportsAMissingYtDlpOrFfmpegWithoutRunningAnything() = runTest {
        val runner = FakeYtDlp()

        val noYtDlp = downloader(runner, ytDlp = ToolLookup.NotFound("yt-dlp was not found.")).download(request) {}
        val noFfmpeg = downloader(runner, ffmpeg = ToolLookup.NotFound("ffmpeg was not found.")).download(request) {}

        assertEquals(MediaResult.Failure("yt-dlp was not found."), noYtDlp)
        assertEquals(MediaResult.Failure("ffmpeg was not found."), noFfmpeg)
        assertTrue(runner.commands.isEmpty())
    }

    @Test
    fun saysSoWhenTheFileWasAlreadyThere() = runTest {
        saved.writeText("an earlier download")
        val runner = FakeYtDlp(lines = listOf("jaitsizazu-saved $saved"))

        val result = downloader(runner).download(request) {}

        assertIs<MediaResult.Failure>(result)
        assertTrue("already" in result.reason && "Clip [abc].mp3" in result.reason, result.reason)
        assertEquals("an earlier download", saved.readText())
    }

    @Test
    fun saysSoWhenTheFileWasAlreadyThereUnderAnotherCase() = runTest {
        // On a case-insensitive file system yt-dlp sees this file, keeps it,
        // and still names the saved file in its own spelling.
        out.resolve("clip [ABC].mp3").writeText("an earlier download")
        val runner = FakeYtDlp(lines = listOf("jaitsizazu-saved $saved"))

        val result = downloader(runner, isSameFile = caseInsensitive).download(request) {}

        assertIs<MediaResult.Failure>(result)
        assertTrue("already" in result.reason && "Clip [abc].mp3" in result.reason, result.reason)
        assertEquals("an earlier download", out.resolve("clip [ABC].mp3").readText())
    }

    @Test
    fun acceptsANewFileWhoseNameDiffersOnlyInCaseOnACaseSensitiveFileSystem() = runTest {
        out.resolve("clip [ABC].mp3").writeText("another file")

        val result = downloader(savesTheClip).download(request, progress::add)

        assertEquals(MediaResult.Success(saved.toString()), result)
    }

    @Test
    fun reportsAFailureWithTheStderrTailAndCleansUp() = runTest {
        val stderr = "jaitsizazu-processing started FFmpegExtractAudio\nERROR: Unsupported URL: https://example.org/clip"
        val runner = FakeYtDlp(exitCode = 1, stderr = stderr, act = { command ->
            Files.writeString(workDirectoryOf(command).resolve("Clip [abc].webm.part"), "partial")
        })

        val result = downloader(runner).download(request) {}

        assertIs<MediaResult.Failure>(result)
        assertTrue("exit code 1" in result.reason, result.reason)
        assertTrue("Unsupported URL" in result.reason, result.reason)
        assertTrue("jaitsizazu-" !in result.reason, "our own progress lines are not errors: ${result.reason}")
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun reportsASuccessWithoutAFile() = runTest {
        val result = downloader(FakeYtDlp()).download(request) {}

        assertIs<MediaResult.Failure>(result)
        assertTrue("no file" in result.reason, result.reason)
    }

    @Test
    fun cleansUpAndRethrowsWhenCancelled() = runTest {
        val runner = FakeYtDlp(act = { command ->
            Files.writeString(workDirectoryOf(command).resolve("Clip [abc].webm.part"), "partial")
            awaitCancellation()
        })
        var thrown: Throwable? = null

        val job = launch {
            try {
                downloader(runner).download(request) {}
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        testScheduler.advanceUntilIdle()
        job.cancel()
        job.join()

        assertIs<CancellationException>(thrown)
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun retriesRemovingTheWorkFolderBeforeGivingUp() = runTest {
        var attempts = 0
        val busyTwice: (Path) -> Unit = {
            if (++attempts < 3) throw FileSystemException(it.toString(), null, "in use")
            it.deleteRecursively()
        }
        val runner = FakeYtDlp(exitCode = 1, stderr = "ERROR: boom")

        val result = downloader(runner, deleteRecursively = busyTwice).download(request) {}

        assertIs<MediaResult.Failure>(result)
        assertTrue("could not be removed" !in result.reason, result.reason)
        assertEquals(3, attempts)
        assertEquals(emptyList(), leftovers())
    }

    @Test
    fun namesTheTemporaryFolderItCouldNotRemoveAfterAFailure() = runTest {
        val runner = FakeYtDlp(exitCode = 1, stderr = "ERROR: boom")

        val result = downloader(runner, deleteRecursively = lockedDelete).download(request) {}

        assertIs<MediaResult.Failure>(result)
        val work = workDirectoryOf(runner.commands.single())
        assertTrue("ERROR: boom" in result.reason, result.reason)
        assertTrue("Some temporary files could not be removed: $work" in result.reason, result.reason)
        assertEquals(3, deleteAttempts)
    }

    @Test
    fun namesTheTemporaryFolderItCouldNotRemoveAfterACancellation() = runTest {
        val runner = FakeYtDlp(act = { awaitCancellation() })
        var thrown: Throwable? = null

        val job = launch {
            try {
                downloader(runner, deleteRecursively = lockedDelete).download(request) {}
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        testScheduler.advanceUntilIdle()
        job.cancel()
        job.join()

        val leftover = assertIs<DownloadCancelledWithLeftovers>(thrown)
        assertEquals(workDirectoryOf(runner.commands.single()).toString(), leftover.folder)
        assertIs<FileSystemException>(leftover.suppressed.single())
        assertEquals(3, deleteAttempts)
    }

    @Test
    fun rethrowsThePlainCancellationWhenItCleanedUp() = runTest {
        var thrown: Throwable? = null
        val job = launch {
            try {
                downloader(FakeYtDlp(act = { awaitCancellation() })).download(request) {}
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        testScheduler.advanceUntilIdle()
        job.cancel()
        job.join()

        assertIs<CancellationException>(thrown)
        assertNull(thrown as? DownloadCancelledWithLeftovers)
    }

    @Test
    fun reportsAFolderItCannotWriteTo() = runTest {
        val runner = FakeYtDlp()

        val result = downloader(runner, createWorkDirectory = { throw AccessDeniedException(it.toString()) })
            .download(request) {}

        assertIs<MediaResult.Failure>(result)
        assertTrue("Cannot save into $out" in result.reason, result.reason)
        assertTrue(runner.commands.isEmpty())
    }
}
