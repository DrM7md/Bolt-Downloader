package com.mh.videodownloader.ui.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.data.db.HistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container

    val entries: StateFlow<List<HistoryEntry>> = container.historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(entry: HistoryEntry) {
        viewModelScope.launch { container.historyDao.deleteById(entry.id) }
    }

    fun clearAll() {
        viewModelScope.launch { container.historyDao.clear() }
    }
}
