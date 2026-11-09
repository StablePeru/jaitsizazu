package jaitsizazu.android

import jaitsizazu.core.Problem
import jaitsizazu.engine.android.PUBLIC_FOLDER
import kotlin.test.Test
import kotlin.test.assertEquals

class PendingSaveTest {

    private val log = mutableListOf<String>()
    private val pending = PendingSave()

    private fun waitForAnswer(name: String) =
        pending.waitFor(action = { log += "run $name" }, refuse = { log += "refuse $name: $it" })

    @Test
    fun runsTheActionWhenThePermissionIsGranted() {
        waitForAnswer("download")

        pending.answer(granted = true)

        assertEquals(listOf("run download"), log)
    }

    @Test
    fun showsWhyInsteadOfRunningWhenThePermissionIsDenied() {
        waitForAnswer("download")

        pending.answer(granted = false)

        assertEquals(listOf("refuse download: ${Problem.SavePermissionDenied(PUBLIC_FOLDER)}"), log)
    }

    @Test
    fun answersEachActionOnceAndOnlyTheLastOne() {
        waitForAnswer("convert")
        waitForAnswer("download")

        pending.answer(granted = true)
        pending.answer(granted = true)

        assertEquals(listOf("run download"), log)
    }
}
