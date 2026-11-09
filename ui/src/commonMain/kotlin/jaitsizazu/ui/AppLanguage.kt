package jaitsizazu.ui

/**
 * The languages of the app. [tag] is the BCP 47 language tag that selects its
 * texts: Basque lives in the default `values/` strings, English in `values-en/`.
 *
 * The app starts in [DEFAULT], Basque, whatever the language of the system.
 * Each platform applies the choice before the first screen shows (on desktop,
 * as the JVM default locale, which Compose resources read). There is no
 * setting to change it yet.
 */
enum class AppLanguage(val tag: String) {
    BASQUE("eu"),
    ENGLISH("en"),
    ;

    companion object {
        val DEFAULT = BASQUE
    }
}
