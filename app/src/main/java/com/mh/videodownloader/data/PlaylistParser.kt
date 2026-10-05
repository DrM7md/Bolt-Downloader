package com.mh.videodownloader.data

import com.mh.videodownloader.domain.PlaylistEntry
import com.mh.videodownloader.domain.PlaylistInfo
import com.mh.videodownloader.domain.Source
import com.mh.videodownloader.domain.SourceDetector
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns `--flat-playlist --dump-single-json` output into something the UI can list.
 *
 * Parsed with org.json rather than the engine's bundled Jackson: the shape is three fields deep
 * and org.json ships with Android, so the app gains no dependency for it.
 */
object PlaylistParser {

    fun parse(json: String, originalUrl: String): PlaylistInfo {
        val root = JSONObject(json)
        val entries = root.optJSONArray("entries")?.toEntries().orEmpty()
        return PlaylistInfo(
            title = root.optStringOrNull("title")
                ?: root.optStringOrNull("id")
                ?: originalUrl,
            uploader = root.optStringOrNull("uploader") ?: root.optStringOrNull("channel"),
            entries = entries,
            source = SourceDetector.detect(originalUrl).takeIf { it.isSupported } ?: Source.OTHER,
            originalUrl = originalUrl,
            singleVideoUrl = SourceDetector.singleVideoUrlIn(originalUrl),
        )
    }

    private fun JSONArray.toEntries(): List<PlaylistEntry> = buildList {
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            // Private and deleted videos stay in the index with no playable URL; they would only
            // fail later in the queue, so they never reach the list.
            val url = item.optStringOrNull("url")
                ?: item.optStringOrNull("webpage_url")
                ?: item.optStringOrNull("id")?.let { "https://www.youtube.com/watch?v=$it" }
                ?: continue
            add(
                PlaylistEntry(
                    id = item.optStringOrNull("id") ?: url,
                    // A flat index sometimes omits the title (private or region-blocked items).
                    // The video id identifies the row; the full URL just looks like noise.
                    title = item.optStringOrNull("title")
                        ?: item.optStringOrNull("id")
                        ?: url,
                    url = url,
                    durationSec = item.optDouble("duration", 0.0).toInt(),
                    thumbnail = item.bestThumbnail(),
                )
            )
        }
    }

    private fun JSONObject.bestThumbnail(): String? {
        optStringOrNull("thumbnail")?.let { return it }
        val thumbs = optJSONArray("thumbnails") ?: return null
        for (i in thumbs.length() - 1 downTo 0) {
            thumbs.optJSONObject(i)?.optStringOrNull("url")?.let { return it }
        }
        return null
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }
}
