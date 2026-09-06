package com.mh.videodownloader

import android.content.Context
import androidx.room.Room
import com.mh.videodownloader.data.DownloadQueue
import com.mh.videodownloader.data.MediaRepository
import com.mh.videodownloader.data.MediaStoreSaver
import com.mh.videodownloader.data.db.AppDatabase
import com.mh.videodownloader.data.engine.YtDlpEngine
import com.mh.videodownloader.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container. The graph is six objects deep — a DI framework would cost
 * more build time and indirection than it saves here.
 */
class AppContainer(private val context: Context) {

    val settings = SettingsRepository(context)

    val cookiesFile: File get() = File(context.filesDir, "cookies.txt")

    val engine = YtDlpEngine(context)

    val downloadQueue = DownloadQueue()

    val mediaStoreSaver = MediaStoreSaver(context)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "downloads.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val historyDao by lazy { database.historyDao() }

    val mediaRepository = MediaRepository(engine) { cookiesFile.takeIf { it.exists() } }

    /** Where yt-dlp writes before we hand the finished file to MediaStore. */
    fun tempDirFor(taskId: String): File = File(File(context.cacheDir, "downloads"), taskId)

    /**
     * The single feature that decides how long this app keeps working: yt-dlp ships fixes within
     * days of a site change, and updating the binary needs no new APK.
     */
    suspend fun autoUpdateEngineIfDue(): Boolean {
        val current = settings.settings.first()
        if (!current.autoUpdateEngine) return false
        val age = System.currentTimeMillis() - current.lastEngineUpdate
        if (age < UPDATE_INTERVAL_MS) return false
        engine.ensureInitialized().getOrNull() ?: return false
        val result = engine.update()
        if (result !is YtDlpEngine.UpdateResult.Failed) {
            settings.setLastEngineUpdate(System.currentTimeMillis())
        }
        return result is YtDlpEngine.UpdateResult.Updated
    }

    private companion object {
        val UPDATE_INTERVAL_MS = TimeUnit.DAYS.toMillis(7)
    }
}
