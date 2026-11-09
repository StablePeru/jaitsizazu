package jaitsizazu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.tab_convert
import jaitsizazu.ui.resources.tab_download
import org.jetbrains.compose.resources.stringResource

/**
 * The "Convert" and "Download" tabs of every platform. Both screens keep their
 * state while hidden: the state holders own it.
 */
@Composable
fun AppTabs(convert: @Composable () -> Unit, download: @Composable () -> Unit) {
    var selected by remember { mutableStateOf(0) }
    Column {
        TabRow(selectedTabIndex = selected) {
            Tab(selected = selected == 0, onClick = { selected = 0 }, text = { Text(stringResource(Res.string.tab_convert)) })
            Tab(selected = selected == 1, onClick = { selected = 1 }, text = { Text(stringResource(Res.string.tab_download)) })
        }
        if (selected == 0) convert() else download()
    }
}
