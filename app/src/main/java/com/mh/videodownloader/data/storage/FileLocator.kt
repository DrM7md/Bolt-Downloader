package com.mh.videodownloader.data.storage

import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.mh.videodownloader.data.db.HistoryDao
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Keeps the download log and the files on disk in step.
 *
 * The library really moves and renames files, and every move gives a file a new URI. The log
 * stores each file's path as well, so it can find a file again — and it is told about moves the
 * library makes, so the common case never has to search.
 */
class FileLocator(
    private val store: FolderStore,
    private val phoneMedia: PhoneMedia,
    private val settings: SettingsRepository,
    private val historyDao: HistoryDao,
) {

    /** A URI that opens right now, or null if the file is gone. */
    suspend fun resolve(entry: HistoryEntry): Uri? {
        val stored = entry.fileUri.toUri()
        if (phoneMedia.exists(stored)) return stored
        val path = entry.filePath ?: return null

        // Under the granted save folder, a path maps straight back to a document id.
        settings.settings.first().saveFolder?.toUri()?.let { tree ->
            val candidate = store.uriOf(tree, "primary:$path")
            if (phoneMedia.exists(candidate)) {
                historyDao.relocate(entry.id, candidate.toString(), path)
                return candidate
            }
        }
        phoneMedia.findByPath(path)?.takeIf { phoneMedia.exists(it) }?.let {
            historyDao.relocate(entry.id, it.toString(), path)
            return it
        }
        return null
    }

    /** A file moved or was renamed by the library. */
    suspend fun fileMoved(oldDocId: String, newUri: Uri) {
        val newId = runCatching { DocumentsContract.getDocumentId(newUri) }.getOrNull() ?: return
        historyDao.relocateByPath(storagePath(oldDocId), storagePath(newId), newUri.toString())
    }

    /** A folder moved or was renamed: logged files inside it follow by path. */
    suspend fun folderMoved(oldDocId: String, newUri: Uri) {
        val newId = runCatching { DocumentsContract.getDocumentId(newUri) }.getOrNull() ?: return
        historyDao.relocateFolder(storagePath(oldDocId), storagePath(newId))
    }

    suspend fun sourceUrlFor(path: String): String? = historyDao.findByPath(path)?.sourceUrl
}
