package jaitsizazu.core

/**
 * Use case: download the media at a web address into a folder.
 *
 * It checks the request before the engine runs: the address must be a web
 * address (`https://` or `http://`), so a local file or a text that looks like
 * an option never reaches the engine, and the folder must exist. [isDirectory]
 * tells whether a path is a folder, so this class stays free of I/O.
 */
class DownloadMedia(
    private val downloader: MediaDownloader,
    private val isDirectory: (String) -> Boolean,
) {
    suspend operator fun invoke(request: DownloadRequest, onProgress: (DownloadProgress) -> Unit = {}): MediaResult {
        val url = request.url.trim()
        if (url.isEmpty()) return MediaResult.Failure(Problem.NoWebAddress)
        if (!isWebAddress(url)) return MediaResult.Failure(Problem.NotAWebAddress(url))
        val folder = request.outputDirectory
        if (folder.isBlank()) return MediaResult.Failure(Problem.NoFolderChosen)
        if (!isDirectory(folder)) return MediaResult.Failure(Problem.FolderNotFound(folder))

        return downloader.download(request.copy(url = url), onProgress)
    }
}

/** Whether [text] is an `http` or `https` address with something after the scheme and no spaces. */
fun isWebAddress(text: String): Boolean {
    val scheme = listOf("https://", "http://").firstOrNull { text.startsWith(it, ignoreCase = true) } ?: return false
    return text.length > scheme.length && text.none { it.isWhitespace() }
}
