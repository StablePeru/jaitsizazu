package jaitsizazu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jaitsizazu.core.AudioFormat
import jaitsizazu.core.Problem
import jaitsizazu.core.fileNameOf
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.cancel
import jaitsizazu.ui.resources.choose_file
import jaitsizazu.ui.resources.conversion_cancelled
import jaitsizazu.ui.resources.convert_button
import jaitsizazu.ui.resources.convert_title
import jaitsizazu.ui.resources.convert_to
import jaitsizazu.ui.resources.converting
import jaitsizazu.ui.resources.no_file_chosen
import jaitsizazu.ui.resources.saved_as
import org.jetbrains.compose.resources.stringResource

/**
 * The converter screen. Stateless: it shows [state] and reports clicks
 * through the callbacks. The caller owns the state and the file chooser.
 * While a conversion runs, a Cancel button takes the Convert button's place
 * and reports [onCancel].
 */
@Composable
fun ConverterScreen(
    state: ConverterState,
    onChooseFile: () -> Unit,
    onFormatSelected: (AudioFormat) -> Unit,
    onConvert: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idle = state.status != ConversionStatus.Converting
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Text(stringResource(Res.string.convert_title), style = MaterialTheme.typography.h5)

        SetupWarnings(state.setupWarnings)

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val chosen = state.selectedFile?.let(::fileNameOf)
                Text(
                    text = chosen ?: stringResource(Res.string.no_file_chosen),
                    color = if (chosen != null) Palette.Text else Palette.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(stringResource(Res.string.choose_file), onChooseFile, enabled = idle)
            }
            Divider(color = Palette.Line)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SectionLabel(stringResource(Res.string.convert_to))
            ChoiceGroup(AudioFormat.entries, state.format, idle, { it.label }, onFormatSelected)
        }

        StatusLine(state.status)

        if (idle) {
            PrimaryButton(stringResource(Res.string.convert_button), onConvert, enabled = state.canConvert)
        } else {
            SecondaryButton(stringResource(Res.string.cancel), onCancel)
        }
    }
}

/** What a startup check found, each explained, as errors that do not block anything. */
@Composable
internal fun SetupWarnings(problems: List<Problem>) {
    if (problems.isEmpty()) return
    val text = problems.map { problemText(it) }.joinToString("\n\n")
    SelectionContainer { Text(text, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.error) }
}

@Composable
private fun StatusLine(status: ConversionStatus) {
    when (status) {
        ConversionStatus.Idle -> Unit
        ConversionStatus.Converting -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(Res.string.converting))
            ThinProgress(progress = null)
        }
        is ConversionStatus.Done -> DoneLine(stringResource(Res.string.saved_as, fileNameOf(status.outputPath)), status.outputPath)
        is ConversionStatus.Failed -> SelectionContainer {
            Text(failureText(status.problem, status.leftover), color = MaterialTheme.colors.error)
        }
        ConversionStatus.Cancelled -> Text(stringResource(Res.string.conversion_cancelled), color = Palette.Muted)
    }
}

/** Format names are the same in every language. */
private val AudioFormat.label: String
    get() = when (this) {
        AudioFormat.WAV -> "WAV"
        AudioFormat.MP3 -> "MP3"
        AudioFormat.FLAC -> "FLAC"
        AudioFormat.OGG_VORBIS -> "Ogg Vorbis"
    }
