package com.mh.videodownloader.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.R
import com.mh.videodownloader.data.MediaRepository
import com.mh.videodownloader.data.engine.YtDlpEngine
import com.mh.videodownloader.domain.DownloadSelection
import com.mh.videodownloader.domain.DownloadTask
import com.mh.videodownloader.domain.FormatOption
import com.mh.videodownloader.domain.MediaInfo
import com.mh.videodownloader.domain.SourceDetector
import com.mh.videodownloader.service.DownloadService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface HomeUiState {
    data object Idle : HomeUiState
    data object Analyzing : HomeUiState
    data class Ready(
        val info: MediaInfo,
        val formats: List<FormatOption>,
        val audioBytes: Long?,
    ) : HomeUiState

    data class Error(val message: String, val canRetryAfterUpdate: Boolean) : HomeUiState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedHeight = MutableStateFlow<Int?>(null)
    val selectedHeight: StateFlow<Int?> = _selectedHeight.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    private val _isUpdatingEngine = MutableStateFlow(false)
    val isUpdatingEngine: StateFlow<Boolean> = _isUpdatingEngine.asStateFlow()

    val engineStatus = container.engine.status
    val downloads = container.downloadQueue.tasks

    private var analyzeJob: Job? = null

    fun onUrlChanged(value: String) {
        _url.value = value
        // A new link invalidates whatever we were showing; never let the user download the
        // previous video by accident.
        analyzeJob?.cancel()
        if (_uiState.value !is HomeUiState.Idle) _uiState.value = HomeUiState.Idle
        _selectedHeight.value = null
    }

    /** A link arriving from another app's share sheet. Always wins over what is in the field. */
    fun submitExternalUrl(value: String, messageRes: Int?, autoAnalyze: Boolean) {
        val link = SourceDetector.firstUrlIn(value) ?: value.trim()
        if (link.isBlank() || link == _url.value) return
        onUrlChanged(link)
        messageRes?.let { _message.value = it }
        if (autoAnalyze && SourceDetector.isValid(link)) analyze()
    }

    /**
     * Offers whatever is on the clipboard when the app comes to the foreground. Deliberately
     * picky: it only fills an empty field, and only with something that really is a link —
     * an address or a stray sentence on the clipboard must never land in the box.
     */
    fun offerClipboard(text: String?) {
        if (_url.value.isNotBlank()) return
        val link = SourceDetector.firstUrlIn(text) ?: return
        onUrlChanged(link)
        _message.value = R.string.clipboard_pasted
    }

    /** The explicit paste button: the user asked for it, so take the text as-is. */
    fun pasteFromClipboard(text: String?) {
        val raw = text?.trim().orEmpty()
        if (raw.isBlank()) return
        onUrlChanged(SourceDetector.firstUrlIn(raw) ?: raw)
    }

    fun clearUrl() {
        onUrlChanged("")
    }

    fun analyze() {
        val target = _url.value.trim()
        if (target.isEmpty()) return
        if (!SourceDetector.isValid(target)) {
            _uiState.value = HomeUiState.Error(
                getApplication<Application>().getString(R.string.error_invalid_url),
                canRetryAfterUpdate = false,
            )
            return
        }

        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Analyzing
            container.mediaRepository.analyze(target)
                .onSuccess { analysis ->
                    _uiState.value = HomeUiState.Ready(
                        info = analysis.info,
                        formats = analysis.formats,
                        audioBytes = analysis.audioBytes,
                    )
                    // Default to the best rung at or below 720p — the one people actually pick.
                    _selectedHeight.value = analysis.formats.lastOrNull { it.height <= 720 }?.height
                        ?: analysis.formats.firstOrNull()?.height
                }
                .onFailure { error ->
                    _uiState.value = when (error) {
                        is MediaRepository.InvalidUrlException -> HomeUiState.Error(
                            getApplication<Application>().getString(R.string.error_invalid_url),
                            canRetryAfterUpdate = false,
                        )

                        else -> HomeUiState.Error(
                            error.readableMessage(),
                            canRetryAfterUpdate = true,
                        )
                    }
                }
        }
    }

    /**
     * Roughly 80% of sudden breakages are a yt-dlp version behind a site change, so the retry
     * button updates the engine first instead of repeating the same failing call.
     */
    fun updateEngineAndRetry() {
        if (_isUpdatingEngine.value) return
        viewModelScope.launch {
            _isUpdatingEngine.value = true
            val result = container.engine.update()
            _isUpdatingEngine.value = false
            _message.value = when (result) {
                is YtDlpEngine.UpdateResult.Updated -> R.string.engine_updated
                YtDlpEngine.UpdateResult.AlreadyUpToDate -> R.string.engine_up_to_date
                is YtDlpEngine.UpdateResult.Failed -> R.string.engine_update_failed_short
            }
            if (result !is YtDlpEngine.UpdateResult.Failed) {
                container.settings.setLastEngineUpdate(System.currentTimeMillis())
            }
            analyze()
        }
    }

    fun selectHeight(height: Int) {
        _selectedHeight.value = height
    }

    fun downloadVideo() {
        val ready = _uiState.value as? HomeUiState.Ready ?: return
        val height = _selectedHeight.value ?: ready.formats.lastOrNull()?.height ?: return
        val label = ready.formats.firstOrNull { it.height == height }?.label ?: "${height}p"
        enqueue(ready.info, DownloadSelection.Video(height, label))
    }

    fun downloadAudio() {
        val ready = _uiState.value as? HomeUiState.Ready ?: return
        enqueue(ready.info, DownloadSelection.AudioMp3)
    }

    private fun enqueue(info: MediaInfo, selection: DownloadSelection) {
        val context = getApplication<Application>()
        val task = DownloadTask(
            id = UUID.randomUUID().toString(),
            url = info.originalUrl,
            title = info.title,
            thumbnail = info.thumbnail,
            sourceLabel = info.source.brandName
                ?: context.getString(info.source.labelRes ?: R.string.source_other),
            selection = selection,
            durationSec = info.durationSec,
            uploader = info.uploader,
        )
        DownloadService.enqueue(context, task)
        _message.value = R.string.download_started
    }

    fun cancelDownload(taskId: String) {
        DownloadService.cancel(getApplication(), taskId)
    }

    fun dismissTask(taskId: String) {
        container.downloadQueue.remove(taskId)
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun Throwable.readableMessage(): String {
        val raw = message?.trim().orEmpty()
        if (raw.isBlank()) return javaClass.simpleName
        // yt-dlp errors arrive as a wall of traceback; the last ERROR line is the useful one.
        val errorLine = raw.lines().lastOrNull { it.contains("ERROR", ignoreCase = true) }
            ?: raw.lines().lastOrNull { it.isNotBlank() }
        return (errorLine ?: raw)
            .removePrefix("ERROR:")
            .trim()
            .take(400)
    }
}
