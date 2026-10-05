package com.mh.videodownloader.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file or sub-folder inside a folder the user picked from the device. */
data class DeviceItem(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
) {
    val isFolder: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}

/**
 * Reads a user-picked device folder through the Storage Access Framework.
 *
 * Queries DocumentsContract directly rather than going through DocumentFile: one query returns
 * every child with its name, type, size and date, where DocumentFile issues a query per field
 * per file — the difference between instant and seconds on a folder of a few hundred videos.
 */
class DeviceFolderSource(private val context: Context) {

    fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    suspend fun displayName(tree: Uri): String? = withContext(Dispatchers.IO) {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, rootId(tree))
        runCatching {
            context.contentResolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /** Sub-folders first, then videos and audio, newest first. Anything else is skipped. */
    suspend fun list(tree: Uri, documentId: String): List<DeviceItem> = withContext(Dispatchers.IO) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        val items = mutableListOf<DeviceItem>()
        context.contentResolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val mime = c.getString(2) ?: continue
                val isMedia = mime == Document.MIME_TYPE_DIR ||
                    mime.startsWith("video/") || mime.startsWith("audio/")
                if (!isMedia) continue
                items += DeviceItem(
                    documentId = id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    name = c.getString(1) ?: id,
                    mimeType = mime,
                    sizeBytes = if (c.isNull(3)) 0L else c.getLong(3),
                    modifiedAt = if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
        }
        items.sortedWith(compareByDescending<DeviceItem> { it.isFolder }.thenByDescending { it.modifiedAt })
    }

    suspend fun delete(item: DeviceItem): Boolean = withContext(Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(context.contentResolver, item.uri) }
            .getOrDefault(false)
    }
}
