package jaitsizazu.android

import jaitsizazu.core.Problem
import jaitsizazu.engine.android.PUBLIC_FOLDER

/**
 * An action that saves into [PUBLIC_FOLDER] and waits for the answer to the
 * storage permission request (Android 8 and 9 ask for it). Only the last
 * action waits: Android shows one request at a time.
 */
internal class PendingSave {
    private var action: (() -> Unit)? = null
    private var refuse: ((Problem) -> Unit)? = null

    /** Makes [action] wait for the answer; [refuse] shows why it did not run. */
    fun waitFor(action: () -> Unit, refuse: (Problem) -> Unit) {
        this.action = action
        this.refuse = refuse
    }

    /** Runs the waiting action when [granted]; otherwise shows that the permission was denied. Then forgets it. */
    fun answer(granted: Boolean) {
        val action = action
        val refuse = refuse
        this.action = null
        this.refuse = null
        if (granted) action?.invoke() else refuse?.invoke(Problem.SavePermissionDenied(PUBLIC_FOLDER))
    }
}
