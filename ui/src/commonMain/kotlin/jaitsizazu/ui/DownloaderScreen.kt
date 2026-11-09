package jaitsizazu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jaitsizazu.core.DownloadProgress
import jaitsizazu.core.DownloadQuality
import jaitsizazu.core.fileNameOf

/** Shown on the download screen at all times. */
const val DOWNLOAD_LEGAL_NOTE =
    "Download only your own content, public domain or freely licensed works, or personal copies where the law allows."

/**
 * The downloader screen. Stateless: it shows [state] and reports user actions
 * through the callbacks. The caller owns the state and the folder chooser.
 * While a download runs, a Cancel button reports [onCancel].
 */
@Composable
fun DownloaderScreen(
    state: DownloaderState,
    onUrlChange: (String) -> Unit,
    onQualitySelected: (DownloadQuality) -> Unit,
    onChooseFolder: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = !state.isBusy
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Download from a web address", style = MaterialTheme.typography.h5)
        Text(DOWNLOAD_LEGAL_NOTE, style = MaterialTheme.typography.body2)

        state.setupWarning?.let { warning ->
            SelectionContainer { Text(warning, color = MaterialTheme.colors.error) }
        }

        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChange,
            label = { Text("Web address") },
            placeholder = { Text("https://") },
            singleLine = true,
            enabled = idle,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Save as", style = MaterialTheme.typography.subtitle1)
        ChoiceGroup(DownloadQuality.entries, state.quality, idle, { it.label }, onQualitySelected)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onChooseFolder, enabled = idle) { Text("Change folder") }
            Text(
                text = state.outputDirectory.ifBlank { "No folder chosen yet." },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDownload, enabled = state.canDownload) { Text("Download") }
            if (state.status is DownloadStatus.Downloading) OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }

        DownloadStatusBlock(state.status)

        Divider()
        UpdateBlock(state.update, enabled = state.canUpdate, onUpdate = onUpdate)
    }
}

@Composable
private fun DownloadStatusBlock(status: DownloadStatus) {
    when (status) {
        DownloadStatus.Idle -> Unit
        is DownloadStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val percent = (status.progress as? DownloadProgress.Downloading)?.percent
            if (percent != null) {
                LinearProgressIndicator(progress = (percent / 100).toFloat(), modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(progressText(status.progress))
        }
        is DownloadStatus.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Saved as ${fileNameOf(status.outputPath)}")
            SelectionContainer { Text(status.outputPath, style = MaterialTheme.typography.body2) }
        }
        is DownloadStatus.Failed -> SelectionContainer { Text(status.reason, color = MaterialTheme.colors.error) }
        DownloadStatus.Cancelled -> Text("Download cancelled.")
    }
}

@Composable
private fun UpdateBlock(update: UpdateStatus, enabled: Boolean, onUpdate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Sites such as YouTube change often. If downloads stop working, update yt-dlp, the download engine.",
            style = MaterialTheme.typography.body2,
        )
        OutlinedButton(onClick = onUpdate, enabled = enabled) { Text("Update yt-dlp") }
        when (update) {
            UpdateStatus.Idle -> Unit
            UpdateStatus.Updating -> Text("Updating yt-dlp…")
            is UpdateStatus.Done -> SelectionContainer { Text(update.message) }
            is UpdateStatus.Failed -> SelectionContainer { Text(update.reason, color = MaterialTheme.colors.error) }
        }
    }
}

private val DownloadQuality.label: String
    get() = when (this) {
        DownloadQuality.AUDIO_MP3 -> "Audio (MP3)"
        DownloadQuality.VIDEO_MP4 -> "Video (MP4, up to 1080p)"
    }
