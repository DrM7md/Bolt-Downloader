package com.mh.videodownloader.ui.history

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.data.db.HistoryEntry
import com.mh.videodownloader.ui.library.EntryActionsViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : EntryActionsViewModel(application) {

    /** Null until the first read from the database, so "empty" is never shown by mistake. */
    val entries: StateFlow<List<HistoryEntry>?> = container.historyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun clearAll() {
        viewModelScope.launch { container.historyDao.clear() }
    }
}
