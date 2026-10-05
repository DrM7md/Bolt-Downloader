package com.mh.videodownloader.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.net.toUri
import com.mh.videodownloader.R

/**
 * A URI another app is allowed to read.
 *
 * On Android 10+ downloads are saved through MediaStore and already come back as `content://`.
 * On 8–9 they are real files, and handing a `file://` URI to another app throws
 * FileUriExposedException — so those get wrapped by our FileProvider instead.
 */
fun shareableUri(context: Context, rawUri: String): Uri {
    val uri = rawUri.toUri()
    if (uri.scheme != "file") return uri
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        uri.toFile(),
    )
}

/**
 * Hands the file to the system share sheet. Nothing leaves the device until the user picks a
 * target there — the chooser is the confirmation step.
 */
fun shareMedia(context: Context, rawUri: String, mimeType: String, title: String): Boolean =
    runCatching {
        val uri = shareableUri(context, rawUri)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_SUBJECT, title)
            // ClipData is what lets the system share sheet read the file's name and thumbnail
            // for its preview; without it the sheet falls back to the raw MediaStore row id.
            clipData = ClipData.newUri(context.contentResolver, title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_via))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }.isSuccess
