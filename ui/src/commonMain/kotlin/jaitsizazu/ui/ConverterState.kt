package jaitsizazu.ui

import jaitsizazu.core.AudioFormat

/**
 * Everything the converter screen shows. Plain Kotlin, no Compose types.
 *
 * [setupWarning] is set when a startup check found a problem (for example,
 * ffmpeg is missing) and explains how to fix it. It does not block converting:
 * the engine checks again on every conversion, so the user can fix the
 * problem and try again.
 */
data class ConverterState(
    val selectedFile: String? = null,
    val format: AudioFormat = AudioFormat.WAV,
    val status: ConversionStatus = ConversionStatus.Idle,
    val setupWarning: String? = null,
) {
    val canConvert: Boolean
        get() = selectedFile != null && status != ConversionStatus.Converting
}

sealed interface ConversionStatus {
    data object Idle : ConversionStatus
    data object Converting : ConversionStatus
    data class Done(val outputPath: String) : ConversionStatus
    data class Failed(val reason: String) : ConversionStatus
    /** The user stopped the conversion; the engine removes its partial output. */
    data object Cancelled : ConversionStatus
}
