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

/** One selectable row in the quality list, built from the formats yt-dlp really returned. */
data class FormatOption(
    val height: Int,
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
