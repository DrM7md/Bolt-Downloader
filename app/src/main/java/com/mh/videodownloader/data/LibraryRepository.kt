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

    suspend fun move(entryId: Long, folderId: Long?) = history.moveToFolder(entryId, folderId)

    /** The folder a playlist's downloads should land in, created the first time it is needed. */
    suspend fun playlistFolderId(playlistTitle: String): Long =
        folders.findPlaylistFolder(playlistTitle)?.id
            ?: folders.insert(Folder(name = playlistTitle, isPlaylist = true, createdAt = now()))

    sealed interface DeleteOutcome {
        data object Done : DeleteOutcome

        /**
         * Android will not let the app delete a file it no longer owns (after a reinstall, say)
         * without asking the user. The UI launches [request]; [finish] then drops the row.
         */
        data class NeedsConsent(val request: IntentSender) : DeleteOutcome
    }

    suspend fun delete(entry: HistoryEntry, deleteFile: Boolean): DeleteOutcome {
        if (deleteFile) {
            val consent = withContext(Dispatchers.IO) { deleteFileOrAskConsent(entry) }
            if (consent != null) return DeleteOutcome.NeedsConsent(consent)
        }
        history.deleteById(entry.id)
        return DeleteOutcome.Done
    }

    /** Called once the user approved the system delete prompt. */
    suspend fun finishDelete(entry: HistoryEntry) = history.deleteById(entry.id)

    private fun deleteFileOrAskConsent(entry: HistoryEntry): IntentSender? {
        val uri = entry.fileUri.toUri()
        if (uri.scheme == "file") {
            uri.toFile().delete()
            return null
        }
        return try {
            context.contentResolver.delete(uri, null, null)
            null
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                    e.userAction.actionIntent.intentSender

                else -> throw e
            }
        }
    }

    private fun now() = System.currentTimeMillis()
}
