package jaitsizazu.engine.desktop

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.core.UpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the real yt-dlp and ffmpeg against a local HTTP server started by the
 * test: nothing leaves the machine. The Docker build image installs both, so
 * this runs in `./gradlew check`; without them these tests fail on purpose.
 * yt-dlp's generic extractor downloads the served file as a direct link.
 * The cancellation test reads /proc, so it expects Linux.
 */
class YtDlpIntegrationTest {

    private val dir: Path = createTempDirectory("jaitsizazu-ytdlp")
    private val out: Path = dir.resolve("Downloads").createDirectory()
    private val stalled = CountDownLatch(1)
    private lateinit var server: HttpServer
    private lateinit var ytDlp: ToolLookup.Found
    private lateinit var ffmpeg: ToolLookup.Found
    private lateinit var clip: Path
    private lateinit var voice: Path
    private lateinit var downloader: YtDlpDownloader
    private val progress = CopyOnWriteArrayList<DownloadProgress>()

    private fun found(tool: ExternalTool): ToolLookup.Found =
        when (val lookup = ToolLocator(tool).locate()) {
            is ToolLookup.Found -> lookup
            is ToolLookup.NotFound -> fail("These tests need ${tool.binaryName}. ${lookup.problem}")
        }

    @BeforeTest
    fun setUp() = runBlocking {
        ffmpeg = found(ExternalTool.FFMPEG)
        ytDlp = found(ExternalTool.YT_DLP)
        val noDeno = ToolLookup.NotFound(Problem.ToolNotFound(Tool.DENO, ExternalTool.DENO.envVar, "bin/deno"))
        downloader = YtDlpDownloader({ ytDlp }, { ffmpeg }, { noDeno }, SystemProcessRunner())

        // A one-second video with AAC sound, like a short clip on a website.
        clip = dir.resolve("clip.mp4")
        makeWithFfmpeg(
            "-f", "lavfi", "-i", "testsrc=size=320x240:rate=10:duration=1",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=1",
            "-c:v", "mpeg4", "-c:a", "aac", "-shortest", clip.toString(),
        )
        val clipBytes = clip.readBytes()
        // One second of Opus sound in WebM, like the audio-only streams of YouTube.
        voice = dir.resolve("voice.webm")
        makeWithFfmpeg("-f", "lavfi", "-i", "sine=frequency=440:duration=1", "-c:a", "libopus", voice.toString())
        val voiceBytes = voice.readBytes()

        server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/clip.mp4") { exchange -> exchange.serve(clipBytes) }
        server.createContext("/voice.webm") { exchange -> exchange.serve(voiceBytes, contentType = "audio/webm") }
        // Sends the start of a large file, then waits: a download that never ends.
        server.createContext("/stalled.mp4") { exchange ->
            exchange.serve(ByteArray(STALLED_SIZE), sendOnly = 256 * 1024, thenWait = { stalled.await(60, TimeUnit.SECONDS) })
        }
        server.executor = Executors.newCachedThreadPool { Thread(it).apply { isDaemon = true } }
        server.start()
    }

    @AfterTest
    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    fun tearDown() {
        stalled.countDown()
        server.stop(0)
        dir.deleteRecursively()
    }

    private suspend fun makeWithFfmpeg(vararg arguments: String) {
        val made = SystemProcessRunner().run(listOf(ffmpeg.path, "-hide_banner", "-nostdin", "-loglevel", "error") + arguments)
        assertEquals(0, made.exitCode, made.stderrTail)
    }

    /**
     * A hash of the encoded packets of the first audio stream in [file]. It
     * stays the same when a stream is only copied into another container, and
     * changes when the sound is decoded and encoded again.
     */
    private fun audioPacketHash(file: Path): String = runBlocking {
        val lines = CopyOnWriteArrayList<String>()
        val outcome = SystemProcessRunner().run(
            listOf(ffmpeg.path, "-hide_banner", "-nostdin", "-loglevel", "error", "-i", file.toString()) +
                listOf("-map", "0:a:0", "-c", "copy", "-f", "streamhash", "-hash", "md5", "-"),
            lines::add,
        )
        assertEquals(0, outcome.exitCode, outcome.stderrTail)
        lines.single { it.startsWith("0,a,") }
    }

    private fun HttpExchange.serve(
        body: ByteArray,
        sendOnly: Int = body.size,
        contentType: String = "video/mp4",
        thenWait: () -> Unit = {},
    ) {
        responseHeaders.add("Content-Type", contentType)
        if (requestMethod == "HEAD") {
            responseHeaders.add("Content-Length", body.size.toString())
            sendResponseHeaders(200, -1)
        } else {
            sendResponseHeaders(200, body.size.toLong())
            runCatching {
                responseBody.write(body, 0, sendOnly)
                responseBody.flush()
                thenWait()
            }
        }
        runCatching { close() }
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun download(path: String, quality: DownloadQuality): MediaResult = runBlocking {
        downloader.download(DownloadRequest(url(path), quality, out.toString()), progress::add)
    }

    private fun savedFile(result: MediaResult): Path {
        assertIs<MediaResult.Success>(result, "$result")
        return Path.of(result.outputPath)
    }

    @Test
    fun downloadsAVideoAsMp4() {
        val saved = savedFile(download("/clip.mp4", DownloadQuality.VIDEO_MP4))

        assertEquals(out, saved.parent)
        assertTrue(saved.name.endsWith(".mp4"), saved.name)
        assertEquals("ftyp", saved.readBytes().copyOfRange(4, 8).decodeToString())
        assertEquals(listOf(saved), out.listDirectoryEntries(), "only the video is left in the folder")
        assertTrue(progress.any { it is DownloadProgress.Downloading }, "no progress was reported: $progress")
    }

    @Test
    fun downloadsTheAudioAsMp3() {
        val saved = savedFile(download("/clip.mp4", DownloadQuality.AUDIO_MP3))

        assertTrue(saved.name.endsWith(".mp3"), saved.name)
        val bytes = saved.readBytes()
        val id3 = bytes.copyOfRange(0, 3).decodeToString() == "ID3"
        val frameSync = bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0
        assertTrue(id3 || frameSync, "not an MP3 file")
        assertEquals(listOf(saved), out.listDirectoryEntries())
        assertTrue(DownloadProgress.Processing in progress, "the conversion step was not reported: $progress")
    }

    @Test
    fun downloadsTheAudioAsWav() {
        val saved = savedFile(download("/clip.mp4", DownloadQuality.AUDIO_WAV))

        assertTrue(saved.name.endsWith(".wav"), saved.name)
        val bytes = saved.readBytes()
        assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())
        assertEquals(listOf(saved), out.listDirectoryEntries())
    }

