package com.mh.videodownloader.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 45_300_000 -> "45.3 MB". Returns null for unknown/zero sizes so callers can fall back. */
fun formatBytes(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val pattern = if (value >= 100 || unit == 0) "%.0f %s" else "%.1f %s"
    return String.format(Locale.US, pattern, value, units[unit])
}

/** 272 -> "04:32", 3872 -> "1:04:32". */
fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return "--:--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

/** Seconds remaining -> "02:15". yt-dlp reports -1 when it has no estimate yet. */
fun formatEta(etaSeconds: Long): String? {
    if (etaSeconds <= 0L) return null
    return formatDuration(etaSeconds.toInt())
}

/**
 * yt-dlp progress lines look like:
 * `[download]  45.3% of  120.51MiB at    2.13MiB/s ETA 00:32`
 * We only pull the speed out of them; percentage and ETA arrive through the callback.
 */
private val SPEED_REGEX = Regex("""at\s+([0-9.]+\s*[KMG]?i?B/s)""", RegexOption.IGNORE_CASE)

fun extractSpeed(line: String): String? =
    SPEED_REGEX.find(line)?.groupValues?.get(1)?.replace("iB/s", "B/s")?.trim()

fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("yyyy/MM/dd · HH:mm", Locale.US).format(Date(epochMillis))

/** Strips characters MediaStore refuses in DISPLAY_NAME and keeps the name a sane length. */
fun sanitizeFileName(name: String): String {
    val cleaned = name
        .replace(Regex("""[\/:*?"<>|\r\n\t]"""), "_")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .trimStart('.')
    val safe = cleaned.ifBlank { "download" }
    if (safe.toByteArray().size <= 180) return safe
    val dot = safe.lastIndexOf('.')
    val ext = if (dot > 0) safe.substring(dot) else ""
    var base = if (dot > 0) safe.substring(0, dot) else safe
    while (base.toByteArray().size > 180 - ext.toByteArray().size && base.isNotEmpty()) {
        base = base.dropLast(1)
    }
    return base.trim() + ext
}

fun percentOf(value: Float): Int = abs(value).toInt().coerceIn(0, 100)

/**
 * Wraps Latin/numeric fragments in a bidi isolate so "~28.5 MB" keeps its order inside an
 * Arabic (RTL) line instead of being flipped to "MB 28.5~".
 */
fun ltr(text: String): String = "$LTR_ISOLATE$text$POP_ISOLATE"

private const val LTR_ISOLATE = '\u2066'
private const val POP_ISOLATE = '\u2069'
