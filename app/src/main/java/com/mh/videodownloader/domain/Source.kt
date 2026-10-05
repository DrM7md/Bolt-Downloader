package com.mh.videodownloader.domain

import android.net.Uri
import androidx.annotation.StringRes
import com.mh.videodownloader.R

/**
 * The visible identity of a link's origin.
 *
 * This is a *presentation* layer, not an extraction layer — yt-dlp is what actually understands
 * each site. Source only decides what we show the user, which engine flags to add, and which
 * links to reject before we even start the engine.
 */
enum class Source(
    val brandName: String?,
    @StringRes val labelRes: Int?,
) {
    YOUTUBE("YouTube", null),
    INSTAGRAM("Instagram", null),
    TIKTOK("TikTok", null),
    TWITTER("X / Twitter", null),
    FACEBOOK("Facebook", null),
    DIRECT(null, R.string.source_direct),
    OTHER(null, R.string.source_other),
    UNKNOWN(null, R.string.source_unknown);

    val isSupported: Boolean get() = this != UNKNOWN
}

object SourceDetector {

    private val rules = listOf(
        Regex("""(youtube\.com|youtu\.be|youtube-nocookie\.com)""", RegexOption.IGNORE_CASE) to Source.YOUTUBE,
        Regex("""instagram\.com""", RegexOption.IGNORE_CASE) to Source.INSTAGRAM,
        Regex("""(tiktok\.com|vm\.tiktok\.com)""", RegexOption.IGNORE_CASE) to Source.TIKTOK,
        Regex("""(twitter\.com|x\.com|t\.co)""", RegexOption.IGNORE_CASE) to Source.TWITTER,
        Regex("""(facebook\.com|fb\.watch|fb\.com)""", RegexOption.IGNORE_CASE) to Source.FACEBOOK,
        Regex("""\.(mp4|m4a|mp3|webm|mkv|mov)(\?.*)?$""", RegexOption.IGNORE_CASE) to Source.DIRECT,
    )

    fun detect(url: String): Source {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return Source.UNKNOWN
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return Source.UNKNOWN
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return Source.UNKNOWN
        if (uri.host.isNullOrBlank()) return Source.UNKNOWN
        // OTHER means: a valid link we have no badge for. Hand it to yt-dlp anyway — it knows
        // over a thousand sites and our short list must never be the thing that blocks the user.
        return rules.firstOrNull { (regex, _) -> regex.containsMatchIn(trimmed) }?.second ?: Source.OTHER
    }

    fun isValid(url: String): Boolean = detect(url).isSupported

    /** True for a link that indexes several videos rather than one. */
    fun isPlaylist(url: String): Boolean {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return false
        if (!uri.getQueryParameter("list").isNullOrBlank()) return true
        val path = uri.path?.lowercase().orEmpty()
        return path.contains("/playlist") || path.contains("/sets/")
    }

    /** The single-video link hiding inside a "watch?v=...&list=..." URL, if there is one. */
    fun singleVideoUrlIn(url: String): String? {
        val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
        val id = uri.getQueryParameter("v") ?: return null
        return if (id.isBlank()) null else "https://www.youtube.com/watch?v=$id"
    }

    /** Pulls the first http(s) link out of arbitrary shared text ("Look at this: https://..."). */
    fun firstUrlIn(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = Regex("""https?://\S+""", RegexOption.IGNORE_CASE).find(text) ?: return null
        return match.value.trimEnd('.', ',', ')', '»', '"', '\'')
    }
}
