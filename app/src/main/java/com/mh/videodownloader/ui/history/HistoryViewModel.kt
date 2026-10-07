package com.mh.videodownloader.ui.history

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.R
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.data.storage.MediaDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The download log: what was downloaded, newest first. It only ever removes its own rows —
 * clearing it never deletes a video. Files are managed in the library.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container

    /** Null until the first read from the database, so "empty" is never shown by mistake. */
    val entries: StateFlow<List<HistoryEntry>?> = container.historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _info = MutableStateFlow<MediaDetails?>(null)
    val info: StateFlow<MediaDetails?> = _info.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    /** Finds the file — wherever the library has moved it since — then hands it to [then]. */
    fun withFile(entry: HistoryEntry, then: (Uri) -> Unit) {
        viewModelScope.launch {
            val uri = container.fileLocator.resolve(entry)
            if (uri == null) _message.value = R.string.file_missing else then(uri)
        }
    }

    fun showInfo(entry: HistoryEntry) {
        viewModelScope.launch {
            val uri = container.fileLocator.resolve(entry)
            val probe = uri?.let { container.phoneMedia.probe(it) }
            _info.value = MediaDetails(
                name = entry.displayName,
                path = entry.filePath,
                sizeBytes = entry.sizeBytes,
                date = entry.createdAt,
                durationMs = probe?.first ?: entry.durationSec.takeIf { it > 0 }?.let { it * 1000L },
                width = probe?.second,
                height = probe?.third,
                mimeType = entry.mimeType,
                sourceUrl = entry.sourceUrl,
            )
        }
    }

    fun dismissInfo() {
        _info.value = null
    }

    fun remove(entry: HistoryEntry) {
        viewModelScope.launch { container.historyDao.deleteById(entry.id) }
    }

    fun clearAll() {
        viewModelScope.launch { container.historyDao.clear() }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
