package com.mh.videodownloader.domain

/** Everything the UI needs about a link after yt-dlp has looked at it. */
data class MediaInfo(
    val id: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Int,
    val source: Source,
    val extractorKey: String?,
    val originalUrl: String,
    val isLive: Boolean = false,
)

/**
 * One selectable row in the quality list, built from the formats yt-dlp really returned.
 *
 * [rung] counts the SHORT side, which is what "1080p" means to a viewer and what yt-dlp's `res`
 * sorter uses. Going by height alone labels a 720x1280 TikTok clip 1080p, which is simply wrong.
 */
data class FormatOption(
    val rung: Int,
    val label: String,
    val ext: String,
    val approxBytes: Long?,
    val fps: Int,
)

sealed interface DownloadSelection {
    data class Video(val height: Int, val label: String) : DownloadSelection
    data object AudioMp3 : DownloadSelection

    val isAudio: Boolean get() = this is AudioMp3
}

enum class DownloadState {
    QUEUED, DOWNLOADING, PROCESSING, SAVING, COMPLETED, FAILED, CANCELED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELED
    val isActive: Boolean get() = !isTerminal
}

data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String?,
    val sourceLabel: String,
    val selection: DownloadSelection,
    val durationSec: Int = 0,
    val uploader: String? = null,
    val state: DownloadState = DownloadState.QUEUED,
    val progress: Float = 0f,
    val etaSec: Long = -1L,
    val speed: String? = null,
    val error: String? = null,
    val savedUri: String? = null,
)

/** One row of a playlist index, as returned by `--flat-playlist` (no per-video request yet). */
data class PlaylistEntry(
    val id: String,
    val title: String,
    val url: String,
    val durationSec: Int,
    val thumbnail: String?,
)

data class PlaylistInfo(
    val title: String,
    val uploader: String?,
    val entries: List<PlaylistEntry>,
    val source: Source,
    val originalUrl: String,
    /** Set when the link pointed at one video inside a playlist, so we can offer just that one. */
    val singleVideoUrl: String?,
)
