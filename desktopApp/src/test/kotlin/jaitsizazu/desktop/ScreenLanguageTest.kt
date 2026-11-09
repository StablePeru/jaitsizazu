package jaitsizazu.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import jaitsizazu.core.Problem
import jaitsizazu.core.Tool
import jaitsizazu.ui.AppLanguage
import jaitsizazu.ui.ConverterScreen
import jaitsizazu.ui.ConverterState
import jaitsizazu.ui.DownloadStatus
import jaitsizazu.ui.DownloaderScreen
import jaitsizazu.ui.DownloaderState
import jaitsizazu.ui.UpdateStatus
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The screens in the language the app chose, whatever the language of the machine that runs the tests. */
@OptIn(ExperimentalTestApi::class)
class ScreenLanguageTest {

    private val defaultLocale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val failed = DownloaderState(
        url = "https://example.org/clip",
        outputDirectory = "/home/me/Downloads",
        status = DownloadStatus.Failed(Problem.DownloadFailed(1, "ERROR: Unsupported URL")),
    )

    @Test
    fun theDownloadScreenIsInBasqueByDefault() = runComposeUiTest {
        Locale.setDefault(Locale.ENGLISH) // The machine's language does not matter.
        useAppLanguage(AppLanguage.DEFAULT)
        setContent { MaterialTheme { DownloaderScreen(failed, {}, {}, {}, {}, {}, {}) } }

        onNodeWithText("Deskargatu web-helbide batetik").assertExists()
        onNodeWithText("Audioa (WAV, galera gehiagorik gabe, editatzeko)").assertExists()
        onNodeWithText("Bideoa (kalitate onena, 4K arte)").assertExists()
        // The translated sentence frames yt-dlp's own output, which stays as it is.
        onNodeWithText("Deskargak huts egin du (yt-dlp irteera-kodea: 1).\nERROR: Unsupported URL").assertExists()
        onNodeWithText("Download from a web address").assertDoesNotExist()
    }

    @Test
    fun theDownloadScreenIsInEnglishWhenEnglishIsChosen() = runComposeUiTest {
        useAppLanguage(AppLanguage.ENGLISH)
        setContent { MaterialTheme { DownloaderScreen(failed, {}, {}, {}, {}, {}, {}) } }

        onNodeWithText("Download from a web address").assertExists()
        onNodeWithText("Audio (original, as the site serves it)").assertExists()
        onNodeWithText("The download failed (yt-dlp exit code 1).\nERROR: Unsupported URL").assertExists()
    }

    @Test
    fun theDesktopDownloadScreenOffersToChangeTheFolder() = runComposeUiTest {
        useAppLanguage(AppLanguage.ENGLISH)
        setContent { MaterialTheme { DownloaderScreen(failed, {}, {}, onChooseFolder = {}, {}, {}, {}) } }

        onNodeWithText("Change folder").assertExists()
        onNodeWithText("/home/me/Downloads").assertExists()
    }

    @Test
    fun aFixedFolderHasNoButtonAndIsNamed() = runComposeUiTest {
        useAppLanguage(AppLanguage.ENGLISH)
        val android = failed.copy(outputDirectory = "Download/Jaitsizazu")
        setContent { MaterialTheme { DownloaderScreen(android, {}, {}, onChooseFolder = null, {}, {}, {}) } }

        onNodeWithText("Change folder").assertDoesNotExist()
        onNodeWithText("Files are saved in Download/Jaitsizazu.").assertExists()
    }

    @Test
    fun theAndroidEngineProblemsAreInBasque() = runComposeUiTest {
        useAppLanguage(AppLanguage.BASQUE)
        val notReady = Problem.ToolNotReadyInApp(Tool.FFMPEG, "/data/app/lib/libffmpeg.so", "failed to initialize")
        val state = failed.copy(
            setupWarnings = listOf(notReady),
            update = UpdateStatus.Failed(Problem.UpdateNotDownloaded("Unable to resolve host")),
        )
        setContent { MaterialTheme { DownloaderScreen(state, {}, {}, null, {}, {}, {}) } }

        onNodeWithText(
            "ffmpeg ez dago prest Jaitsizazuren barruan (/data/app/lib/libffmpeg.so).",
            substring = true,
        ).assertExists()
        onNodeWithText("failed to initialize", substring = true).assertExists()
        onNodeWithText(
            "Ezin izan da yt-dlp berria deskargatu. Egiaztatu Interneteko konexioa eta saiatu berriro.\nUnable to resolve host",
        ).assertExists()
    }

    @Test
    fun theConverterExplainsAMissingToolInBasque() = runComposeUiTest {
        useAppLanguage(AppLanguage.BASQUE)
        val noFfmpeg = Problem.ToolNotFound(Tool.FFMPEG, "JAITSIZAZU_FFMPEG", "bin/ffmpeg")
        setContent { MaterialTheme { ConverterScreen(ConverterState(setupWarnings = listOf(noFfmpeg)), {}, {}, {}, {}) } }

        onNodeWithText("Bihurtu audio-fitxategi bat").assertExists()
        onNodeWithText("Ez da aurkitu ffmpeg. Jaitsizazuk fitxategiak bihurtzeko eta deskargak amaitzeko behar du.", substring = true)
            .assertExists()
        onNodeWithText("JAITSIZAZU_FFMPEG aldagaian", substring = true).assertExists()
    }

    @Test
    fun theCrashDialogSpeaksTheAppLanguage() {
        val log = Path.of("/home/me/.local/state/jaitsizazu/crash.log")

        useAppLanguage(AppLanguage.BASQUE)
        val basque = runBlocking { loadCrashTexts(log) }
        useAppLanguage(AppLanguage.ENGLISH)
        val english = runBlocking { loadCrashTexts(log) }

        assertEquals("Jaitsizazuk ustekabeko errore bat izan du:", basque.intro)
        assertEquals("Xehetasunak hemen gorde dira: $log", basque.detailsSaved)
        assertEquals(CrashTexts.fallback(log), english, "the English texts and the built-in fallback say the same")
        assertTrue(log.toString() in basque.detailsNotSaved, basque.detailsNotSaved)
    }
}
