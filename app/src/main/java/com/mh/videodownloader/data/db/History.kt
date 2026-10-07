package com.mh.videodownloader.data.db

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import kotlinx.coroutines.flow.Flow

/**
 * One line of the download log. The log is a record of what was downloaded, nothing more:
 * clearing it never touches a file, and the library reads the disk, not this table.
 */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val sourceLabel: String,
    val sourceUrl: String,
    val fileUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val durationSec: Int,
    val isAudio: Boolean,
    val createdAt: Long,
    /**
     * Where the file lives, e.g. "Download/VideoDownloader/YouTube/clip.mp4". Lets the log find
     * the file again after it has been moved between folders, when [fileUri] no longer works.
     */
    val filePath: String? = null,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE filePath IS NULL")
    suspend fun withoutPath(): List<HistoryEntry>

    @Query("SELECT * FROM history WHERE filePath = :path LIMIT 1")
    suspend fun findByPath(path: String): HistoryEntry?

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("UPDATE history SET fileUri = :uri, filePath = :path WHERE id = :id")
    suspend fun relocate(id: Long, uri: String, path: String?)

    /** Keeps the log pointing at a file the library just moved or renamed. */
    @Query("UPDATE history SET fileUri = :newUri, filePath = :newPath WHERE filePath = :oldPath")
    suspend fun relocateByPath(oldPath: String, newPath: String, newUri: String)

    /** A whole folder moved or was renamed: every logged file inside it follows. */
    @Query(
        "UPDATE history SET filePath = :newPrefix || substr(filePath, length(:oldPrefix) + 1) " +
            "WHERE filePath LIKE :oldPrefix || '/%'"
    )
    suspend fun relocateFolder(oldPrefix: String, newPrefix: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

/** v2 kept "folders" as rows in this database; v3 makes them real folders on disk. */
@DeleteTable(tableName = "folders")
@DeleteColumn(tableName = "history", columnName = "folderId")
class DropVirtualFolders : AutoMigrationSpec

@Database(
    entities = [HistoryEntry::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = DropVirtualFolders::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
}
