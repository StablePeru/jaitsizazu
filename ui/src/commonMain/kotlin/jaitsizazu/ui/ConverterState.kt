package jaitsizazu.ui

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.Leftover
import jaitsizazu.core.Problem

/**
 * Everything the converter screen shows. Plain Kotlin, no Compose types and no
 * texts: the screen puts problems into words in the app's language.
 *
 * [setupWarnings] holds what a startup check found (for example, ffmpeg is
 * missing); the screen explains how to fix each. They do not block converting:
 * the engine checks again on every conversion, so the user can fix the
 * problem and try again.
 */
data class ConverterState(
    val selectedFile: String? = null,
    val format: AudioFormat = AudioFormat.WAV,
    val status: ConversionStatus = ConversionStatus.Idle,
    val setupWarnings: List<Problem> = emptyList(),
) {
    val canConvert: Boolean
        get() = selectedFile != null && status != ConversionStatus.Converting
}

sealed interface ConversionStatus {
    data object Idle : ConversionStatus
    data object Converting : ConversionStatus
    data class Done(val outputPath: String) : ConversionStatus
    /** The conversion failed because of [problem]; [leftover] is what it could not remove. */
    data class Failed(val problem: Problem, val leftover: Leftover? = null) : ConversionStatus
    /** The user stopped the conversion; the engine removes its partial output. */
    data object Cancelled : ConversionStatus
}
