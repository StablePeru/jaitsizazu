package jaitsizazu.ui

import jaitsizazu.core.AudioFormat
import jaitsizazu.core.ConversionRequest
import jaitsizazu.core.ConvertMedia
import jaitsizazu.core.MediaResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Holds the converter screen state and runs conversions in [scope].
 * While a conversion runs, every other action is ignored, except [cancel].
 *
 * State changes happen on [scope], which is meant to be single-threaded (the
 * UI thread). The conversion itself runs in [workContext], so its blocking
 * file checks stay off that thread: the desktop app passes `Dispatchers.IO`.
 */
class ConverterStateHolder(
    private val convertMedia: ConvertMedia,
    private val scope: CoroutineScope,
    private val workContext: CoroutineContext = EmptyCoroutineContext,
) {
    private val mutableState = MutableStateFlow(ConverterState())
    val state: StateFlow<ConverterState> = mutableState.asStateFlow()

    private var conversion: Job? = null

    private val isConverting get() = mutableState.value.status == ConversionStatus.Converting

    fun selectFile(path: String) {
        if (isConverting) return
        mutableState.update { it.copy(selectedFile = path, status = ConversionStatus.Idle) }
    }

    fun selectFormat(format: AudioFormat) {
        if (isConverting) return
        mutableState.update { it.copy(format = format, status = ConversionStatus.Idle) }
    }

    /** Shows [message] as a warning that does not block converting, or clears it when null. */
    fun showSetupWarning(message: String?) {
        mutableState.update { it.copy(setupWarning = message) }
    }

    fun convert() {
        val current = mutableState.value
        val file = current.selectedFile
        if (!current.canConvert || file == null) return

        mutableState.update { it.copy(status = ConversionStatus.Converting) }
        conversion = scope.launch {
            val status = try {
                when (val result = withContext(workContext) { convertMedia(ConversionRequest(file, current.format)) }) {
                    is MediaResult.Success -> ConversionStatus.Done(result.outputPath)
                    is MediaResult.Failure -> ConversionStatus.Failed(result.reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ConversionStatus.Failed("Unexpected error: ${e.message ?: e::class.simpleName}")
            }
            // A result that arrives after cancel() must not replace the Cancelled status.
            ensureActive()
            mutableState.update {
                // A conversion that worked proves ffmpeg is there after all.
                val warning = if (status is ConversionStatus.Done) null else it.setupWarning
                it.copy(status = status, setupWarning = warning)
            }
        }
    }

    /**
     * Stops the running conversion, if any. The status turns to Cancelled at once;
     * the engine stops its work and removes its partial output in the background.
     */
    fun cancel() {
        if (!isConverting) return
        conversion?.cancel()
        conversion = null
        mutableState.update { it.copy(status = ConversionStatus.Cancelled) }
    }
}
