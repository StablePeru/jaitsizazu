package jaitsizazu.core

/**
 * Use case: convert a local file into another audio format, next to the original.
 *
 * It never overwrites a file: when the natural output name is taken, it picks
 * a free one such as `memo (1).wav`. [exists] tells whether a path is taken,
 * so this class stays free of I/O. The target format is always set: the
 * request type does not allow a missing one.
 */
class ConvertMedia(
    private val converter: MediaConverter,
    private val exists: (String) -> Boolean,
) {
    suspend operator fun invoke(request: ConversionRequest): MediaResult {
        val input = request.inputPath
        if (input.isBlank()) return MediaResult.Failure(Problem.NoFileChosen)
        if (fileNameOf(input).isBlank()) return MediaResult.Failure(Problem.NotAFile(input))
        if (!exists(input)) return MediaResult.Failure(Problem.FileNotFound(input))

        val output = firstFreePath(outputPath(input, request.target), exists)
            ?: return MediaResult.Failure(Problem.NoFreeName(input))
        return converter.convert(request, output)
    }
}
