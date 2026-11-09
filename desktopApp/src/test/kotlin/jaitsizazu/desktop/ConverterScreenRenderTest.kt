package jaitsizazu.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import jaitsizazu.ui.ConversionStatus
import jaitsizazu.ui.ConverterScreen
import jaitsizazu.ui.ConverterState
import kotlin.test.Test

/** Smoke test: the screen composes and renders off-screen in every status. */
class ConverterScreenRenderTest {

    private fun render(state: ConverterState) {
        ImageComposeScene(width = 640, height = 480) {
            MaterialTheme {
                ConverterScreen(state, onChooseFile = {}, onFormatSelected = {}, onConvert = {})
            }
        }.use { it.render() }
    }

    @Test
    fun rendersEveryStatus() {
        val file = "/voice/memo.ogg"
        render(ConverterState())
        render(ConverterState(selectedFile = file, status = ConversionStatus.Converting))
        render(ConverterState(selectedFile = file, status = ConversionStatus.Done("/voice/memo.wav")))
        render(ConverterState(selectedFile = file, status = ConversionStatus.Failed("ffmpeg failed")))
        render(ConverterState(setupProblem = "ffmpeg was not found."))
    }
}
