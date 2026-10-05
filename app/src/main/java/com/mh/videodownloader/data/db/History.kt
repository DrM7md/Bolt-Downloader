package com.mh.videodownloader.data.db

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

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
    /** Null means "not filed yet" — the library shows these under Unsorted. */
    val folderId: Long? = null,
)

/**
 * A library folder. Playlist downloads create one automatically ([isPlaylist]), so a 40-video
 * playlist lands as one tidy folder rather than 40 loose rows.
 */
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isPlaylist: Boolean,
    val createdAt: Long,
)

data class FolderSummary(
    @Embedded val folder: Folder,
    val itemCount: Int,
    val cover: String?,
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE folderId IS NULL ORDER BY createdAt DESC")
    fun observeUnsorted(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE folderId = :folderId ORDER BY createdAt DESC")
    fun observeInFolder(folderId: Long): Flow<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("UPDATE history SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long?)

    @Query("UPDATE history SET folderId = NULL WHERE folderId = :folderId")
    suspend fun unfileAll(folderId: Long)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Dao
interface FolderDao {
    @Query(
        """
        SELECT f.*,
               (SELECT COUNT(*) FROM history h WHERE h.folderId = f.id) AS itemCount,
               (SELECT h.thumbnail FROM history h WHERE h.folderId = f.id
                 ORDER BY h.createdAt DESC LIMIT 1) AS cover
        FROM folders f
        ORDER BY f.createdAt DESC
        """
    )
    fun observeSummaries(): Flow<List<FolderSummary>>

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observe(id: Long): Flow<Folder?>

    @Query("SELECT * FROM folders WHERE isPlaylist = 1 AND name = :name LIMIT 1")
    suspend fun findPlaylistFolder(name: String): Folder?

    @Insert
    suspend fun insert(folder: Folder): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(
    entities = [HistoryEntry::class, Folder::class],
    version = 2,
    exportSchema = true,
    // v1 -> v2 adds the folders table and history.folderId; Room derives the SQL from the
    // exported schemas, so existing downloads survive the upgrade untouched.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun folderDao(): FolderDao
}
