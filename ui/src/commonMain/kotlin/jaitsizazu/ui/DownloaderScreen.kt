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
import jaitsizazu.core.Leftover
import jaitsizazu.core.fileNameOf
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.cancel
import jaitsizazu.ui.resources.cancelling
import jaitsizazu.ui.resources.change_folder
import jaitsizazu.ui.resources.download_button
import jaitsizazu.ui.resources.download_cancelled
import jaitsizazu.ui.resources.download_title
import jaitsizazu.ui.resources.legal_note
import jaitsizazu.ui.resources.no_folder_chosen
import jaitsizazu.ui.resources.save_as
import jaitsizazu.ui.resources.saved_as
import jaitsizazu.ui.resources.update_button
import jaitsizazu.ui.resources.update_done
import jaitsizazu.ui.resources.update_explainer
import jaitsizazu.ui.resources.update_unclear
import jaitsizazu.ui.resources.update_unclear_last_line
import jaitsizazu.ui.resources.updating
import jaitsizazu.ui.resources.web_address
import org.jetbrains.compose.resources.stringResource

/**
 * The downloader screen. Stateless: it shows [state] and reports user actions
 * through the callbacks. The caller owns the state and the folder chooser.
 * While a download runs, a Cancel button reports [onCancel]. The legal note
 * is always shown.
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
        Text(stringResource(Res.string.download_title), style = MaterialTheme.typography.h5)
        Text(stringResource(Res.string.legal_note), style = MaterialTheme.typography.body2)

        SetupWarnings(state.setupWarnings)

        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(Res.string.web_address)) },
            placeholder = { Text("https://") },
            singleLine = true,
            enabled = idle,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(Res.string.save_as), style = MaterialTheme.typography.subtitle1)
        ChoiceGroup(
            options = DownloadQuality.entries,
            selected = state.quality,
            enabled = idle,
            label = { qualityLabel(it) },
            onSelected = onQualitySelected,
            hint = { qualityHint(it) },
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onChooseFolder, enabled = idle) { Text(stringResource(Res.string.change_folder)) }
            Text(
                text = state.outputDirectory.ifBlank { stringResource(Res.string.no_folder_chosen) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onDownload, enabled = state.canDownload) { Text(stringResource(Res.string.download_button)) }
            if (state.status is DownloadStatus.Downloading) {
                OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            }
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
            Text(progressText(status.progress, progressWords()))
        }
        is DownloadStatus.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.saved_as, fileNameOf(status.outputPath)))
            SelectionContainer { Text(status.outputPath, style = MaterialTheme.typography.body2) }
        }
        is DownloadStatus.Failed -> SelectionContainer {
            Text(failureText(status.problem, status.leftover), color = MaterialTheme.colors.error)
        }
        DownloadStatus.Cancelling -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(stringResource(Res.string.cancelling))
        }
        is DownloadStatus.Cancelled -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.download_cancelled))
            status.leftoverFolder?.let { folder ->
                SelectionContainer {
                    Text(leftoverText(Leftover.TemporaryFolder(folder, detail = null)), color = MaterialTheme.colors.error)
                }
            }
        }
    }
}

@Composable
private fun UpdateBlock(update: UpdateStatus, enabled: Boolean, onUpdate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.update_explainer), style = MaterialTheme.typography.body2)
        OutlinedButton(onClick = onUpdate, enabled = enabled) { Text(stringResource(Res.string.update_button)) }
        when (update) {
            UpdateStatus.Idle -> Unit
            UpdateStatus.Updating -> Text(stringResource(Res.string.updating))
            is UpdateStatus.Done -> SelectionContainer { Text(stringResource(Res.string.update_done, update.toolReport)) }
            is UpdateStatus.Unclear -> SelectionContainer {
                val lastLine = update.lastLine?.let { stringResource(Res.string.update_unclear_last_line, it) }
                Text(listOfNotNull(stringResource(Res.string.update_unclear), lastLine).joinToString("\n"))
            }
            is UpdateStatus.Failed -> SelectionContainer {
                Text(problemText(update.problem), color = MaterialTheme.colors.error)
            }
        }
    }
}
