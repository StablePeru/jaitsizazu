package jaitsizazu.ui

import jaitsizazu.ui.resources.Res
import jaitsizazu.ui.resources.progress_percent
import jaitsizazu.ui.resources.progress_time_left
import jaitsizazu.ui.resources.tab_download
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two string files: Basque in `values/` (the default) and English in
 * `values-en/`. Reads the files of this module, so it runs from the module folder.
 */
class StringResourcesTest {

    private val defaultLocale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun file(folder: String) = File("src/commonMain/composeResources/$folder/strings.xml")

    /** Every key of [folder] with the placeholders (`%1$s`, ...) its text uses. */
    private fun strings(folder: String): Map<String, Set<String>> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file(folder))
        val nodes = document.getElementsByTagName("string")
        val entries = (0 until nodes.length).map { nodes.item(it) as Element }
        val names = entries.map { it.getAttribute("name") }
        assertEquals(names.distinct(), names, "a key appears twice in $folder")
        return entries.associate { entry ->
            entry.getAttribute("name") to PLACEHOLDER.findAll(entry.textContent).map { it.value }.toSet()
        }
    }

    @Test
    fun basqueAndEnglishHaveTheSameKeys() {
        val basque = strings("values")
        val english = strings("values-en")

        assertEquals(emptySet(), english.keys - basque.keys, "missing in Basque")
        assertEquals(emptySet(), basque.keys - english.keys, "missing in English")
    }

    @Test
    fun everyTranslationFillsInTheSamePlaceholders() {
        val basque = strings("values")
        val english = strings("values-en")

        for ((key, placeholders) in english) {
            assertEquals(placeholders, basque[key], "placeholders of $key")
        }
    }

    @Test
    fun theBasqueTextsAreMarkedAsProposals() {
        assertTrue("Propuestas; las revisa el autor." in file("values").readText())
    }

    @Test
    fun basqueIsTheDefaultWhateverTheSystemLanguage() = runTest {
        for (locale in listOf(Locale.forLanguageTag(AppLanguage.BASQUE.tag), Locale.FRENCH, Locale.ROOT)) {
            Locale.setDefault(locale)
            assertEquals("Deskargatu", getString(Res.string.tab_download), "$locale")
        }
    }

    @Test
    fun englishCanBeChosen() = runTest {
        Locale.setDefault(Locale.forLanguageTag(AppLanguage.ENGLISH.tag))

        assertEquals("Download", getString(Res.string.tab_download))
    }

    @Test
    fun theProgressTemplatesWorkAsTheScreenFillsThemIn() = runTest {
        Locale.setDefault(Locale.forLanguageTag(AppLanguage.BASQUE.tag))
        assertEquals("%42", fillTemplate(getString(Res.string.progress_percent), "42"))
        assertEquals("1:05 falta", fillTemplate(getString(Res.string.progress_time_left), "1:05"))

        Locale.setDefault(Locale.ENGLISH)
        assertEquals("42%", fillTemplate(getString(Res.string.progress_percent), "42"))
        assertEquals("1:05 left", fillTemplate(getString(Res.string.progress_time_left), "1:05"))
    }

    private companion object {
        val PLACEHOLDER = Regex("""%\d+\$[sd]""")
    }
}
