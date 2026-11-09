package jaitsizazu.ui

import jaitsizazu.core.AudioFormat

/**
 * Everything the converter screen shows. Plain Kotlin, no Compose types.
 *
 * [setupProblem] is set when the engine cannot work at all (for example,
 * ffmpeg is missing); it explains to the user how to fix it.
 */
data class ConverterState(
    val selectedFile: String? = null,
    val format: AudioFormat = AudioFormat.WAV,
    val status: ConversionStatus = ConversionStatus.Idle,
    val setupProblem: String? = null,
) {
    val canConvert: Boolean
        get() = selectedFile != null && status != ConversionStatus.Converting && setupProblem == null
}

sealed interface ConversionStatus {
    data object Idle : ConversionStatus
    data object Converting : ConversionStatus
    data class Done(val outputPath: String) : ConversionStatus
    data class Failed(val reason: String) : ConversionStatus
}
