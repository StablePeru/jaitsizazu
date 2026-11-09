package jaitsizazu.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import jaitsizazu.ui.ConversionStatus
import jaitsizazu.ui.ConverterScreen
import jaitsizazu.ui.ConverterState
import org.jetbrains.skia.Bitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Smoke test: the screen composes and renders off-screen in every status, and draws something. */
class ConverterScreenRenderTest {

    private val width = 640
    private val height = 480

    /** Renders [state] and returns how many distinct colours a sample of its pixels holds. */
    private fun renderedColours(state: ConverterState): Int {
        val image = ImageComposeScene(width, height) {
            MaterialTheme {
                ConverterScreen(state, onChooseFile = {}, onFormatSelected = {}, onConvert = {}, onCancel = {})
            }
        }.use { it.render() }
        assertEquals(width to height, image.width to image.height)

        val bitmap = Bitmap.makeFromImage(image)
        val colours = mutableSetOf<Int>()
        for (y in 0 until height step 4) {
            for (x in 0 until width step 4) colours += bitmap.getColor(x, y)
        }
        return colours.size
    }

    @Test
    fun rendersEveryStatus() {
        val file = "/voice/memo.ogg"
        val states = listOf(
            ConverterState(),
            ConverterState(selectedFile = file, status = ConversionStatus.Converting),
            ConverterState(selectedFile = file, status = ConversionStatus.Done("/voice/memo.wav")),
            ConverterState(selectedFile = file, status = ConversionStatus.Failed("ffmpeg failed")),
            ConverterState(selectedFile = file, status = ConversionStatus.Cancelled),
            ConverterState(setupWarning = "ffmpeg was not found."),
        )
        for (state in states) {
            // A blank or uniform image means nothing was drawn.
            assertTrue(renderedColours(state) > 1, "nothing drawn for $state")
        }
    }
}
