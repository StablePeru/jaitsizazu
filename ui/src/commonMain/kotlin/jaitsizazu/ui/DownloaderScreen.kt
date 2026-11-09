package jaitsizazu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
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
import jaitsizazu.ui.resources.fixed_folder
import jaitsizazu.ui.resources.legal_note
import jaitsizazu.ui.resources.no_folder_chosen
import jaitsizazu.ui.resources.preparing_engine
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
 * While a download runs, a Cancel button takes the Download button's place
 * and reports [onCancel]. The legal note is always shown.
 *
 * [onChooseFolder] null means the folder is fixed (on Android): there is no
 * button to change it, and the screen says where files go. While the engine
 * is still [preparing][DownloaderState.preparing], the screen says so.
 */
@Composable
fun DownloaderScreen(
    state: DownloaderState,
    onUrlChange: (String) -> Unit,
    onQualitySelected: (DownloadQuality) -> Unit,
    onChooseFolder: (() -> Unit)?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = !state.isBusy
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(stringResource(Res.string.download_title), style = MaterialTheme.typography.h5)

        SetupWarnings(state.setupWarnings)

        UnderlineField(
            label = stringResource(Res.string.web_address),
            value = state.url,
            onValueChange = onUrlChange,
            enabled = idle,
            placeholder = "https://",
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(stringResource(Res.string.save_as))
            ChoiceGroup(
                options = DownloadQuality.entries,
                selected = state.quality,
                enabled = idle,
                label = { qualityLabel(it) },
                onSelected = onQualitySelected,
                hint = { qualityHint(it) },
            )
        }

        if (onChooseFolder != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.outputDirectory.ifBlank { stringResource(Res.string.no_folder_chosen) },
                    style = MaterialTheme.typography.body2,
                    color = Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(stringResource(Res.string.change_folder), onChooseFolder, enabled = idle)
            }
        } else {
            Text(stringResource(Res.string.fixed_folder, state.outputDirectory), style = MaterialTheme.typography.body2, color = Palette.Muted)
        }

        if (state.preparing) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(Res.string.preparing_engine), style = MaterialTheme.typography.body2, color = Palette.Muted)
                ThinProgress(progress = null)
            }
        }

        DownloadStatusBlock(state.status)

        if (state.status is DownloadStatus.Downloading) {
            SecondaryButton(stringResource(Res.string.cancel), onCancel)
        } else {
            PrimaryButton(stringResource(Res.string.download_button), onDownload, enabled = state.canDownload)
        }

        Hairline()
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.legal_note), style = MaterialTheme.typography.caption, color = Palette.Muted)
            UpdateBlock(state.update, enabled = state.canUpdate, onUpdate = onUpdate)
        }
    }
}

@Composable
private fun DownloadStatusBlock(status: DownloadStatus) {
    when (status) {
        DownloadStatus.Idle -> Unit
        is DownloadStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val percent = (status.progress as? DownloadProgress.Downloading)?.percent
            Text(progressText(status.progress, progressWords()))
            ThinProgress(percent?.let { (it / 100).toFloat() })
        }
        is DownloadStatus.Done -> DoneLine(stringResource(Res.string.saved_as, fileNameOf(status.outputPath)), status.outputPath)
        is DownloadStatus.Failed -> SelectionContainer {
            Text(failureText(status.problem, status.leftover), color = MaterialTheme.colors.error)
        }
        DownloadStatus.Cancelling -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.cancelling))
            ThinProgress(progress = null)
        }
        is DownloadStatus.Cancelled -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.download_cancelled), color = Palette.Muted)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.update_explainer), style = MaterialTheme.typography.caption, color = Palette.Muted)
        QuietButton(stringResource(Res.string.update_button), onUpdate, enabled = enabled)
        val note = MaterialTheme.typography.body2
        when (update) {
            UpdateStatus.Idle -> Unit
            UpdateStatus.Updating -> Text(stringResource(Res.string.updating), style = note)
            is UpdateStatus.Done -> SelectionContainer { Text(stringResource(Res.string.update_done, update.toolReport), style = note) }
            is UpdateStatus.Unclear -> SelectionContainer {
                val lastLine = update.lastLine?.let { stringResource(Res.string.update_unclear_last_line, it) }
                Text(listOfNotNull(stringResource(Res.string.update_unclear), lastLine).joinToString("\n"), style = note)
            }
            is UpdateStatus.Failed -> SelectionContainer {
                Text(problemText(update.problem), style = note, color = MaterialTheme.colors.error)
            }
        }
    }
}
