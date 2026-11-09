package jaitsizazu.engine.android

import android.annotation.TargetApi
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * [MediaPublisher] into the public `Download/Jaitsizazu` folder, with no broad
 * storage permission.
 *
 * - Android 10 and later (API level 29+): through MediaStore, which needs no
 *   permission for the app's own files and renames a file whose name is taken.
 * - Android 8 and 9 (API levels 26-28): straight into the Downloads folder,
 *   which needs `WRITE_EXTERNAL_STORAGE`. The app asks for it only on those
 *   versions, before it saves. A taken name gets a free variant.
 */
class DownloadsPublisher(private val context: Context) : MediaPublisher {

    override fun publish(file: Path): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishToMediaStore(file) else publishToFolder(file)

    @TargetApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(file: Path): String {
        val resolver = context.contentResolver
        val name = file.fileName.toString()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeOf(name))
            put(MediaStore.MediaColumns.RELATIVE_PATH, PUBLIC_FOLDER)
            // Hidden from other apps until it is complete.
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("MediaStore did not create $name")
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("MediaStore did not open $name")
            output.use { Files.copy(file, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        // MediaStore may have picked another name, if this one was taken.
        val savedName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
            ?: name
        return "$PUBLIC_FOLDER/$savedName"
    }

    private fun publishToFolder(file: Path): String {
        @Suppress("DEPRECATION") // Only used before Android 10, where it is the way.
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toPath()
        val folder = Files.createDirectories(downloads.resolve(PUBLIC_FOLDER.substringAfter('/')))
        val target = claimFreeFile(folder, file.fileName.toString())
        try {
            // Replaces only the empty file claimed just now.
            Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            runCatching { Files.deleteIfExists(target) }
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.toString()), null, null)
        return "$PUBLIC_FOLDER/${target.fileName}"
    }

    private fun mimeTypeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"
}
