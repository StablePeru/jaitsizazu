package jaitsizazu.core

/**
 * Returns [inputName] with its extension replaced by the one of [target].
 *
 * Only the last extension is replaced: `"a.b.opus"` becomes `"a.b.wav"`.
 * A name with no extension gets one appended. A leading dot marks a hidden
 * file, not an extension, so `".hidden"` becomes `".hidden.wav"`.
 */
fun outputFileName(inputName: String, target: AudioFormat): String {
    require(inputName.isNotBlank()) { "File name must not be blank." }
    val lastDot = inputName.lastIndexOf('.')
    val baseName = if (lastDot > 0) inputName.substring(0, lastDot) else inputName
    return "$baseName.${target.extension}"
}
