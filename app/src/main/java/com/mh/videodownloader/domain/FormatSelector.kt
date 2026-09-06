package com.mh.videodownloader.domain

import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo

/**
 * Turns the raw `formats` array yt-dlp returns into the short, honest list we show the user.
 * Nothing here is hard-coded: a rung only appears if the site actually offers it.
 */
object FormatSelector {

    private val RUNGS = listOf(144, 240, 360, 480, 720, 1080, 1440, 2160, 4320)

    fun toMediaInfo(info: VideoInfo, originalUrl: String, source: Source): MediaInfo = MediaInfo(
        id = info.id.orEmpty(),
        title = (info.fulltitle ?: info.title)?.takeIf { it.isNotBlank() } ?: originalUrl,
        uploader = info.uploader ?: info.uploaderId,
        thumbnail = info.thumbnail ?: info.thumbnails?.lastOrNull()?.url,
        durationSec = info.duration,
        source = source,
        extractorKey = info.extractorKey ?: info.extractor,
        originalUrl = originalUrl,
        isLive = info.duration <= 0 && info.formats?.any { it.url?.contains(".m3u8") == true } == true,
    )

    fun buildOptions(info: VideoInfo): List<FormatOption> {
        val duration = info.duration
        val formats = info.formats.orEmpty()

        val audioOnly = formats.filter { it.isAudioOnly }
        val bestAudioBytes = audioOnly
            .maxByOrNull { it.tbr }
            ?.let { estimateBytes(it, duration) }

        val videoFormats = formats.filter { it.hasVideo && it.height > 0 }
        if (videoFormats.isEmpty()) return emptyList()

        return videoFormats
            .groupBy { rungFor(it.height) }
            .mapNotNull { (rung, candidates) ->
                // Prefer mp4/h264 at the same rung: it merges fastest and plays everywhere.
                val best = candidates.maxByOrNull { scoreOf(it) } ?: return@mapNotNull null
                val videoBytes = estimateBytes(best, duration)
                val needsAudio = best.acodec == null || best.acodec == "none"
                val total = when {
                    videoBytes == null -> null
                    needsAudio -> videoBytes + (bestAudioBytes ?: 0L)
                    else -> videoBytes
                }
                FormatOption(
                    height = rung,
                    label = "${rung}p",
                    ext = if (best.ext == "webm" && needsAudio) "mp4" else (best.ext ?: "mp4"),
                    approxBytes = total,
                    fps = best.fps,
                )
            }
            .sortedBy { it.height }
    }

    fun bestAudioBytes(info: VideoInfo): Long? =
        info.formats.orEmpty().filter { it.isAudioOnly }
            .maxByOrNull { it.tbr }
            ?.let { estimateBytes(it, info.duration) }

    private fun rungFor(height: Int): Int = RUNGS.lastOrNull { it <= height } ?: height

    private fun scoreOf(f: VideoFormat): Int {
        var score = f.tbr
        if (f.ext == "mp4") score += 4000
        if (f.vcodec?.startsWith("avc") == true) score += 2000
        return score
    }

    private fun estimateBytes(f: VideoFormat, durationSec: Int): Long? = when {
        f.fileSize > 0 -> f.fileSize
        f.fileSizeApproximate > 0 -> f.fileSizeApproximate
        f.tbr > 0 && durationSec > 0 -> f.tbr.toLong() * 1000L / 8L * durationSec
        else -> null
    }

    private val VideoFormat.hasVideo: Boolean get() = vcodec != null && vcodec != "none"
    private val VideoFormat.isAudioOnly: Boolean
        get() = (vcodec == null || vcodec == "none") && acodec != null && acodec != "none"
}
