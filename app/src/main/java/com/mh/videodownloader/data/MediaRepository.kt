package com.mh.videodownloader.data

import com.mh.videodownloader.data.engine.YtDlpEngine
import com.mh.videodownloader.domain.FormatOption
import com.mh.videodownloader.domain.FormatSelector
import com.mh.videodownloader.domain.MediaInfo
import com.mh.videodownloader.domain.PlaylistInfo
import com.mh.videodownloader.domain.SourceDetector
import kotlinx.coroutines.CancellationException

data class Analysis(
    val info: MediaInfo,
    val formats: List<FormatOption>,
    val audioBytes: Long?,
)

class MediaRepository(private val engine: YtDlpEngine) {

    suspend fun analyze(url: String): Result<Analysis> {
        val clean = url.trim()
        val source = SourceDetector.detect(clean)
        if (!source.isSupported) {
            return Result.failure(InvalidUrlException())
        }
        return try {
            engine.ensureInitialized().getOrThrow()
            val raw = engine.analyze(clean)
            Result.success(
                Analysis(
                    info = FormatSelector.toMediaInfo(raw, clean, source),
                    formats = FormatSelector.buildOptions(raw),
                    audioBytes = FormatSelector.bestAudioBytes(raw),
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    suspend fun analyzePlaylist(url: String): Result<PlaylistInfo> {
        val clean = url.trim()
        if (!SourceDetector.detect(clean).isSupported) {
            return Result.failure(InvalidUrlException())
        }
        return try {
            engine.ensureInitialized().getOrThrow()
            val json = engine.analyzePlaylist(clean)
            val info = PlaylistParser.parse(json, clean)
            if (info.entries.isEmpty()) {
                Result.failure(IllegalStateException("playlist has no downloadable entries"))
            } else {
                Result.success(info)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    class InvalidUrlException : Exception("invalid url")
}
