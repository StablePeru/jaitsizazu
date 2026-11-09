package jaitsizazu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A group of radio buttons, one per entry of [options], each shown with its
 * [label] and, when [hint] gives one, a short note under it.
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
        for (option in options) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelected(option) },
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == selected, onClick = null, enabled = enabled)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(label(option))
                    hint(option)?.let { Text(it, style = MaterialTheme.typography.caption) }
                }
            }
        }
    }
}
