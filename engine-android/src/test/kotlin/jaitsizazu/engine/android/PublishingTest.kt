package jaitsizazu.engine.android

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.DownloadCancelledWithLeftovers
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.Leftover
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaDownloader
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

@OptIn(ExperimentalPathApi::class)
class PublishingTest {

    private val cache: Path = Files.createTempDirectory("jaitsizazu-publishing-test")

    @AfterTest
    fun cleanUp() = cache.deleteRecursively()

    /** Records what it publishes, with its content, or fails with [failure]. */
    private class FakePublisher(private val failure: Exception? = null) : MediaPublisher {
        val published = mutableListOf<Pair<String, String>>()

        override fun publish(file: Path): String {
            failure?.let { throw it }
            published += file.fileName.toString() to file.readText()
            return "$PUBLIC_FOLDER/${file.fileName}"
        }
    }

    // Converter

    private val input = cache.resolve("memo.opus").also { it.writeText("opus") }
    private val output = cache.resolve("memo.wav")
    private val conversion = ConversionRequest(input.toString(), AudioFormat.WAV)

    /** A converter that runs [convert]. */
    private fun converter(convert: (String) -> MediaResult) = object : MediaConverter {
        override suspend fun convert(request: ConversionRequest, outputPath: String) = convert(outputPath)
    }

    private val converts = converter { outputPath ->
        Path.of(outputPath).writeText("wav")
        MediaResult.Success(outputPath)
    }

    @Test
    fun publishesTheConvertedFileAndRemovesItFromTheCache() = runTest {
        val publisher = FakePublisher()

        val result = PublishingConverter(converts, publisher).convert(conversion, output.toString())

        assertEquals(MediaResult.Success("$PUBLIC_FOLDER/memo.wav"), result)
        assertEquals(listOf("memo.wav" to "wav"), publisher.published)
        assertFalse(output.exists(), "the cache copy is still there")
        assertEquals("opus", input.readText())
    }

    @Test
    fun passesAFailedConversionOnAndPublishesNothing() = runTest {
        val failure = MediaResult.Failure(Problem.ConversionFailed(1, "Invalid data"))
        val publisher = FakePublisher()
        val fails = converter { outputPath ->
            Path.of(outputPath).writeText("half")
            failure
        }

        val result = PublishingConverter(fails, publisher).convert(conversion, output.toString())

        assertEquals(failure, result)
        assertEquals(emptyList(), publisher.published)
        assertFalse(output.exists())
    }

    @Test
    fun saysWhenTheConvertedFileCannotBePublished() = runTest {
        val publisher = FakePublisher(IOException("No space left on device"))

        val result = PublishingConverter(converts, publisher).convert(conversion, output.toString())

        assertEquals(MediaResult.Failure(Problem.CannotSaveInto(PUBLIC_FOLDER, "No space left on device")), result)
        assertFalse(output.exists())
    }

    @Test
    fun dropsTheLeftoverOfAFailedConversionWithTheCacheOutput() = runTest {
        // The user cannot reach the app's cache, so pointing at the file there would not help.
        val fails = converter { outputPath ->
            Path.of(outputPath).writeText("half")
            MediaResult.Failure(Problem.ConversionFailed(1, "Invalid data"), Leftover.UnfinishedFile(outputPath, "busy"))
        }

        val result = PublishingConverter(fails, FakePublisher()).convert(conversion, output.toString())

        assertEquals(MediaResult.Failure(Problem.ConversionFailed(1, "Invalid data")), result)
        assertFalse(output.exists())
    }

    @Test
    fun saysWhenMediaStoreRejectsTheConvertedFile() = runTest {
        // MediaStore throws more than I/O and security errors, for example when it finds no free name.
        for (rejection in listOf(
            IllegalStateException("Failed to build unique file"),
            IllegalArgumentException("Invalid column"),
            UnsupportedOperationException("Unknown URI"),
        )) {
            val result = PublishingConverter(converts, FakePublisher(rejection)).convert(conversion, output.toString())

            assertEquals(MediaResult.Failure(Problem.CannotSaveInto(PUBLIC_FOLDER, rejection.message)), result)
            assertFalse(output.exists())
        }
    }

    @Test
    fun passesACancellationWhilePublishingOn() = runTest {
        assertFailsWith<CancellationException> {
            PublishingConverter(converts, FakePublisher(CancellationException("cancelled"))).convert(conversion, output.toString())
        }
        assertFalse(output.exists())
    }

