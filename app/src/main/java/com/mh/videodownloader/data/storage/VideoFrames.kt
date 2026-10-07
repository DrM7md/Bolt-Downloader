package com.mh.videodownloader.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import kotlin.math.ceil
import kotlin.math.max

/**
 * A thumbnail for a local video: the frame 10% in (the very first is so often a fade from
 * black). [modifiedAt] is part of the identity so a replaced file gets a fresh frame.
 */
data class VideoFrame(val uri: Uri, val modifiedAt: Long)

/**
 * Pulls a video frame at the size it will be shown and keeps it on disk as a small JPEG.
 *
 * Extracting a frame means opening the video and decoding around a keyframe — tens of
 * milliseconds per file even on a fast phone. Coil only caches decoded images in memory, so
 * without this every launch (and every scroll past the memory cache) redid that work. Now it
 * happens once per file, and later loads are a ~15 KB JPEG read.
 */
class VideoFrameFetcher(
    private val frame: VideoFrame,
    private val options: Options,
    private val cacheDir: File,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val slotW = bucket(options.size.width.pxOrElse { DEFAULT_PX })
        val slotH = bucket(options.size.height.pxOrElse { DEFAULT_PX })
        val file = File(cacheDir, cacheKey(frame, slotW, slotH) + ".jpg")
        if (!file.exists()) {
            val bitmap = extract(slotW, slotH) ?: error("No frame in ${frame.uri}")
            cacheDir.mkdirs()
            // Written beside the target and renamed, so a reader never sees half a JPEG.
            val tmp = File(cacheDir, "${file.name}.${System.nanoTime()}.tmp")
            try {
                tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                if (!tmp.renameTo(file)) tmp.delete()
            } finally {
                bitmap.recycle()
                tmp.delete()
            }
        }
        return SourceFetchResult(
            source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.DISK,
        )
    }

    private fun extract(slotW: Int, slotH: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, frame.uri)
            fun meta(key: Int) = retriever.extractMetadata(key)?.toLongOrNull() ?: 0L
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val atUs = if (durationMs > 0) durationMs * 100 else -1L // 10% in, in microseconds
            val edge = coverEdge(
                width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt(),
                height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt(),
                rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt(),
                slotW = slotW,
                slotH = slotH,
            )
            val option = MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                // Decodes straight to the target size instead of a full 1080p/4K frame. A square
                // box keeps the aspect ratio whatever the rotation; the long edge lands on [edge].
                retriever.getScaledFrameAtTime(atUs, option, edge, edge)
            } else {
                retriever.getFrameAtTime(atUs, option)?.let { scaleDown(it, edge) }
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun scaleDown(full: Bitmap, edge: Int): Bitmap {
        val long = max(full.width, full.height)
        if (long <= edge) return full
        val ratio = edge.toFloat() / long
        val scaled = Bitmap.createScaledBitmap(full, (full.width * ratio).toInt(), (full.height * ratio).toInt(), true)
        if (scaled !== full) full.recycle()
        return scaled
    }

    class Factory(context: Context) : Fetcher.Factory<VideoFrame> {
        private val cacheDir = cacheDir(context)
        override fun create(data: VideoFrame, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoFrameFetcher(data, options, cacheDir)
    }

    /** Lets Coil's memory cache recognise the same frame again (it adds the size itself). */
    class Key : Keyer<VideoFrame> {
        override fun key(data: VideoFrame, options: Options) = "${data.uri}#${data.modifiedAt}"
    }

    companion object {
        private const val DEFAULT_PX = 512
        private const val MAX_CACHE_BYTES = 48L * 1024 * 1024
        private const val TRIM_TO_BYTES = 32L * 1024 * 1024

        fun cacheDir(context: Context) = File(context.cacheDir, "video_frames")

        /** Rounds a slot side up to 64 px steps so nearby slot sizes share one cached file. */
        private fun bucket(px: Int) = ((px + 63) / 64 * 64).coerceIn(64, 1024)

        /**
         * The long edge a frame needs so that, scaled down, it still covers a [slotW]×[slotH]
         * cropped slot in both directions — portrait videos included. Never larger than the
         * video itself.
         */
        private fun coverEdge(width: Int, height: Int, rotation: Int, slotW: Int, slotH: Int): Int {
            if (width <= 0 || height <= 0) return max(slotW, slotH) * 16 / 9
            val (w, h) = if (rotation == 90 || rotation == 270) height to width else width to height
            val scale = minOf(1f, max(slotW.toFloat() / w, slotH.toFloat() / h))
            return ceil(max(w, h) * scale).toInt().coerceAtLeast(1)
        }

        private fun cacheKey(frame: VideoFrame, slotW: Int, slotH: Int) =
            "${frame.uri}|${frame.modifiedAt}|${slotW}x$slotH".encodeUtf8().sha256().hex().take(32)

        /** Keeps the frame cache small: past [MAX_CACHE_BYTES], the oldest frames go first. */
        fun trim(context: Context) {
            val files = cacheDir(context).listFiles()?.filter { it.isFile } ?: return
            var total = files.sumOf { it.length() }
            if (total <= MAX_CACHE_BYTES) return
            for (file in files.sortedBy { it.lastModified() }) {
                if (total <= TRIM_TO_BYTES) break
                total -= file.length()
                file.delete()
            }
        }
    }
}
