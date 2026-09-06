package com.mh.videodownloader.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mh.videodownloader.MainActivity
import com.mh.videodownloader.R
import com.mh.videodownloader.domain.DownloadTask
import com.mh.videodownloader.util.formatEta
import com.mh.videodownloader.util.percentOf

/** Builds every notification the download pipeline shows. */
object DownloadNotifier {

    const val CHANNEL_PROGRESS = "downloads"
    const val CHANNEL_COMPLETED = "completed"
    const val FOREGROUND_ID = 1001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // LOW importance on purpose: a progress bar that beeps on every percent is unusable.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                context.getString(R.string.channel_downloads),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COMPLETED,
                context.getString(R.string.channel_completed),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        )
    }

    fun progress(context: Context, task: DownloadTask): Notification {
        val percent = percentOf(task.progress)
        val detail = buildList {
            add(context.getString(stateLabel(task)))
            task.speed?.let { add(it) }
            formatEta(task.etaSec)?.let { add(it) }
        }.joinToString(" · ")

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notif_download)
            .setContentTitle(task.title)
            .setContentText(detail)
            .setSubText(task.sourceLabel)
            .setProgress(100, percent, task.progress < 0f)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent(context))
            .addAction(
                0,
                context.getString(R.string.cancel),
                cancelIntent(context, task.id),
            )
            .build()
    }

    fun preparing(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notif_download)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_preparing))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openAppIntent(context))
            .build()

    fun showCompleted(context: Context, task: DownloadTask, uri: Uri, mimeType: String) {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            context, task.id.hashCode(), view,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setSmallIcon(R.drawable.ic_notif_done)
            .setContentTitle(task.title)
            .setContentText(context.getString(R.string.notif_complete_action))
            .setSubText(task.sourceLabel)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        notify(context, task.id.hashCode(), notification)
    }

    fun showFailed(context: Context, task: DownloadTask, reason: String?) {
        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setSmallIcon(R.drawable.ic_notif_error)
            .setContentTitle(context.getString(R.string.notif_failed_title))
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason ?: task.title))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        notify(context, task.id.hashCode(), notification)
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, DownloadService::class.java).apply {
            action = DownloadService.ACTION_CANCEL
            putExtra(DownloadService.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getService(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stateLabel(task: DownloadTask) = when (task.state) {
        com.mh.videodownloader.domain.DownloadState.QUEUED -> R.string.state_queued
        com.mh.videodownloader.domain.DownloadState.DOWNLOADING -> R.string.state_downloading
        com.mh.videodownloader.domain.DownloadState.PROCESSING -> R.string.state_processing
        com.mh.videodownloader.domain.DownloadState.SAVING -> R.string.state_saving
        com.mh.videodownloader.domain.DownloadState.COMPLETED -> R.string.state_completed
        com.mh.videodownloader.domain.DownloadState.FAILED -> R.string.state_failed
        com.mh.videodownloader.domain.DownloadState.CANCELED -> R.string.state_canceled
    }
}
