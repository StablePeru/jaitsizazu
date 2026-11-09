package jaitsizazu.ui

import androidx.compose.runtime.Composable
import jaitsizazu.core.Leftover
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool

// Turns the typed results of core and the engines into words, in the app's
// language. Paths, addresses, system messages and the output of the external
// tools are quoted as they are: only the sentence around them is translated.

/** What the user reads about [problem], followed by what [leftover] left behind, if anything. */
@Composable
internal fun failureText(problem: Problem, leftover: Leftover? = null): String =
    listOfNotNull(problemText(problem), leftover?.let { leftoverText(it) }).joinToString("\n")

/** The sentence about [problem] in the app's language. */
@Composable
internal fun problemText(problem: Problem): String = when (problem) {
    Problem.NoFileChosen -> "Choose a file to convert."
    is Problem.NotAFile -> "${problem.path} is not a file."
    is Problem.FileNotFound -> "The file ${problem.path} does not exist."
    is Problem.NoFreeName -> "No free name left for the converted file next to ${problem.inputPath}."
    Problem.NoWebAddress -> "Paste the web address of a video or a song first."
    is Problem.NotAWebAddress -> "${problem.text} is not a web address. It must start with https:// or http://."
    Problem.NoFolderChosen -> "Choose a folder to save the download in."
    is Problem.FolderNotFound -> "The folder ${problem.path} does not exist."
    is Problem.ToolNotFound -> problem.tool.displayName.let { name ->
        "$name was not found. Jaitsizazu needs it to ${toolPurpose(problem.tool)}. " +
            "Install $name so that it is on your PATH, " +
            "or set ${problem.envVar} to the full path of the $name binary, " +
            "or put it in the app folder as ${problem.bundledPath}. Then try again. " +
            "If you changed PATH or ${problem.envVar}, restart Jaitsizazu first."
    }
    is Problem.ToolNotAtConfiguredPath ->
        "${problem.envVar} is set to ${problem.configured}, but there is no executable ${problem.tool.displayName} there."
    is Problem.ToolDidNotStart ->
        "Could not start ${problem.tool.displayName} (${problem.path})."
            .withRawLines(problem.detail)
    is Problem.ToolReadOnly ->
        "${problem.tool.displayName} at ${problem.path} is read-only for your user, so Jaitsizazu cannot update it. " +
            "Update it the way it was installed (for example, with your package manager), " +
            "or put a copy you can write to in the bin folder of the app."
    is Problem.FileAlreadyExists -> "${problem.path} already exists. Nothing was overwritten."
    is Problem.CannotCreateFile ->
        "Could not create ${problem.path}.".withRawLines(problem.detail)
    is Problem.CannotSaveInto ->
        "Cannot save into ${problem.folder}.".withRawLines(problem.detail)
    is Problem.ConversionFailed ->
        "The conversion failed (ffmpeg exit code ${problem.exitCode}).".withRawLines(problem.toolOutput)
    is Problem.DownloadFailed ->
        "The download failed (yt-dlp exit code ${problem.exitCode}).".withRawLines(problem.toolOutput)
    Problem.NoFileReported -> "yt-dlp finished, but it reported no file."
    is Problem.AlreadyInFolder ->
        if (problem.existingName == problem.name) {
            "${problem.name} is already in this folder. Nothing was overwritten."
        } else {
            "${problem.name} is already in this folder, as ${problem.existingName}. Nothing was overwritten."
        }
    is Problem.UpdateFailed ->
        "yt-dlp could not update itself (exit code ${problem.exitCode}).".withRawLines(problem.toolOutput)
    is Problem.Unexpected -> "Unexpected error: ${problem.detail}"
}

/** The sentence about [leftover], with the system's own message on the next line. */
@Composable
internal fun leftoverText(leftover: Leftover): String = when (leftover) {
    is Leftover.UnfinishedFile -> "The unfinished file ${leftover.path} could not be removed."
    is Leftover.TemporaryFolder -> "Some temporary files could not be removed: ${leftover.path}. You can delete that folder."
}.withRawLines(leftover.detail)

/** Completes "Jaitsizazu needs it to ...", in the app's language. */
@Composable
private fun toolPurpose(tool: Tool): String = when (tool) {
    Tool.FFMPEG -> "convert files and to finish downloads"
    Tool.YT_DLP -> "download"
    Tool.DENO -> "download from YouTube"
}

/** This sentence, then [raw] (a system message or a tool's output) as it is, on the next line. */
private fun String.withRawLines(raw: String?): String = if (raw.isNullOrBlank()) this else "$this\n$raw"
