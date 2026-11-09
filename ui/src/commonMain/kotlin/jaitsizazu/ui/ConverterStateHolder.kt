package jaitsizazu.ui

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.ConvertMedia
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the converter screen state and runs conversions in [scope].
 * While a conversion runs, every other action is ignored.
 */
class ConverterStateHolder(
    private val convertMedia: ConvertMedia,
    private val scope: CoroutineScope,
    setupProblem: String? = null,
) {
    private val mutableState = MutableStateFlow(ConverterState(setupProblem = setupProblem))
    val state: StateFlow<ConverterState> = mutableState.asStateFlow()

    private val isConverting get() = mutableState.value.status == ConversionStatus.Converting

    fun selectFile(path: String) {
        if (isConverting) return
        mutableState.update { it.copy(selectedFile = path, status = ConversionStatus.Idle) }
    }

    fun selectFormat(format: AudioFormat) {
        if (isConverting) return
        mutableState.update { it.copy(format = format, status = ConversionStatus.Idle) }
    }

    fun convert() {
        val current = mutableState.value
        val file = current.selectedFile
        if (!current.canConvert || file == null) return

        mutableState.update { it.copy(status = ConversionStatus.Converting) }
        scope.launch {
            val status = try {
                when (val result = convertMedia(ConversionRequest(file, current.format))) {
                    is MediaResult.Success -> ConversionStatus.Done(result.outputPath)
                    is MediaResult.Failure -> ConversionStatus.Failed(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConversionStatus.Failed("Unexpected error: ${e.message ?: e::class.simpleName}")
            }
            mutableState.update { it.copy(status = status) }
        }
    }
}
