package jaitsizazu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.tab_convert
import jaitsizazu.ui.resources.tab_download
import org.jetbrains.compose.resources.stringResource

/**
 * The "Convert" and "Download" tabs of every platform: two words, the shown
 * one white and underlined. Both screens keep their state while hidden: the
 * state holders own it. On a wide window the content stays in a centred column.
 */
@Composable
fun AppTabs(convert: @Composable () -> Unit, download: @Composable () -> Unit) {
    var selected by remember { mutableStateOf(0) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Tab(stringResource(Res.string.tab_convert), selected == 0) { selected = 0 }
                Tab(stringResource(Res.string.tab_download), selected == 1) { selected = 1 }
            }
            if (selected == 0) convert() else download()
        }
    }
}

@Composable
private fun Tab(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (selected) {
                    val y = size.height - 1.dp.toPx()
                    drawLine(Palette.Text, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.h6, color = if (selected) Palette.Text else Palette.Dim)
    }
}
