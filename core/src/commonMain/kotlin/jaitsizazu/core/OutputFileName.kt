package jaitsizazu.core

/**
 * Returns the bare file name [inputName] with its extension replaced by the one of [target].
 *
 * [inputName] must be a file name, not a path: it must not contain `/` or `\`.
 * Use [outputPath] for paths.
 *
 * Only the last extension is replaced: `"a.b.opus"` becomes `"a.b.wav"`.
 * A name with no extension gets one appended. A leading dot marks a hidden
 * file, not an extension, so `".hidden"` becomes `".hidden.wav"`.
 */
fun outputFileName(inputName: String, target: AudioFormat): String {
    require(inputName.isNotBlank()) { "File name must not be blank." }
    require('/' !in inputName && '\\' !in inputName) { "Expected a file name, not a path: $inputName" }
    return "${baseName(inputName)}.${target.extension}"
}

/**
 * Returns the path of the converted file: [inputPath] in the same directory,
 * with the file name's extension replaced by the one of [target].
 *
 * Both `/` and `\` separate directories, so dots in a directory name are
 * never taken for an extension: `"v1.2/memo"` becomes `"v1.2/memo.wav"`.
 */
fun outputPath(inputPath: String, target: AudioFormat): String {
    val (directory, name) = splitPath(inputPath)
    return directory + outputFileName(name, target)
}

/** Returns the last segment of [path], after its last `/` or `\`. */
fun fileNameOf(path: String): String = splitPath(path).second

/**
 * Returns [path] if [exists] says it is free. Otherwise tries `"name (1).ext"`,
 * `"name (2).ext"` and so on, in the same directory, and returns the first free one.
 * Returns null if the first [MAX_FREE_NAME_COUNTER] counters are all taken.
 */
fun firstFreePath(path: String, exists: (String) -> Boolean): String? {
    if (!exists(path)) return path
    val (directory, name) = splitPath(path)
    val base = baseName(name)
    val extension = name.removePrefix(base)
    return (1..MAX_FREE_NAME_COUNTER)
        .asSequence()
        .map { counter -> "$directory$base ($counter)$extension" }
        .firstOrNull { !exists(it) }
}

/** Highest counter [firstFreePath] tries before giving up. */
const val MAX_FREE_NAME_COUNTER = 1000

/** Splits [path] after its last `/` or `\`: the directory part keeps its separator. */
private fun splitPath(path: String): Pair<String, String> {
    val cut = path.lastIndexOfAny(charArrayOf('/', '\\')) + 1
    return path.substring(0, cut) to path.substring(cut)
}

/** The file name without its last extension. A leading dot is not an extension. */
private fun baseName(fileName: String): String {
    val lastDot = fileName.lastIndexOf('.')
    return if (lastDot > 0) fileName.substring(0, lastDot) else fileName
}
