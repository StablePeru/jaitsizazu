package jaitsizazu.engine.android

import java.nio.file.Path

/**
 * The job folders of the files picked for conversion: only the folder of the
 * selected file is kept. Not thread-safe: use it from one thread (the main
 * thread), so that two picks never interleave.
 */
class PickedFolders {
    private var selected: Path? = null

    /**
     * Records that a picked file was copied into [folder], and whether it was
     * [selected] for conversion (it is not while a conversion runs). Returns the
     * folder that holds no selected file any more, to remove once no conversion
     * uses it: the previously selected one when [selected], otherwise [folder].
     */
    fun picked(folder: Path, selected: Boolean): Path? {
        if (!selected) return folder
        val previous = this.selected
        this.selected = folder
        return previous
    }
}
