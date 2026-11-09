package jaitsizazu.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

const val APP_NAME = "Jaitsizazu"

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = APP_NAME) {
        PlaceholderScreen()
    }
}

@Composable
fun PlaceholderScreen() {
    MaterialTheme {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(APP_NAME, style = MaterialTheme.typography.h4)
            Text("The converter is coming soon.")
        }
    }
}
