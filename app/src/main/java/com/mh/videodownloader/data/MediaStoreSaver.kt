package com.mh.videodownloader.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.mh.videodownloader.util.sanitizeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moves a finished download out of our private cache and into the public
 * `Download/VideoDownloader` folder so the Files app and the gallery can see it.
 *
 * yt-dlp and ffmpeg need a real filesystem path to write to, while Android 10+ exposes public
 * storage only through content URIs — hence download-then-move rather than download-in-place.
 */
class MediaStoreSaver(private val context: Context) {

    data class Saved(val uri: Uri, val displayName: String, val sizeBytes: Long)

    suspend fun saveToDownloads(source: File, mimeType: String): Saved =
        withContext(Dispatchers.IO) {
            val displayName = sanitizeFileName(source.name)
            val size = source.length()
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                insertViaMediaStore(source, displayName, mimeType)
            } else {
                copyToLegacyPublicDir(source, displayName)
            }
            source.delete()
            source.parentFile?.takeIf { it.name != "downloads" }?.delete()
            Saved(uri, displayName, size)
        }

    private fun insertViaMediaStore(source: File, displayName: String, mimeType: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused to create an entry for $displayName")

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
        } ?: error("Could not open $uri for writing")

        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** Android 8–9 still has real paths in public storage; WRITE_EXTERNAL_STORAGE covers us there. */
    private fun copyToLegacyPublicDir(source: File, displayName: String): Uri {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            FOLDER
        ).apply { mkdirs() }
        var target = File(dir, displayName)
        var counter = 1
        while (target.exists()) {
            val dot = displayName.lastIndexOf('.')
            val base = if (dot > 0) displayName.substring(0, dot) else displayName
            val ext = if (dot > 0) displayName.substring(dot) else ""
            target = File(dir, "$base ($counter)$ext")
            counter++
        }
        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE * 8) }
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return target.toUri()
    }

    companion object {
        const val FOLDER = "VideoDownloader"
    }
}
