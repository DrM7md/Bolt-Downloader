package com.mh.videodownloader.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mh.videodownloader.App
import com.mh.videodownloader.AppContainer
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.domain.DownloadState
import com.mh.videodownloader.domain.DownloadTask
import com.mh.videodownloader.util.extractSpeed
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs the download queue, one job at a time, as a foreground service.
 *
 * A 200 MB download over mobile data takes minutes; without a foreground service Android would
 * freeze or kill the process halfway and leave a truncated file behind.
 */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null
    private var lastNotifiedAt = 0L

    private val container: AppContainer get() = (application as App).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()

        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_TASK_ID)?.let { cancelTask(it) }
        }

        ensureWorkerRunning()
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val notification = currentVisibleTask()
            ?.let { DownloadNotifier.progress(this, it) }
            ?: DownloadNotifier.preparing(this)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(this, DownloadNotifier.FOREGROUND_ID, notification, type)
        }.onFailure { Log.e(TAG, "startForeground failed", it) }
    }

    private fun cancelTask(taskId: String) {
        val queue = container.downloadQueue
        val task = queue.find(taskId) ?: return
        if (task.state.isTerminal) return
        queue.update(taskId) { it.copy(state = DownloadState.CANCELED) }
        container.engine.cancel(taskId)
        NotificationManagerCompat.from(this).cancel(taskId.hashCode())
    }

    private fun ensureWorkerRunning() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            while (isActive) {
                val next = container.downloadQueue.nextQueued() ?: break
                runTask(next)
            }
            stopSelfSafely()
        }
    }

    private suspend fun runTask(task: DownloadTask) {
        val queue = container.downloadQueue
        val tempDir = container.tempDirFor(task.id)

        try {
            queue.update(task.id) { it.copy(state = DownloadState.DOWNLOADING, progress = 0f) }
            pushProgressNotification(force = true)

            container.engine.ensureInitialized().getOrThrow()

            val file = container.engine.download(
                url = task.url,
                selection = task.selection,
                outputDir = tempDir,
                processId = task.id,
                cookiesFile = container.cookiesFile.takeIf { it.exists() },
            ) { percent, eta, line ->
                onProgressLine(task.id, percent, eta, line)
            }

            if (queue.find(task.id)?.state == DownloadState.CANCELED) {
                tempDir.deleteRecursively()
                return
            }

            queue.update(task.id) { it.copy(state = DownloadState.SAVING, progress = 100f) }
            pushProgressNotification(force = true)

            val mime = if (task.selection.isAudio) "audio/mpeg" else "video/mp4"
            val saved = container.mediaStoreSaver.saveToDownloads(file, mime)
            tempDir.deleteRecursively()

            val folderId = task.collection?.let { container.library.playlistFolderId(it) }
            container.historyDao.insert(
                HistoryEntry(
                    title = task.title,
                    uploader = task.uploader,
                    thumbnail = task.thumbnail,
                    sourceLabel = task.sourceLabel,
                    sourceUrl = task.url,
                    fileUri = saved.uri.toString(),
                    displayName = saved.displayName,
                    mimeType = mime,
                    sizeBytes = saved.sizeBytes,
                    durationSec = task.durationSec,
                    isAudio = task.selection.isAudio,
                    createdAt = System.currentTimeMillis(),
                    folderId = folderId,
                )
            )

            queue.update(task.id) {
                it.copy(
                    state = DownloadState.COMPLETED,
                    progress = 100f,
                    savedUri = saved.uri.toString(),
                )
            }
            DownloadNotifier.showCompleted(this, task, saved.uri, mime)
        } catch (e: YoutubeDL.CanceledException) {
            queue.update(task.id) { it.copy(state = DownloadState.CANCELED) }
            tempDir.deleteRecursively()
        } catch (e: Throwable) {
            tempDir.deleteRecursively()
            if (queue.find(task.id)?.state == DownloadState.CANCELED) return
            Log.e(TAG, "download failed", e)
            val reason = e.message?.lines()?.lastOrNull { line -> line.isNotBlank() }?.trim()
                ?: e.javaClass.simpleName
            queue.update(task.id) { it.copy(state = DownloadState.FAILED, error = reason) }
            DownloadNotifier.showFailed(this, task, reason)
        }
    }

    private fun onProgressLine(taskId: String, percent: Float, eta: Long, line: String) {
        val queue = container.downloadQueue
        if (queue.find(taskId)?.state == DownloadState.CANCELED) return
        // yt-dlp sits at 99% while ffmpeg merges the video and audio tracks; say so out loud
        // instead of showing a progress bar that looks stuck.
        val merging = line.contains("[Merger]") ||
            line.contains("[ExtractAudio]") ||
            line.contains("[EmbedThumbnail]")
        queue.update(taskId) {
            it.copy(
                state = if (merging) DownloadState.PROCESSING else DownloadState.DOWNLOADING,
                progress = percent.coerceIn(0f, 100f),
                etaSec = eta,
                speed = extractSpeed(line) ?: it.speed,
            )
        }
        pushProgressNotification()
    }

    private fun currentVisibleTask(): DownloadTask? = container.downloadQueue.tasks.value
        .firstOrNull { it.state.isActive && it.state != DownloadState.QUEUED }

    private fun pushProgressNotification(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifiedAt < NOTIFY_INTERVAL_MS) return
        lastNotifiedAt = now
        val current = currentVisibleTask() ?: return
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(DownloadNotifier.FOREGROUND_ID, DownloadNotifier.progress(this, current))
        }
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val NOTIFY_INTERVAL_MS = 700L

        const val ACTION_CANCEL = "com.mh.videodownloader.action.CANCEL"
        const val EXTRA_TASK_ID = "task_id"

        /** Adds a job to the shared queue and makes sure the service is running. */
        fun enqueue(context: Context, task: DownloadTask) {
            val app = context.applicationContext as App
            app.container.downloadQueue.enqueue(task)
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, DownloadService::class.java),
            )
        }

        /** Queues a whole playlist with a single service start instead of one per entry. */
        fun enqueueAll(context: Context, tasks: List<DownloadTask>) {
            if (tasks.isEmpty()) return
            val app = context.applicationContext as App
            tasks.forEach { app.container.downloadQueue.enqueue(it) }
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, DownloadService::class.java),
            )
        }

        fun cancel(context: Context, taskId: String) {
            val intent = Intent(context.applicationContext, DownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }
    }
}
