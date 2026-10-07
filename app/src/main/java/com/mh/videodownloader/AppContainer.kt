package com.mh.videodownloader

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import com.mh.videodownloader.data.DownloadQueue
import com.mh.videodownloader.data.MediaRepository
import com.mh.videodownloader.data.db.AppDatabase
import com.mh.videodownloader.data.engine.YtDlpEngine
import com.mh.videodownloader.data.prefs.SettingsRepository
import com.mh.videodownloader.data.storage.DownloadSaver
import com.mh.videodownloader.data.storage.FileLocator
import com.mh.videodownloader.data.storage.FolderStore
import com.mh.videodownloader.data.storage.PhoneMedia
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container. The graph is a handful of objects deep — a DI framework
 * would cost more build time and indirection than it saves here.
 */
class AppContainer(private val context: Context) {

    val settings = SettingsRepository(context)

    val engine = YtDlpEngine(context)

    val downloadQueue = DownloadQueue()

    val folderStore = FolderStore(context)

    val phoneMedia = PhoneMedia(context)

    val downloadSaver = DownloadSaver(context, folderStore, settings)

    // No destructive fallback: the schema migrates (see AppDatabase), and silently wiping
    // someone's download log on a failed upgrade is worse than failing loudly.
    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "downloads.db").build()
    }

    val historyDao by lazy { database.historyDao() }

    val fileLocator by lazy { FileLocator(folderStore, phoneMedia, settings, historyDao) }

    val mediaRepository = MediaRepository(engine)

    /** Where yt-dlp writes before the finished file is copied into the save folder. */
    fun tempDirFor(taskId: String): File = File(File(context.cacheDir, "downloads"), taskId)

    /**
     * Keeps yt-dlp current without asking. It ships fixes within days of a site change, and
     * updating the binary needs no new APK — weekly is often enough to stay ahead of YouTube and
     * rare enough to cost nothing (one ~3 MB request). A failure is retried on the next launch.
     */
    suspend fun autoUpdateEngineIfDue(): Boolean {
        val age = System.currentTimeMillis() - settings.settings.first().lastEngineUpdate
        if (age < UPDATE_INTERVAL_MS) return false
        engine.ensureInitialized().getOrNull() ?: return false
        val result = engine.update()
        if (result !is YtDlpEngine.UpdateResult.Failed) {
            settings.setLastEngineUpdate(System.currentTimeMillis())
        }
        return result is YtDlpEngine.UpdateResult.Updated
    }

    /** One-off upkeep after an upgrade; every step is idempotent and cheap when there is nothing to do. */
    suspend fun housekeeping() {
        // 2.0 kept an imported cookies file; the feature is gone, so is the file.
        File(context.filesDir, "cookies.txt").delete()

        // Log rows written before 3.0 have no path. Fill it in so the log can still find a
        // file after the library moves it.
        historyDao.withoutPath().forEach { row ->
            phoneMedia.pathOf(row.fileUri.toUri())?.let { historyDao.relocate(row.id, row.fileUri, it) }
        }
    }

    private companion object {
        val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)
    }
}
