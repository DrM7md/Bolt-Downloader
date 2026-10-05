package com.mh.videodownloader.ui.library

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.data.db.Folder
import com.mh.videodownloader.data.db.FolderSummary
import com.mh.videodownloader.data.db.HistoryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : EntryActionsViewModel(application) {

    // Null until the first database read, so a cold start never flashes "library is empty".
    val folderSummaries: StateFlow<List<FolderSummary>?> = library.folderSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val unsorted: StateFlow<List<HistoryEntry>?> = library.unsorted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { library.createFolder(name) }
    }
}

class FolderViewModel(
    application: Application,
    savedState: SavedStateHandle,
) : EntryActionsViewModel(application) {

    val folderId: Long = checkNotNull(savedState.get<Long>(ARG_FOLDER_ID))

    val folder: StateFlow<Folder?> = library.folder(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val items: StateFlow<List<HistoryEntry>?> = library.itemsIn(folderId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun rename(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { library.renameFolder(folderId, name) }
    }

    fun deleteFolder() {
        viewModelScope.launch { library.deleteFolder(folderId) }
    }

    companion object {
        const val ARG_FOLDER_ID = "folderId"
    }
}
