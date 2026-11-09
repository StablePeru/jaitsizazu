package jaitsizazu.desktop

import jaitsizazu.ui.AppLanguage
import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.crash_details_not_saved
import jaitsizazu.ui.resources.crash_details_saved
import jaitsizazu.ui.resources.crash_intro
import org.jetbrains.compose.resources.getString
import java.nio.file.Path
import java.util.Locale

/**
 * Makes [language] the language of every text the app shows. On desktop,
 * Compose resources pick their texts by the JVM default locale, so this sets
 * it, before the first window and the first resource are read. The texts of
 * the system's own dialogs (such as the buttons of the folder chooser) come
 * from Java and stay in English when Java has no translation.
 */
fun useAppLanguage(language: AppLanguage) {
    Locale.setDefault(Locale.forLanguageTag(language.tag))
}

/** The crash dialog's texts in the app's language, naming [logFile]. */
suspend fun loadCrashTexts(logFile: Path): CrashTexts = CrashTexts(
    intro = getString(Res.string.crash_intro),
    detailsSaved = getString(Res.string.crash_details_saved, logFile.toString()),
    detailsNotSaved = getString(Res.string.crash_details_not_saved, logFile.toString()),
)
