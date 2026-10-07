package com.mh.videodownloader.data.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.mh.videodownloader.data.prefs.SettingsRepository
import com.mh.videodownloader.util.sanitizeFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Moves a finished download out of the private cache into the save folder, inside its source
 * folder ("YouTube", "TikTok", …) and, for a playlist, a folder named after the playlist.
 *
 * yt-dlp and ffmpeg need a real path to write to, while public storage is only reachable through
 * content URIs — hence download-then-copy. Two routes lead to the same place on disk:
 *  - with the save folder granted, through the Storage Access Framework (works for any folder
 *    the user picked, including an SD card);
 *  - before that grant, through MediaStore into Download/VideoDownloader, which needs no
 *    permission at all — so the very first download already lands in the right folder.
 */
class DownloadSaver(
    private val context: Context,
    private val store: FolderStore,
    private val settings: SettingsRepository,
) {

    data class Saved(val uri: Uri, val displayName: String, val sizeBytes: Long, val path: String)

    suspend fun save(source: File, mimeType: String, subfolders: List<String>): Saved =
        withContext(Dispatchers.IO) {
            val displayName = sanitizeFileName(source.name)
            val tree = settings.settings.first().saveFolder?.toUri()
                ?.takeIf { store.displayName(it) != null }

            val saved = when {
                tree != null -> saveToTree(tree, source, displayName, mimeType, subfolders)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    saveViaMediaStore(source, displayName, mimeType, subfolders)

                else -> saveToLegacyDir(source, displayName, subfolders)
            }
            source.delete()
            source.parentFile?.takeIf { it.name != "downloads" }?.delete()
            saved
        }

    private suspend fun saveToTree(
        tree: Uri,
        source: File,
        displayName: String,
        mimeType: String,
        subfolders: List<String>,
    ): Saved {
        val dir = store.ensurePath(tree, store.rootId(tree), subfolders)
        val item = store.write(tree, dir, displayName, mimeType, source)
        return Saved(item.uri, item.name, source.length(), item.path)
    }

    private fun saveViaMediaStore(
        source: File,
        displayName: String,
        mimeType: String,
        subfolders: List<String>,
    ): Saved {
        val resolver = context.contentResolver
        // MediaStore creates any missing folders in RELATIVE_PATH by itself.
        val relative = (listOf(Environment.DIRECTORY_DOWNLOADS, DEFAULT_FOLDER) +
            subfolders.map(::safeFolderName).filter { it.isNotEmpty() }).joinToString("/") + "/"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relative)
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

        // MediaStore may have renamed the file to avoid a clash; read back what it really is.
        val finalName = mediaStoreName(uri) ?: displayName
        return Saved(uri, finalName, source.length(), relative + finalName)
    }

    /** Android 8–9 still has real paths in public storage; WRITE_EXTERNAL_STORAGE covers us there. */
    private fun saveToLegacyDir(source: File, displayName: String, subfolders: List<String>): Saved {
        var dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), DEFAULT_FOLDER)
        subfolders.map(::safeFolderName).filter { it.isNotEmpty() }.forEach { dir = File(dir, it) }
        dir.mkdirs()
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
        val root = Environment.getExternalStorageDirectory().absolutePath + "/"
        return Saved(target.toUri(), target.name, target.length(), target.absolutePath.removePrefix(root))
    }

    private fun mediaStoreName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    companion object {
        const val DEFAULT_FOLDER = "VideoDownloader"
        val DEFAULT_PATH = "${Environment.DIRECTORY_DOWNLOADS}/$DEFAULT_FOLDER"
    }
}
