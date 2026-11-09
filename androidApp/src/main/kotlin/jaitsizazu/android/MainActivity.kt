package jaitsizazu.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import jaitsizazu.ui.AppLanguage
import jaitsizazu.ui.AppTabs
import jaitsizazu.ui.ConverterScreen
import jaitsizazu.ui.DownloaderScreen
import java.util.Locale

/**
 * The only screen: the shared "Convert" and "Download" tabs, as on desktop.
 * The state holders live in [AppViewModel], so they outlive a rotation.
 *
 * Every text is in [AppLanguage.DEFAULT], Basque, whatever the language of
 * the phone: the activity's configuration carries that locale, and Compose
 * resources read it from there.
 */
class MainActivity : ComponentActivity() {

    private val app: AppViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val configuration = Configuration(newBase.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(AppLanguage.DEFAULT.tag))
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.safeDrawingPadding()) { App(app) }
                }
            }
        }
    }

    @Composable
    private fun App(app: AppViewModel) {
        val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(app::pickFileToConvert)
        }
        val withStorage = rememberStorageAccess()

        AppTabs(
            convert = {
                val state by app.converter.state.collectAsState()
                ConverterScreen(
                    state = state,
                    onChooseFile = { pickFile.launch(arrayOf("audio/*", "video/*")) },
                    onFormatSelected = app.converter::selectFormat,
                    onConvert = { withStorage(app.converter::convert) },
                    onCancel = app.converter::cancel,
                )
            },
            download = {
                val state by app.downloader.state.collectAsState()
                DownloaderScreen(
                    state = state,
                    onUrlChange = app.downloader::setUrl,
                    onQualitySelected = app.downloader::selectQuality,
                    // The folder is fixed: Download/Jaitsizazu.
                    onChooseFolder = null,
                    onDownload = { withStorage(app.downloader::download) },
                    onCancel = app.downloader::cancel,
                    onUpdate = app.downloader::updateDownloader,
                )
            },
        )
    }

    /**
     * Runs an action that saves into Download/Jaitsizazu. Android 8 and 9 need
     * the storage permission for it: there the action waits for the answer, and
     * runs either way. Without the permission, saving fails and says so.
     */
    @Composable
    private fun rememberStorageAccess(): (() -> Unit) -> Unit {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return { it() }
        // The action that waits for the answer. Kept across recompositions, not across a rotation.
        val pending = remember { arrayOfNulls<() -> Unit>(1) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            pending[0]?.invoke()
            pending[0] = null
        }
        return { action ->
            val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
                action()
            } else {
                pending[0] = action
                ask.launch(permission)
            }
        }
    }
}
