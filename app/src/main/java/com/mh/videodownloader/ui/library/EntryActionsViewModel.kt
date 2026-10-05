package com.mh.videodownloader.ui.library

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.data.LibraryRepository
import com.mh.videodownloader.data.db.Folder
import com.mh.videodownloader.data.db.HistoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A pending system "allow this app to delete…" prompt for one entry. */
data class DeleteConsent(val entry: HistoryEntry, val request: IntentSender)

/**
 * What every list of downloads can do to an entry — delete (optionally with its file) and file
 * it into a folder. Shared so History, Library and folder screens behave identically.
 */
open class EntryActionsViewModel(application: Application) : AndroidViewModel(application) {

    protected val container = (application as App).container
    protected val library: LibraryRepository get() = container.library

    val folders: StateFlow<List<Folder>> = library.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _consent = MutableStateFlow<DeleteConsent?>(null)
    val consent: StateFlow<DeleteConsent?> = _consent.asStateFlow()

    fun delete(entry: HistoryEntry, deleteFile: Boolean) {
        viewModelScope.launch {
            when (val outcome = library.delete(entry, deleteFile)) {
                LibraryRepository.DeleteOutcome.Done -> Unit
                is LibraryRepository.DeleteOutcome.NeedsConsent ->
                    _consent.value = DeleteConsent(entry, outcome.request)
            }
        }
    }

    fun onConsentResult(granted: Boolean) {
        val pending = _consent.value ?: return
        _consent.value = null
        if (granted) viewModelScope.launch { library.finishDelete(pending.entry) }
    }

    fun move(entry: HistoryEntry, folderId: Long?) {
        viewModelScope.launch { library.move(entry.id, folderId) }
    }

    /** "New folder…" from inside the move dialog: create it and file the entry in one go. */
    fun moveToNewFolder(entry: HistoryEntry, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = library.createFolder(name)
            library.move(entry.id, id)
        }
    }
}
