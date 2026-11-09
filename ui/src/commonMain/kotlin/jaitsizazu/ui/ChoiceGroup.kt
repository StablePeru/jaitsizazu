package jaitsizazu.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A list of choices, one row per entry of [options], with a hairline between
 * rows. Each row shows a dot, its [label] and, when [hint] gives one, a short
 * note under it. The selected row has a white dot and bolder text.
 */
@Composable
internal fun <T> ChoiceGroup(
    options: List<T>,
    selected: T,
    enabled: Boolean,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    hint: @Composable (T) -> String? = { null },
) {
    Column(Modifier.selectableGroup()) {
        options.forEachIndexed { index, option ->
            val isSelected = option == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    )
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Dot(isSelected, enabled)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        label(option),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (enabled || isSelected) Palette.Text else Palette.Muted,
                    )
                    hint(option)?.let { Text(it, style = MaterialTheme.typography.caption, color = Palette.Muted) }
                }
            }
            if (index < options.lastIndex) Hairline()
        }
    }
}

@Composable
private fun Dot(selected: Boolean, enabled: Boolean) {
    val modifier = Modifier.size(10.dp)
    if (selected) {
        Box(modifier.background(Palette.Text, CircleShape))
    } else {
        Box(modifier.border(1.5.dp, if (enabled) Palette.Faint else Palette.Line, CircleShape))
    }
}
