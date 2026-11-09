package jaitsizazu.engine.android

import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.DownloadCancelledWithLeftovers
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadRequest
import jaitsizazu.core.MAX_FREE_NAME_COUNTER
import jaitsizazu.core.MediaConverter
import jaitsizazu.core.MediaDownloader
import jaitsizazu.core.MediaResult
import jaitsizazu.core.Problem
import jaitsizazu.core.firstFreePath
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

/** The public folder where finished files go on Android, as a file manager shows it. */
const val PUBLIC_FOLDER = "Download/Jaitsizazu"

/**
 * Copies a finished file out of the app's private cache into [PUBLIC_FOLDER],
 * where the user and other apps find it.
 */
fun interface MediaPublisher {
    /**
     * Copies [file] into [PUBLIC_FOLDER] under its own name, or under a free
     * variant of it, never over an existing file. Returns where the user finds
     * it, such as `Download/Jaitsizazu/clip.mp3`. Throws when it cannot: an
     * [IOException], a [SecurityException], or whatever MediaStore throws.
     */
    fun publish(file: Path): String
}

/**
 * [MediaConverter] that converts with [converter] inside the app's cache and
 * then publishes the result with [publisher]: the success path is the public
 * one. The converted file in the cache is removed in every case, so a failure
 * names no leftover there: the user could not reach it anyway.
 */
class PublishingConverter(
    private val converter: MediaConverter,
    private val publisher: MediaPublisher,
    private val deleteFile: (Path) -> Unit = { Files.deleteIfExists(it) },
) : MediaConverter {

    override suspend fun convert(request: ConversionRequest, outputPath: String): MediaResult {
        val output = Paths.get(outputPath)
        try {
            return when (val result = converter.convert(request, outputPath)) {
                is MediaResult.Success -> publisher.publishOrFail(Paths.get(result.outputPath))
                // Its unfinished file is the cache output, removed below.
                is MediaResult.Failure -> result.copy(leftover = null)
            }
        } finally {
            ignoringFailure { deleteFile(output) }
        }
    }
}

/**
 * [MediaDownloader] that downloads with [downloader] into a fresh job folder
 * of the app's cache, made by [createJobFolder], and then publishes the file
 * with [publisher]: the success path is the public one. The request's own
 * folder is not used.
 *
 * The job folder is removed in every case. A cache folder that cannot be
 * removed is not reported: the user cannot reach it, and the app clears old
 * job folders on its next start (see [JobFolders]).
 */
@OptIn(ExperimentalPathApi::class)
class PublishingDownloader(
    private val downloader: MediaDownloader,
    private val publisher: MediaPublisher,
    private val createJobFolder: () -> Path,
    private val deleteRecursively: (Path) -> Unit = { it.deleteRecursively() },
) : MediaDownloader {

    override suspend fun download(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit): MediaResult {
        val job = try {
            createJobFolder()
        } catch (e: IOException) {
            return MediaResult.Failure(Problem.CannotSaveInto(PUBLIC_FOLDER, e.message))
        }
        try {
            return when (val result = downloader.download(request.copy(outputDirectory = job.toString()), onProgress)) {
                is MediaResult.Success -> publisher.publishOrFail(Paths.get(result.outputPath))
                // Whatever it left behind is inside the job folder, removed below.
                is MediaResult.Failure -> result.copy(leftover = null)
            }
        } catch (e: DownloadCancelledWithLeftovers) {
            // The same: its leftover folder is inside the job folder.
            throw CancellationException("The download was cancelled.").apply { addSuppressed(e) }
        } finally {
            ignoringFailure { deleteRecursively(job) }
        }
    }
}

/**
 * Publishes [file], or says why it could not. Besides I/O and security errors,
 * MediaStore throws IllegalArgumentException, IllegalStateException and
 * UnsupportedOperationException, so any error but a cancellation counts.
 */
private fun MediaPublisher.publishOrFail(file: Path): MediaResult =
    try {
        MediaResult.Success(publish(file))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        MediaResult.Failure(Problem.CannotSaveInto(PUBLIC_FOLDER, e.message))
    }

private inline fun ignoringFailure(action: () -> Unit) {
    try {
        action()
    } catch (_: IOException) {
        // The cache is cleared on the next start.
    } catch (_: SecurityException) {
        // The same.
    }
}

/**
 * Creates a new file in [folder] named [name], or `name (1).ext`, `name (2).ext`
 * and so on when it is taken, like the converter names its files. [create]
 * must create the file and fail with [FileAlreadyExistsException] if it
 * exists, in one atomic step, so an existing file is never replaced.
 */
fun claimFreeFile(folder: Path, name: String, create: (Path) -> Unit = { Files.createFile(it) }): Path {
    val claimed = firstFreePath(folder.resolve(name).toString()) { candidate ->
        try {
            create(Paths.get(candidate))
            false
        } catch (_: FileAlreadyExistsException) {
            true
        }
    } ?: throw IOException("No free name for $name in $folder after $MAX_FREE_NAME_COUNTER tries")
    return Paths.get(claimed)
}

/**
 * A file name that is safe on every Android file system and in MediaStore,
 * made from [displayName], the name another app gave a file. Separators,
 * control characters and characters that FAT forbids become `_`; a name that
 * is empty or only dots becomes `file`; a name longer than [MAX_NAME_BYTES]
 * UTF-8 bytes is cut before its extension.
 */
fun safeFileName(displayName: String?): String {
    val cleaned = displayName.orEmpty()
        .map { if (it < ' ' || it == '\u007f' || it in FORBIDDEN) '_' else it }
        .joinToString("")
        .trim()
    if (cleaned.isEmpty() || cleaned.all { it == '.' }) return "file"
    if (cleaned.utf8Size() <= MAX_NAME_BYTES) return cleaned

    val dot = cleaned.lastIndexOf('.')
    val extension = if (dot > 0 && cleaned.length - dot <= MAX_EXTENSION_LENGTH) cleaned.substring(dot) else ""
    var base = cleaned.removeSuffix(extension)
    while (base.isNotEmpty() && (base + extension).utf8Size() > MAX_NAME_BYTES) {
        base = base.dropLast(1)
        // Never keep half of a surrogate pair.
        if (base.lastOrNull()?.isHighSurrogate() == true) base = base.dropLast(1)
    }
    return base + extension
}

private fun String.utf8Size(): Int = toByteArray(Charsets.UTF_8).size

/** Room for " (1000)" within the 255 bytes that file systems allow. */
const val MAX_NAME_BYTES = 240

private const val MAX_EXTENSION_LENGTH = 10

private val FORBIDDEN = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
