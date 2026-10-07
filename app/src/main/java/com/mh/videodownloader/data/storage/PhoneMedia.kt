package com.mh.videodownloader.data.storage

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One video from anywhere on the phone. */
data class PhoneVideo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val addedAt: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val path: String,
)

/** What the "Info" sheet shows for a file. Missing values are simply not listed. */
data class MediaDetails(
    val name: String,
    val path: String?,
    val sizeBytes: Long,
    val date: Long,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val mimeType: String,
    /** Where it was downloaded from, when the download log still knows. */
    val sourceUrl: String? = null,
)

/** Read-only access to the phone's media through MediaStore. */
class PhoneMedia(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Videos only — "all videos" mode deliberately leaves out music and voice notes. */
    suspend fun allVideos(): List<PhoneVideo> = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // VOLUME_EXTERNAL covers SD cards too, not just internal storage.
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION") MediaStore.Video.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            pathColumn,
        )
        val out = mutableListOf<PhoneVideo>()
        resolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")
            ?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val rawPath = c.getString(8).orEmpty()
                    out += PhoneVideo(
                        uri = ContentUris.withAppendedId(collection, id),
                        name = name,
                        sizeBytes = c.getLong(2),
                        addedAt = c.getLong(3) * 1000L,
                        durationMs = c.getLong(4),
                        width = c.getInt(5),
                        height = c.getInt(6),
                        mimeType = c.getString(7) ?: "video/*",
                        path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) rawPath + name
                        else rawPath.removePrefix(Environment.getExternalStorageDirectory().absolutePath + "/"),
                    )
                }
            }
        out
    }

    /**
     * Finds a file again by its path after the library moved it (its old URI died with the move).
     * Only works for internal storage, where MediaStore paths and our stored paths line up.
     */
    suspend fun findByPath(path: String): Uri? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
        val slash = path.lastIndexOf('/')
        if (slash < 0) return@withContext null
        val folder = path.substring(0, slash + 1)
        val name = path.substring(slash + 1)
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        runCatching {
            resolver.query(
                files,
                arrayOf(MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.RELATIVE_PATH} = ? AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} = ?",
                arrayOf(folder, name),
                null,
            )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(files, c.getLong(0)) else null }
        }.getOrNull()
    }

    /** Path of a MediaStore item, for log rows written before 3.0 recorded paths. */
    suspend fun pathOf(uri: Uri): String? = withContext(Dispatchers.IO) {
        if (uri.scheme == "file") {
            return@withContext uri.path?.removePrefix(Environment.getExternalStorageDirectory().absolutePath + "/")
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || uri.authority != MediaStore.AUTHORITY) {
            return@withContext null
        }
        runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0).orEmpty() + c.getString(1).orEmpty() else null }
        }.getOrNull()
    }

    /** True when the URI still opens — moved or deleted files fail here. */
    suspend fun exists(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { resolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
    }

    /** Duration and resolution straight from the file, for the Info sheet. */
    suspend fun probe(uri: Uri): Triple<Long?, Int?, Int?> = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            Triple(duration, width, height)
        } catch (e: Exception) {
            Triple(null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Asks the user before deleting a video this app did not create (Android 11+). */
    fun deleteRequest(uris: List<Uri>) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, uris).intentSender
        } else {
            null
        }
}
