package com.mh.videodownloader.ui.library

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.data.DeviceItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** One level of the folder the user is browsing; the stack is the path from the picked root. */
data class FolderLevel(val documentId: String, val name: String)

sealed interface DeviceLibraryState {
    data object NoFolder : DeviceLibraryState
    data object Loading : DeviceLibraryState
    data object Unreadable : DeviceLibraryState
    data class Ready(val path: List<FolderLevel>, val items: List<DeviceItem>) : DeviceLibraryState
}

/**
 * Library mode backed by a folder on the device instead of the app's own download records.
 * Sub-folders are browsed in place; the files themselves are never copied into the app.
 */
class DeviceLibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container
    private val source = container.deviceFolders

    private val _state = MutableStateFlow<DeviceLibraryState>(DeviceLibraryState.Loading)
    val state: StateFlow<DeviceLibraryState> = _state.asStateFlow()

    private var tree: Uri? = null
    private var path: List<FolderLevel> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings
                .map { it.libraryFolder }
                .distinctUntilChanged()
                .collect { picked -> openRoot(picked) }
        }
    }

    private suspend fun openRoot(picked: String?) {
        val uri = picked?.toUri()
        tree = uri
        if (uri == null) {
            _state.value = DeviceLibraryState.NoFolder
            return
        }
        val name = source.displayName(uri)
        if (name == null) {
            // The grant is gone (folder deleted, or access revoked from system settings).
            _state.value = DeviceLibraryState.Unreadable
            return
        }
        path = listOf(FolderLevel(source.rootId(uri), name))
        load()
    }

    /** Re-reads the current level; files change outside the app, so this runs on every resume. */
    fun refresh() {
        if (tree != null && path.isNotEmpty()) load()
    }

    fun open(folder: DeviceItem) {
        path = path + FolderLevel(folder.documentId, folder.name)
        load()
    }

    /** Returns false at the root so the caller can let the system handle Back. */
    fun up(): Boolean {
        if (path.size <= 1) return false
        path = path.dropLast(1)
        load()
        return true
    }

    fun delete(item: DeviceItem) {
        viewModelScope.launch {
            if (source.delete(item)) refresh()
        }
    }

    private fun load() {
        val uri = tree ?: return
        val level = path.lastOrNull() ?: return
        val snapshot = path
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Keep the previous list on screen while re-reading; only a first load shows Loading.
            if (_state.value !is DeviceLibraryState.Ready) _state.value = DeviceLibraryState.Loading
            _state.value = runCatching { source.list(uri, level.documentId) }
                .fold(
                    onSuccess = { DeviceLibraryState.Ready(snapshot, it) },
                    onFailure = { DeviceLibraryState.Unreadable },
                )
        }
    }
}