    @Test
    fun removesTheCacheOutputWhenTheConversionIsCancelled() = runTest {
        val cancelled = converter { outputPath ->
            Path.of(outputPath).writeText("half")
            throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            PublishingConverter(cancelled, FakePublisher()).convert(conversion, output.toString())
        }
        assertFalse(output.exists())
    }

    // Downloader

    private val jobs = cache.resolve("jobs").createDirectories()
    private val request = DownloadRequest("https://example.org/clip", DownloadQuality.AUDIO_MP3, PUBLIC_FOLDER)
    private val folders = mutableListOf<String>()

    private fun downloader(
        publisher: MediaPublisher,
        behaviour: (Path) -> MediaResult,
    ): PublishingDownloader {
        val inner = object : MediaDownloader {
            override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
                folders += request.outputDirectory
                onProgress(DownloadProgress.Processing)
                return behaviour(Path.of(request.outputDirectory))
            }
        }
        return PublishingDownloader(inner, publisher, createJobFolder = { Files.createTempDirectory(jobs, "download-") })
    }

    private val savesTheClip: (Path) -> MediaResult = { folder ->
        val file = folder.resolve("Clip [abc].mp3").also { it.writeText("mp3") }
        MediaResult.Success(file.toString())
    }

    @Test
    fun downloadsIntoAJobFolderThenPublishesAndRemovesIt() = runTest {
        val publisher = FakePublisher()
        val progress = mutableListOf<DownloadProgress>()

        val result = downloader(publisher, savesTheClip).download(request, progress::add)

        assertEquals(MediaResult.Success("$PUBLIC_FOLDER/Clip [abc].mp3"), result)
        assertEquals(listOf("Clip [abc].mp3" to "mp3"), publisher.published)
        assertEquals(listOf<DownloadProgress>(DownloadProgress.Processing), progress)
        assertEquals(jobs, Path.of(folders.single()).parent)
        assertEquals(emptyList(), jobs.listDirectoryEntries(), "the job folder is still there")
    }

    @Test
    fun dropsLeftoversOfAFailedDownloadWithItsJobFolder() = runTest {
        val publisher = FakePublisher()
        val result = downloader(publisher) { folder ->
            folder.resolve(".jaitsizazu-1").createDirectories().resolve("part").writeText("x")
            MediaResult.Failure(Problem.DownloadFailed(1, "ERROR"), Leftover.TemporaryFolder("$folder/.jaitsizazu-1", "busy"))
        }.download(request) {}

        assertEquals(MediaResult.Failure(Problem.DownloadFailed(1, "ERROR")), result)
        assertEquals(emptyList(), publisher.published)
        assertEquals(emptyList(), jobs.listDirectoryEntries())
    }

    @Test
    fun turnsACancellationWithLeftoversIntoAPlainOneOnceTheJobFolderIsGone() = runTest {
        val error = assertFailsWith<CancellationException> {
            downloader(FakePublisher()) { folder -> throw DownloadCancelledWithLeftovers("$folder/.jaitsizazu-1") }
                .download(request) {}
        }

        assertFalse(error is DownloadCancelledWithLeftovers)
        assertEquals(emptyList(), jobs.listDirectoryEntries())
    }

    @Test
    fun saysWhenTheDownloadCannotBePublishedOrHasNoJobFolder() = runTest {
        val notPublished = downloader(FakePublisher(SecurityException("denied")), savesTheClip).download(request) {}
        assertEquals(MediaResult.Failure(Problem.CannotSaveInto(PUBLIC_FOLDER, "denied")), notPublished)

        val rejected = downloader(FakePublisher(IllegalStateException("Failed to build unique file")), savesTheClip)
            .download(request) {}
        assertEquals(MediaResult.Failure(Problem.CannotSaveInto(PUBLIC_FOLDER, "Failed to build unique file")), rejected)
        assertEquals(emptyList(), jobs.listDirectoryEntries())

        val noFolder = PublishingDownloader(
            downloader(FakePublisher()) { error("must not run") },
            FakePublisher(),
            createJobFolder = { throw IOException("read-only cache") },
        ).download(request) {}
        assertIs<MediaResult.Failure>(noFolder)
        assertEquals(Problem.CannotSaveInto(PUBLIC_FOLDER, "read-only cache"), noFolder.problem)
    }
}
