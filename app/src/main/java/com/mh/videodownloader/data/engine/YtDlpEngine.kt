package com.mh.videodownloader.data.engine

import android.content.Context
import android.util.Log
import com.mh.videodownloader.domain.DownloadSelection
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The only place in the app that talks to yt-dlp.
 *
 * Everything above this class deals in [VideoInfo] and files; everything below is the bundled
 * Python + ffmpeg payload shipped by youtubedl-android.
 */
class YtDlpEngine(private val appContext: Context) {

    private val initMutex = Mutex()

    /**
     * Setup runs in a scope of its own on purpose. It unpacks Python and copies the yt-dlp
     * zipapp out of the APK, and a caller that walks away mid-copy (the user edits the link and
     * the previous analysis is cancelled) used to leave a truncated binary behind that every
     * later run then failed on. Callers await this job; cancelling the await never cancels it.
     */
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initJob: Deferred<Result<Unit>>? = null

    /**
     * Updating replaces the yt-dlp binary on disk, so it must never overlap a run that is
     * executing that same file — that is exactly how a download ends up reading a half-written
     * zipapp. Runs take one permit; an update takes all of them.
     */
    private val runPermits = Semaphore(MAX_CONCURRENT_RUNS)
    private val updateMutex = Mutex()

    private val _status = MutableStateFlow(Status.NOT_READY)
    val status: StateFlow<Status> = _status.asStateFlow()

    val isReady: Boolean get() = _status.value == Status.READY

    /** Unpacks Python and ffmpeg on first launch (~10s once), no-ops afterwards. */
    suspend fun ensureInitialized(): Result<Unit> {
        if (isReady) return Result.success(Unit)
        val job = initMutex.withLock {
            initJob?.takeIf { !it.isCompleted || isReady }
                ?: engineScope.async { runInit() }.also { initJob = it }
        }
        return job.await()
    }

    private fun runInit(): Result<Unit> {
        _status.value = Status.INITIALIZING
        return runCatching {
            YoutubeDL.getInstance().init(appContext)
            FFmpeg.getInstance().init(appContext)
        }.onSuccess {
            _status.value = Status.READY
        }.onFailure {
            Log.e(TAG, "engine init failed", it)
            _status.value = Status.FAILED
        }
    }

    /**
     * Re-extracts the yt-dlp zipapp. Recovery path for an interrupted first launch: without it a
     * half-written binary is permanent, because the library only copies the file when missing.
     */
    private fun repairYtDlp() {
        val ytdlpDir = File(File(appContext.noBackupFilesDir, YoutubeDL.baseName), YoutubeDL.ytdlpDirName)
        ytdlpDir.deleteRecursively()
        YoutubeDL.getInstance().init_ytdlp(appContext, ytdlpDir)
    }

    private fun Throwable.looksLikeBrokenEngine(): Boolean {
        val text = (message ?: "") + (cause?.message ?: "")
        return text.contains("ZipImportError") ||
            text.contains("bad local file header") ||
            text.contains("can't find '__main__' module")
    }

    /** Runs [block], and if the engine binary itself is broken, re-extracts it and tries once more. */
    private suspend fun <T> repairingIfBroken(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!e.looksLikeBrokenEngine()) throw e
            Log.w(TAG, "yt-dlp binary looks corrupt, re-extracting", e)
            withContext(NonCancellable) { repairYtDlp() }
            block()
        }
    }

    enum class Status { NOT_READY, INITIALIZING, READY, FAILED }

    suspend fun analyze(url: String): VideoInfo = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("--retries", "3")
        }
        runPermits.withPermit { repairingIfBroken { YoutubeDL.getInstance().getInfo(request) } }
    }

    /**
     * Reads a playlist without touching any of its videos: `--flat-playlist` asks the site for the
     * index only, which is one request instead of one per entry.
     */
    suspend fun analyzePlaylist(url: String): String = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            addOption("--no-warnings")
            addOption("--socket-timeout", "20")
            addOption("--retries", "3")
        }
        val response = runPermits.withPermit {
            repairingIfBroken { YoutubeDL.getInstance().execute(request, null, null) }
        }
        response.out.ifBlank { throw IllegalStateException("playlist returned no JSON") }
    }

    /**
     * Runs the actual download into [outputDir] (an app-private folder) and returns the file
     * produced. Progress arrives on the caller's thread through [onProgress].
     */
    suspend fun download(
        url: String,
        selection: DownloadSelection,
        outputDir: File,
        processId: String,
        onProgress: (percent: Float, etaSec: Long, line: String) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        outputDir.mkdirs()
        val request = YoutubeDLRequest(url).apply {
            addOption("-o", File(outputDir, "%(title).80B [%(id)s].%(ext)s").absolutePath)
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--no-warnings")
            addOption("--continue")
            addOption("--retries", "5")
            addOption("--socket-timeout", "20")

            when (selection) {
                is DownloadSelection.Video -> {
                    // Above 720p YouTube serves video and audio as separate tracks, so the
                    // "+bestaudio" half and ffmpeg merging are what keep the result from being silent.
                    addOption("-f", "bv*+ba/b")
                    // Resolution first, codec second. Filtering by [height<=N][ext=mp4] instead —
                    // as this used to — silently handed back 720p whenever a site published 1080p
                    // as VP9/AV1 only, and mislabelled portrait clips, because yt-dlp's `res`
                    // sorts on the SHORT side while `height` is the long one on a phone video.
                    addOption("-S", "res:${selection.height},vcodec:h264,ext:mp4:m4a")
                    addOption("--merge-output-format", "mp4")
                }

                DownloadSelection.AudioMp3 -> {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K")
                    addOption("--embed-thumbnail")
                    addOption("--embed-metadata")
                }
            }
        }

        runPermits.withPermit {
            repairingIfBroken {
                YoutubeDL.getInstance().execute(request, processId) { percent, eta, line ->
                    onProgress(percent, eta, line)
                }
            }
        }

        outputDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.lastModified() }
            ?: throw IllegalStateException("yt-dlp finished but produced no file")
    }

    fun cancel(processId: String) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            .onFailure { Log.w(TAG, "cancel failed", it) }
    }

    suspend fun update(): UpdateResult = updateMutex.withLock {
        withExclusiveEngine {
            try {
                if (!isReady) ensureInitialized().getOrThrow()
                val status = YoutubeDL.getInstance()
                    .updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.STABLE)
                if (status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE) {
                    UpdateResult.AlreadyUpToDate
                } else {
                    UpdateResult.Updated(version())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.w(TAG, "engine update failed", e)
                UpdateResult.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Waits for every in-flight yt-dlp process to finish, then keeps the engine to itself. */
    private suspend fun <T> withExclusiveEngine(block: suspend () -> T): T {
        var acquired = 0
        try {
            repeat(MAX_CONCURRENT_RUNS) {
                runPermits.acquire()
                acquired++
            }
            return withContext(Dispatchers.IO) { block() }
        } finally {
            repeat(acquired) { runPermits.release() }
        }
    }

    fun version(): String? = runCatching { YoutubeDL.getInstance().version(appContext) }.getOrNull()

    sealed interface UpdateResult {
        data class Updated(val version: String?) : UpdateResult
        data object AlreadyUpToDate : UpdateResult
        data class Failed(val message: String) : UpdateResult
    }

    private companion object {
        const val TAG = "YtDlpEngine"
        const val MAX_CONCURRENT_RUNS = 4
    }
}
