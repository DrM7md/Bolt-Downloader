package com.mh.videodownloader.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A file or folder inside the save folder. */
data class StoreItem(
    val docId: String,
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAt: Long,
) {
    val isFolder: Boolean get() = mimeType == Document.MIME_TYPE_DIR
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val path: String get() = storagePath(docId)
}

/** A folder anywhere under the save folder, for "move to…" lists. */
data class FolderRef(
    val docId: String,
    val name: String,
    /** Path below the save folder, e.g. "YouTube / My playlist"; empty for the root itself. */
    val trail: String,
    val depth: Int,
)

data class FolderSummary(val itemCount: Int, val folderCount: Int, val cover: VideoFrame?)

/**
 * Real folders on the device, read and changed through the Storage Access Framework.
 *
 * Everything here works on a tree the user granted once (the save folder). Children are read
 * with a single DocumentsContract query per folder rather than DocumentFile, which issues a query
 * per field per file — the difference between instant and seconds on a few hundred videos.
 */
class FolderStore(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    fun uriOf(tree: Uri, docId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)

    /** Null when the grant is gone — the folder was deleted or access revoked in system settings. */
    suspend fun displayName(tree: Uri, docId: String = rootId(tree)): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.query(uriOf(tree, docId), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
    }

    /** Folders first (by name), then videos and audio (newest first). Anything else is skipped. */
    suspend fun children(tree: Uri, docId: String): List<StoreItem> = withContext(Dispatchers.IO) {
        val items = query(tree, docId).filter { it.isFolder || it.isMedia() }
        items.filter { it.isFolder }.sortedBy { it.name.lowercase() } +
            items.filterNot { it.isFolder }.sortedByDescending { it.modifiedAt }
    }

    suspend fun summary(tree: Uri, folderId: String): FolderSummary = withContext(Dispatchers.IO) {
        val items = runCatching { query(tree, folderId) }.getOrDefault(emptyList())
        val media = items.filter { it.isMedia() }
        val folders = items.filter { it.isFolder }
        // A folder holding only subfolders (YouTube → playlists) borrows a cover from one level down.
        val cover = newestVideo(media)
            ?: folders.firstNotNullOfOrNull { sub ->
                newestVideo(runCatching { query(tree, sub.docId) }.getOrDefault(emptyList()))
            }
        FolderSummary(itemCount = media.size, folderCount = folders.size, cover = cover)
    }

    private fun newestVideo(items: List<StoreItem>): VideoFrame? =
        items.filter { it.mimeType.startsWith("video/") }.maxByOrNull { it.modifiedAt }
            ?.let { VideoFrame(it.uri, it.modifiedAt) }

    /** Every folder under the root, breadth first — the targets offered by "move to". */
    suspend fun allFolders(tree: Uri, maxDepth: Int = 6, limit: Int = 400): List<FolderRef> =
        withContext(Dispatchers.IO) {
            val root = rootId(tree)
            val out = mutableListOf(FolderRef(root, displayName(tree) ?: "", "", 0))
            val queue = ArrayDeque(listOf(out.first()))
            while (queue.isNotEmpty() && out.size < limit) {
                val parent = queue.removeFirst()
                if (parent.depth >= maxDepth) continue
                runCatching { query(tree, parent.docId) }.getOrDefault(emptyList())
                    .filter { it.isFolder }
                    .sortedBy { it.name.lowercase() }
                    .forEach { child ->
                        val ref = FolderRef(
                            docId = child.docId,
                            name = child.name,
                            trail = if (parent.trail.isEmpty()) child.name else parent.trail + " / " + child.name,
                            depth = parent.depth + 1,
                        )
                        out += ref
                        queue += ref
                    }
            }
            out
        }

    /** Walks [segments] below [fromId], creating any folder that does not exist yet. */
    suspend fun ensurePath(tree: Uri, fromId: String, segments: List<String>): String =
        withContext(Dispatchers.IO) {
            var current = fromId
            for (segment in segments.map(::safeFolderName).filter { it.isNotEmpty() }) {
                val existing = query(tree, current).firstOrNull { it.isFolder && it.name.equals(segment, ignoreCase = true) }
                current = existing?.docId ?: createDir(tree, current, segment)
            }
            current
        }

    suspend fun createFolder(tree: Uri, parentId: String, name: String): String? = withContext(Dispatchers.IO) {
        val clean = safeFolderName(name)
        if (clean.isEmpty()) return@withContext null
        runCatching { createDir(tree, parentId, clean) }.getOrNull()
    }

    /** Returns the renamed item's new URI, or null if the provider refused. */
    suspend fun rename(tree: Uri, docId: String, newName: String): Uri? = withContext(Dispatchers.IO) {
        runCatching { DocumentsContract.renameDocument(resolver, uriOf(tree, docId), newName) }.getOrNull()
    }

    /** Folders are deleted with everything inside them; the caller confirms that first. */
    suspend fun delete(tree: Uri, docId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { DocumentsContract.deleteDocument(resolver, uriOf(tree, docId)) }.getOrDefault(false)
    }

    /** A real move on disk — the file changes folder, it is not copied. Returns the new URI. */
    suspend fun move(tree: Uri, docId: String, fromParentId: String, toParentId: String): Uri? =
        withContext(Dispatchers.IO) {
            if (fromParentId == toParentId) return@withContext uriOf(tree, docId)
            runCatching {
                DocumentsContract.moveDocument(
                    resolver,
                    uriOf(tree, docId),
                    uriOf(tree, fromParentId),
                    uriOf(tree, toParentId),
                )
            }.getOrNull()
        }

    /** Copies a finished download into [dirId]; the provider resolves name clashes ("(1)"). */
    suspend fun write(tree: Uri, dirId: String, displayName: String, mimeType: String, source: File): StoreItem =
        withContext(Dispatchers.IO) {
            val created = DocumentsContract.createDocument(resolver, uriOf(tree, dirId), mimeType, displayName)
                ?: error("Could not create $displayName")
            resolver.openOutputStream(created)?.use { out ->
                source.inputStream().use { it.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
            } ?: error("Could not open $created for writing")
            val id = DocumentsContract.getDocumentId(created)
            StoreItem(
                docId = id,
                uri = created,
                name = queryName(created) ?: displayName,
                mimeType = mimeType,
                sizeBytes = source.length(),
                modifiedAt = System.currentTimeMillis(),
            )
        }

    private fun createDir(tree: Uri, parentId: String, name: String): String {
        val uri = DocumentsContract.createDocument(resolver, uriOf(tree, parentId), Document.MIME_TYPE_DIR, name)
            ?: error("Could not create folder $name")
        return DocumentsContract.getDocumentId(uri)
    }

    private fun queryName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    private fun query(tree: Uri, docId: String): List<StoreItem> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
        )
        val items = mutableListOf<StoreItem>()
        resolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                items += StoreItem(
                    docId = id,
                    uri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    name = c.getString(1) ?: id,
                    mimeType = c.getString(2) ?: "application/octet-stream",
                    sizeBytes = if (c.isNull(3)) 0L else c.getLong(3),
                    modifiedAt = if (c.isNull(4)) 0L else c.getLong(4),
                )
            }
        }
        return items
    }

    private fun StoreItem.isMedia() = mimeType.startsWith("video/") || mimeType.startsWith("audio/")

    companion object {
        /** The tree document we point the system picker at for the default save folder. */
        fun defaultFolderHint(): Uri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/VideoDownloader",
        )
    }
}

/**
 * "Download/VideoDownloader/YouTube/clip.mp4" from an ExternalStorageProvider document id
 * ("primary:Download/…"). Ids from other providers are opaque and come back unchanged.
 */
fun storagePath(docId: String): String = when {
    docId.startsWith("primary:") -> docId.removePrefix("primary:")
    else -> docId
}

/** Folder names come from video and playlist titles, so they need the same scrubbing. */
fun safeFolderName(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trimStart('.')
        .take(80)
        .trim()
