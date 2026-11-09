package jaitsizazu.engine.android

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PickedFoldersTest {

    private val first = Paths.get("/cache/jobs/session/input-1")
    private val second = Paths.get("/cache/jobs/session/input-2")
    private val third = Paths.get("/cache/jobs/session/input-3")

    @Test
    fun keepsTheFirstPickedFolder() {
        assertNull(PickedFolders().picked(first, selected = true))
    }

    @Test
    fun givesUpThePreviousFolderWhenAnotherFileIsSelected() {
        val folders = PickedFolders()
        folders.picked(first, selected = true)

        assertEquals(first, folders.picked(second, selected = true))
        assertEquals(second, folders.picked(third, selected = true))
    }

    @Test
    fun givesUpANewFolderWhoseFileWasNotSelectedAndKeepsTheSelectedOne() {
        // A file picked while a conversion runs is not selected: the running conversion keeps its folder.
        val folders = PickedFolders()
        folders.picked(first, selected = true)

        assertEquals(second, folders.picked(second, selected = false))
        assertEquals(first, folders.picked(third, selected = true))
    }
}
