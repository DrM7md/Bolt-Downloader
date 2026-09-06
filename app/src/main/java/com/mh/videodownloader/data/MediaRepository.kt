package com.mh.videodownloader.data

import com.mh.videodownloader.data.engine.YtDlpEngine
import com.mh.videodownloader.domain.FormatOption
import com.mh.videodownloader.domain.FormatSelector
import com.mh.videodownloader.domain.MediaInfo
import com.mh.videodownloader.domain.SourceDetector
import kotlinx.coroutines.CancellationException
import java.io.File

data class Analysis(
    val info: MediaInfo,
    val formats: List<FormatOption>,
    val audioBytes: Long?,
)

class MediaRepository(
    private val engine: YtDlpEngine,
    private val cookiesProvider: () -> File?,
) {

    suspend fun analyze(url: String): Result<Analysis> {
        val clean = url.trim()
        val source = SourceDetector.detect(clean)
        if (!source.isSupported) {
            return Result.failure(InvalidUrlException())
        }
        return try {
            engine.ensureInitialized().getOrThrow()
            val raw = engine.analyze(clean, cookiesProvider())
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

    class InvalidUrlException : Exception("invalid url")
}
