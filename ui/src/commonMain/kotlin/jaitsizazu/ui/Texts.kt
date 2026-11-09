package jaitsizazu.ui

import androidx.compose.runtime.Composable
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.Leftover
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.leftover_temporary_folder
import jaitsizazu.ui.resources.leftover_unfinished_file
import jaitsizazu.ui.resources.problem_already_in_folder
import jaitsizazu.ui.resources.problem_already_in_folder_as
import jaitsizazu.ui.resources.problem_cannot_create_file
import jaitsizazu.ui.resources.problem_cannot_save_into
import jaitsizazu.ui.resources.problem_conversion_failed
import jaitsizazu.ui.resources.problem_download_failed
import jaitsizazu.ui.resources.problem_file_already_exists
import jaitsizazu.ui.resources.problem_file_not_found
import jaitsizazu.ui.resources.problem_folder_not_found
import jaitsizazu.ui.resources.problem_no_file_chosen
import jaitsizazu.ui.resources.problem_no_file_reported
import jaitsizazu.ui.resources.problem_no_folder_chosen
import jaitsizazu.ui.resources.problem_no_free_name
import jaitsizazu.ui.resources.problem_no_web_address
import jaitsizazu.ui.resources.problem_not_a_file
import jaitsizazu.ui.resources.problem_not_a_web_address
import jaitsizazu.ui.resources.problem_tool_did_not_start
import jaitsizazu.ui.resources.problem_tool_not_at_configured_path
import jaitsizazu.ui.resources.problem_tool_not_found
import jaitsizazu.ui.resources.problem_tool_read_only
import jaitsizazu.ui.resources.problem_unexpected
import jaitsizazu.ui.resources.problem_update_failed
import jaitsizazu.ui.resources.quality_audio_mp3
import jaitsizazu.ui.resources.quality_audio_original
import jaitsizazu.ui.resources.quality_audio_original_hint
import jaitsizazu.ui.resources.quality_audio_wav
import jaitsizazu.ui.resources.quality_video_best
import jaitsizazu.ui.resources.quality_video_best_hint
import jaitsizazu.ui.resources.quality_video_mp4
import jaitsizazu.ui.resources.tool_purpose_deno
import jaitsizazu.ui.resources.tool_purpose_ffmpeg
import jaitsizazu.ui.resources.tool_purpose_yt_dlp
import org.jetbrains.compose.resources.stringResource

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
    Problem.NoFileChosen -> stringResource(Res.string.problem_no_file_chosen)
    is Problem.NotAFile -> stringResource(Res.string.problem_not_a_file, problem.path)
    is Problem.FileNotFound -> stringResource(Res.string.problem_file_not_found, problem.path)
    is Problem.NoFreeName -> stringResource(Res.string.problem_no_free_name, problem.inputPath)
    Problem.NoWebAddress -> stringResource(Res.string.problem_no_web_address)
    is Problem.NotAWebAddress -> stringResource(Res.string.problem_not_a_web_address, problem.text)
    Problem.NoFolderChosen -> stringResource(Res.string.problem_no_folder_chosen)
    is Problem.FolderNotFound -> stringResource(Res.string.problem_folder_not_found, problem.path)
    is Problem.ToolNotFound -> stringResource(
        Res.string.problem_tool_not_found,
        problem.tool.displayName,
        toolPurpose(problem.tool),
        problem.envVar,
        problem.bundledPath,
    )
    is Problem.ToolNotAtConfiguredPath -> stringResource(
        Res.string.problem_tool_not_at_configured_path,
        problem.envVar,
        problem.configured,
        problem.tool.displayName,
    )
    is Problem.ToolDidNotStart ->
        stringResource(Res.string.problem_tool_did_not_start, problem.tool.displayName, problem.path)
            .withRawLines(problem.detail)
    is Problem.ToolReadOnly -> stringResource(Res.string.problem_tool_read_only, problem.tool.displayName, problem.path)
    is Problem.FileAlreadyExists -> stringResource(Res.string.problem_file_already_exists, problem.path)
    is Problem.CannotCreateFile ->
        stringResource(Res.string.problem_cannot_create_file, problem.path).withRawLines(problem.detail)
    is Problem.CannotSaveInto ->
        stringResource(Res.string.problem_cannot_save_into, problem.folder).withRawLines(problem.detail)
    is Problem.ConversionFailed ->
        stringResource(Res.string.problem_conversion_failed, problem.exitCode).withRawLines(problem.toolOutput)
    is Problem.DownloadFailed ->
        stringResource(Res.string.problem_download_failed, problem.exitCode).withRawLines(problem.toolOutput)
    Problem.NoFileReported -> stringResource(Res.string.problem_no_file_reported)
    is Problem.AlreadyInFolder ->
        if (problem.existingName == problem.name) {
            stringResource(Res.string.problem_already_in_folder, problem.name)
        } else {
            stringResource(Res.string.problem_already_in_folder_as, problem.name, problem.existingName)
        }
    is Problem.UpdateFailed ->
        stringResource(Res.string.problem_update_failed, problem.exitCode).withRawLines(problem.toolOutput)
    is Problem.Unexpected -> stringResource(Res.string.problem_unexpected, problem.detail)
}

/** The sentence about [leftover], with the system's own message on the next line. */
@Composable
internal fun leftoverText(leftover: Leftover): String = when (leftover) {
    is Leftover.UnfinishedFile -> stringResource(Res.string.leftover_unfinished_file, leftover.path)
    is Leftover.TemporaryFolder -> stringResource(Res.string.leftover_temporary_folder, leftover.path)
}.withRawLines(leftover.detail)

/** Completes "Jaitsizazu needs it to ...", in the app's language. */
@Composable
private fun toolPurpose(tool: Tool): String = when (tool) {
    Tool.FFMPEG -> stringResource(Res.string.tool_purpose_ffmpeg)
    Tool.YT_DLP -> stringResource(Res.string.tool_purpose_yt_dlp)
    Tool.DENO -> stringResource(Res.string.tool_purpose_deno)
}

/** The name of [quality] in the list of choices. */
@Composable
internal fun qualityLabel(quality: DownloadQuality): String = when (quality) {
    DownloadQuality.AUDIO_MP3 -> stringResource(Res.string.quality_audio_mp3)
    DownloadQuality.AUDIO_WAV -> stringResource(Res.string.quality_audio_wav)
    DownloadQuality.AUDIO_ORIGINAL -> stringResource(Res.string.quality_audio_original)
    DownloadQuality.VIDEO_MP4 -> stringResource(Res.string.quality_video_mp4)
    DownloadQuality.VIDEO_BEST -> stringResource(Res.string.quality_video_best)
}

/** A short note under [quality] in the list of choices, or null when it needs none. */
@Composable
internal fun qualityHint(quality: DownloadQuality): String? = when (quality) {
    DownloadQuality.AUDIO_ORIGINAL -> stringResource(Res.string.quality_audio_original_hint)
    DownloadQuality.VIDEO_BEST -> stringResource(Res.string.quality_video_best_hint)
    DownloadQuality.AUDIO_MP3, DownloadQuality.AUDIO_WAV, DownloadQuality.VIDEO_MP4 -> null
}

/** This sentence, then [raw] (a system message or a tool's output) as it is, on the next line. */
private fun String.withRawLines(raw: String?): String = if (raw.isNullOrBlank()) this else "$this\n$raw"
