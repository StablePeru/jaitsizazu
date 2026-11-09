package jaitsizazu.core

/**
 * Why an operation failed, as data. The UI turns each problem into a sentence
 * in the user's language, so no user-facing text lives in `core` or in the
 * engines. Paths, addresses and [detail][CannotCreateFile.detail] texts are
 * shown as they are. So is a [toolOutput][ConversionFailed.toolOutput]: the
 * last lines an external tool wrote, raw and in the tool's own language.
 */
sealed interface Problem {
    /** No file was chosen to convert. */
    data object NoFileChosen : Problem

    /** [path] names a folder, not a file. */
    data class NotAFile(val path: String) : Problem

    /** The file at [path] does not exist. */
    data class FileNotFound(val path: String) : Problem

    /** Every name the converted file could get next to [inputPath] is taken. */
    data class NoFreeName(val inputPath: String) : Problem

    /** No web address was given. */
    data object NoWebAddress : Problem

    /** [text] is not an `https://` or `http://` address. */
    data class NotAWebAddress(val text: String) : Problem

    /** No folder was chosen to save the download in. */
    data object NoFolderChosen : Problem

    /** The folder at [path] does not exist. */
    data class FolderNotFound(val path: String) : Problem

    /**
     * [tool] was found nowhere. [envVar] may hold its full path, and
     * [bundledPath] is where it may sit inside the app folder.
     */
    data class ToolNotFound(val tool: Tool, val envVar: String, val bundledPath: String) : Problem

    /** [envVar] is set to [configured], but there is no executable [tool] there. */
    data class ToolNotAtConfiguredPath(val tool: Tool, val envVar: String, val configured: String) : Problem

    /** [tool] at [path] could not be started. [detail] is the system's own message, if any. */
    data class ToolDidNotStart(val tool: Tool, val path: String, val detail: String?) : Problem

    /** [tool] at [path] cannot be updated, because the user cannot write to it. */
    data class ToolReadOnly(val tool: Tool, val path: String) : Problem

    /** The output file at [path] already exists. Nothing was overwritten. */
    data class FileAlreadyExists(val path: String) : Problem

    /** The output file at [path] could not be created. [detail] is the system's own message, if any. */
    data class CannotCreateFile(val path: String, val detail: String?) : Problem

    /** Nothing can be saved into [folder]. [detail] is the system's own message, if any. */
    data class CannotSaveInto(val folder: String, val detail: String?) : Problem

    /** ffmpeg ended with [exitCode]. */
    data class ConversionFailed(val exitCode: Int, val toolOutput: String) : Problem

    /** yt-dlp ended with [exitCode]. */
    data class DownloadFailed(val exitCode: Int, val toolOutput: String) : Problem

    /** yt-dlp ended without an error, but named no saved file. */
    data object NoFileReported : Problem

    /**
     * The downloaded file [name] was already in the folder, as [existingName],
     * which differs from [name] only in case or Unicode form, or is equal to it.
     * Nothing was overwritten.
     */
    data class AlreadyInFolder(val name: String, val existingName: String) : Problem

    /** yt-dlp could not update itself and ended with [exitCode]. */
    data class UpdateFailed(val exitCode: Int, val toolOutput: String) : Problem

    /**
     * The new yt-dlp could not be fetched or installed (on Android, where the
     * app updates yt-dlp itself). [detail] is the system's own message, if any.
     */
    data class UpdateNotDownloaded(val detail: String?) : Problem

    /**
     * [tool] ships inside the app (on Android), but it is not ready at [path]:
     * the app could not unpack or find it. [detail] is the system's own message, if any.
     */
    data class ToolNotReadyInApp(val tool: Tool, val path: String, val detail: String?) : Problem

    /** Something nobody expected went wrong. [detail] is the error's own message. */
    data class Unexpected(val detail: String) : Problem
}

/** The external programs the engines run. [displayName] is the name users know. */
enum class Tool(val displayName: String) {
    FFMPEG("ffmpeg"),
    YT_DLP("yt-dlp"),
    DENO("deno"),

    /** The JavaScript runtime that YouTube downloads need on Android, where deno is not used. */
    QUICKJS("QuickJS"),
}

/** Temporary output that a failed operation could not remove, for the user to delete. */
sealed interface Leftover {
    val path: String

    /** The system's own message, if any. */
    val detail: String?

    /** The unfinished output file of a conversion. */
    data class UnfinishedFile(override val path: String, override val detail: String?) : Leftover

    /** The temporary folder of a download. */
    data class TemporaryFolder(override val path: String, override val detail: String?) : Leftover
}
