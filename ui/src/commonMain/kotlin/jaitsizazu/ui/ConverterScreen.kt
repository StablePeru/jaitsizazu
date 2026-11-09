package jaitsizazu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
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
 * While a conversion runs, a Cancel button reports [onCancel].
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
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(Res.string.convert_title), style = MaterialTheme.typography.h5)

        SetupWarnings(state.setupWarnings)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onChooseFile, enabled = idle) { Text(stringResource(Res.string.choose_file)) }
            Text(
                text = state.selectedFile?.let(::fileNameOf) ?: stringResource(Res.string.no_file_chosen),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(stringResource(Res.string.convert_to), style = MaterialTheme.typography.subtitle1)
        ChoiceGroup(AudioFormat.entries, state.format, idle, { it.label }, onFormatSelected)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConvert, enabled = state.canConvert) { Text(stringResource(Res.string.convert_button)) }
            if (!idle) OutlinedButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
        }

        StatusLine(state.status)
    }
}

/** What a startup check found, each explained, as errors that do not block anything. */
@Composable
internal fun SetupWarnings(problems: List<Problem>) {
    if (problems.isEmpty()) return
    val text = problems.map { problemText(it) }.joinToString("\n\n")
    SelectionContainer { Text(text, color = MaterialTheme.colors.error) }
}

@Composable
private fun StatusLine(status: ConversionStatus) {
    when (status) {
        ConversionStatus.Idle -> Unit
        ConversionStatus.Converting -> Text(stringResource(Res.string.converting))
        is ConversionStatus.Done -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.saved_as, fileNameOf(status.outputPath)))
            SelectionContainer {
                Text(status.outputPath, style = MaterialTheme.typography.body2)
            }
        }
        is ConversionStatus.Failed -> SelectionContainer {
            Text(failureText(status.problem, status.leftover), color = MaterialTheme.colors.error)
        }
        ConversionStatus.Cancelled -> Text(stringResource(Res.string.conversion_cancelled))
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