    @Test
    fun keepsTheOriginalAacAudioWithoutReEncodingIt() {
        val saved = savedFile(download("/clip.mp4", DownloadQuality.AUDIO_ORIGINAL))

        assertTrue(saved.name.endsWith(".m4a"), saved.name)
        assertEquals(audioPacketHash(clip), audioPacketHash(saved), "the AAC sound was encoded again")
        assertEquals(listOf(saved), out.listDirectoryEntries())
    }

    @Test
    fun keepsTheOriginalOpusAudioWithoutReEncodingIt() {
        val saved = savedFile(download("/voice.webm", DownloadQuality.AUDIO_ORIGINAL))

        assertTrue(saved.name.endsWith(".opus"), saved.name)
        assertEquals(audioPacketHash(voice), audioPacketHash(saved), "the Opus sound was encoded again")
        assertEquals(listOf(saved), out.listDirectoryEntries())
    }

    /**
     * The local server has one file with video and sound, so nothing is merged:
     * this only shows that the pinned yt-dlp accepts the options of this quality.
     * Merging into MP4 or MKV needs separate video and audio streams, as on
     * YouTube, and is not tested here.
     */
    @Test
    fun acceptsTheBestQualityOptions() {
        val saved = savedFile(download("/clip.mp4", DownloadQuality.VIDEO_BEST))

        assertTrue(saved.name.endsWith(".mp4"), saved.name)
        assertEquals(listOf(saved), out.listDirectoryEntries())
    }

    @Test
    fun neverOverwritesAnEarlierDownload() {
        val first = savedFile(download("/clip.mp4", DownloadQuality.VIDEO_MP4))
        val before = first.readBytes()

        val second = download("/clip.mp4", DownloadQuality.VIDEO_MP4)

        assertIs<MediaResult.Failure>(second)
        assertEquals(Problem.AlreadyInFolder(first.name, first.name), second.problem)
        assertTrue(before.contentEquals(first.readBytes()))
        assertEquals(listOf(first), out.listDirectoryEntries())
    }

    /** Whether [pid] runs and is not a zombie. Reads /proc, so it needs Linux. */
    private fun isRunning(pid: Long): Boolean {
        val stat = File("/proc/$pid/stat").takeIf { it.exists() }?.readText() ?: return false
        return stat.substringAfterLast(") ").firstOrNull() !in listOf('Z', 'X')
    }

    @Test
    fun cancellingStopsYtDlpAndLeavesNothingBehind() = runBlocking {
        val job = launch(Dispatchers.Default) {
            downloader.download(DownloadRequest(url("/stalled.mp4"), DownloadQuality.VIDEO_MP4, out.toString()), progress::add)
        }
        withTimeout(60_000) {
            while (progress.none { it is DownloadProgress.Downloading && (it.percent ?: 0.0) > 0.0 }) delay(50)
        }
        val ytDlpProcesses = ProcessHandle.current().descendants().map { it.pid() }.toList()
        assertTrue(ytDlpProcesses.isNotEmpty())

        withTimeout(30_000) { job.cancelAndJoin() }

        withTimeout(10_000) { while (ytDlpProcesses.any(::isRunning)) delay(50) }
        assertEquals(emptyList(), out.listDirectoryEntries(), "the cancelled download left files behind")
    }

    @Test
    fun refusesToUpdateAReadOnlyYtDlp() = runBlocking {
        val noNetwork = object : ProcessRunner {
            override suspend fun run(command: List<String>, onOutputLine: ((String) -> Unit)?): ProcessOutcome =
                fail("The update must be refused before yt-dlp runs.")
        }
        // A read-only copy in a folder of this test, so it does not depend on
        // who owns the yt-dlp that the environment installed.
        val bin = dir.resolve("bin").createDirectory()
        val copy = Files.copy(Path.of(ytDlp.path), bin.resolve("yt-dlp"))
        val file = copy.toFile()
        file.setExecutable(true)
        file.setWritable(false, false)
        try {
            // Root can write to any file, so this test needs a normal user, like the build image's.
            assertTrue(!Files.isWritable(copy), "run the tests as a non-root user: root can write to read-only files")

            val result = YtDlpUpdater({ ToolLookup.Found(copy.toString()) }, noNetwork).update()

            assertEquals(UpdateResult.Failed(Problem.ToolReadOnly(Tool.YT_DLP, copy.toString())), result)
        } finally {
            file.setWritable(true, true)
        }
    }

    private companion object {
        const val STALLED_SIZE = 16 * 1024 * 1024
    }
}
