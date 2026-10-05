package com.mh.videodownloader.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.room.withTransaction
import com.mh.videodownloader.data.db.AppDatabase
import com.mh.videodownloader.data.db.Folder
import com.mh.videodownloader.data.db.HistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Folders, filing and deletion for the library screen. */
class LibraryRepository(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val history = database.historyDao()
    private val folders = database.folderDao()

    val folderSummaries = folders.observeSummaries()
    val allFolders = folders.observeAll()
    val unsorted = history.observeUnsorted()

    fun folder(id: Long) = folders.observe(id)
    fun itemsIn(folderId: Long) = history.observeInFolder(folderId)

    suspend fun createFolder(name: String): Long =
        folders.insert(Folder(name = name.trim(), isPlaylist = false, createdAt = now()))

    suspend fun renameFolder(id: Long, name: String) = folders.rename(id, name.trim())

    /** Deleting a folder never deletes videos: its items go back to Unsorted. */
    suspend fun deleteFolder(id: Long) = database.withTransaction {
        history.unfileAll(id)
        folders.delete(id)
    }

    suspend fun move(entryIds: Collection<Long>, folderId: Long?) = database.withTransaction {
        entryIds.forEach { history.moveToFolder(it, folderId) }
    }

    /** The folder a playlist's downloads should land in, created the first time it is needed. */
    suspend fun playlistFolderId(playlistTitle: String): Long =
        folders.findPlaylistFolder(playlistTitle)?.id
            ?: folders.insert(Folder(name = playlistTitle, isPlaylist = true, createdAt = now()))

    sealed interface DeleteOutcome {
        data object Done : DeleteOutcome

        /**
         * Android will not let the app delete files it no longer owns (after a reinstall, say)
         * without asking the user. The UI launches [request] — one prompt for all of [entries] —
         * and [finishDelete] then drops their rows.
         */
        data class NeedsConsent(
            val request: IntentSender,
            val entries: List<HistoryEntry>,
        ) : DeleteOutcome
    }

    suspend fun delete(entries: List<HistoryEntry>, deleteFile: Boolean): DeleteOutcome {
        if (!deleteFile) {
            database.withTransaction { entries.forEach { history.deleteById(it.id) } }
            return DeleteOutcome.Done
        }
        val blocked = mutableListOf<HistoryEntry>()
        var recoverable: IntentSender? = null
        withContext(Dispatchers.IO) {
            for (entry in entries) {
                when (val result = tryDeleteFile(entry)) {
                    FileDelete.Deleted -> history.deleteById(entry.id)
                    FileDelete.NeedsConsent -> blocked += entry
                    is FileDelete.NeedsRecoverableConsent -> {
                        blocked += entry
                        recoverable = recoverable ?: result.request
                    }
                }
            }
        }
        if (blocked.isEmpty()) return DeleteOutcome.Done
        val request = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(
                context.contentResolver,
                blocked.map { it.fileUri.toUri() },
            ).intentSender
        } else {
            // Android 10 can only ask about one file at a time.
            recoverable ?: return DeleteOutcome.Done
        }
        return DeleteOutcome.NeedsConsent(request, blocked)
    }

    /** Called once the user approved the system delete prompt. */
    suspend fun finishDelete(entries: List<HistoryEntry>) =
        database.withTransaction { entries.forEach { history.deleteById(it.id) } }

    private sealed interface FileDelete {
        data object Deleted : FileDelete
        data object NeedsConsent : FileDelete
        data class NeedsRecoverableConsent(val request: IntentSender) : FileDelete
    }

    private fun tryDeleteFile(entry: HistoryEntry): FileDelete {
        val uri = entry.fileUri.toUri()
        if (uri.scheme == "file") {
            uri.toFile().delete()
            return FileDelete.Deleted
        }
        return try {
            context.contentResolver.delete(uri, null, null)
            FileDelete.Deleted
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> FileDelete.NeedsConsent
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                    FileDelete.NeedsRecoverableConsent(e.userAction.actionIntent.intentSender)

                else -> throw e
            }
        }
    }

    private fun now() = System.currentTimeMillis()
}
