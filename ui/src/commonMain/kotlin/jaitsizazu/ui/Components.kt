package jaitsizazu.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** The small label above a field or a group of choices, in capitals. */
@Composable
internal fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.overline, color = Palette.Muted)
}

/** The main action of a screen: a white pill across the whole width. */
@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.small,
        elevation = ButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = Palette.Text,
            contentColor = Palette.Background,
            disabledBackgroundColor = Palette.Hairline,
            disabledContentColor = Palette.Faint,
        ),
    ) { Text(text) }
}

/** The action that takes the main one's place while it runs, such as Cancel. */
@Composable
internal fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, Palette.Text),
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = Palette.Background, contentColor = Palette.Text),
    ) { Text(text) }
}

/** A minor action, shown as underlined text. */
@Composable
internal fun QuietButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 44.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = Palette.Text, disabledContentColor = Palette.Faint),
    ) {
        Text(text, style = MaterialTheme.typography.body2, textDecoration = TextDecoration.Underline)
    }
}

/** A one-line text field drawn as a line under the text, with [label] above it. */
@Composable
internal fun UnderlineField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    placeholder: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.body1.copy(color = if (enabled) Palette.Text else Palette.Muted),
            cursorBrush = SolidColor(Palette.Text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            decorationBox = { field ->
                Column {
                    Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text(placeholder, color = Palette.Faint)
                        field()
                    }
                    Divider(color = Palette.Line)
                }
            },
        )
    }
}

/** A thin white progress line; null [progress] means it is not known. */
@Composable
internal fun ThinProgress(progress: Float?) {
    val modifier = Modifier.fillMaxWidth().height(2.dp)
    if (progress != null) {
        LinearProgressIndicator(progress = progress, modifier = modifier, color = Palette.Text, backgroundColor = Palette.Hairline)
    } else {
        LinearProgressIndicator(modifier = modifier, color = Palette.Text, backgroundColor = Palette.Hairline)
    }
}

/** A finished job: a check mark, [title], and [detail] (a path) under it, selectable. */
@Composable
internal fun DoneLine(title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CheckMark(Modifier.padding(top = 2.dp).size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title)
            SelectionContainer { Text(detail, style = MaterialTheme.typography.body2, color = Palette.Muted) }
        }
    }
}

@Composable
private fun CheckMark(modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val path = Path().apply {
            moveTo(5f * unit, 12f * unit)
            lineTo(10f * unit, 17f * unit)
            lineTo(19f * unit, 7f * unit)
        }
        drawPath(path, Palette.Text, style = Stroke(width = 2f * unit, cap = StrokeCap.Round))
    }
}

/** A hairline between rows. */
@Composable
internal fun Hairline() {
    Divider(color = Palette.Hairline)
}
