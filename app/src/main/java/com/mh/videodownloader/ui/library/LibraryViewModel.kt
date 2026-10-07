package com.mh.videodownloader.ui.library

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.R
import com.mh.videodownloader.data.prefs.AppSettings
import com.mh.videodownloader.data.storage.DownloadSaver
import com.mh.videodownloader.data.storage.FolderRef
import com.mh.videodownloader.data.storage.FolderSummary
import com.mh.videodownloader.data.storage.MediaDetails
import com.mh.videodownloader.data.storage.PhoneVideo
import com.mh.videodownloader.data.storage.StoreItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** One level of the save folder being browsed; the stack is the path from the root. */
data class FolderLevel(val docId: String, val name: String)

data class FolderCard(val item: StoreItem, val summary: FolderSummary)

sealed interface FolderState {
    /** The save folder has not been granted yet — one tap on the system picker fixes that. */
    data object NeedsAccess : FolderState
    data object Loading : FolderState
    data object Unreadable : FolderState
    data class Ready(
        val path: List<FolderLevel>,
        val folders: List<FolderCard>,
        val files: List<StoreItem>,
    ) : FolderState {
        val current: FolderLevel get() = path.last()
    }
}

sealed interface PhoneState {
    data object NeedsPermission : PhoneState
    data object Loading : PhoneState
    data class Ready(val videos: List<PhoneVideo>) : PhoneState
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container
    private val store = container.folderStore

    val settings: StateFlow<AppSettings?> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _folderState = MutableStateFlow<FolderState>(FolderState.Loading)
    val folderState: StateFlow<FolderState> = _folderState.asStateFlow()

    private val _phoneState = MutableStateFlow<PhoneState>(PhoneState.Loading)
    val phoneState: StateFlow<PhoneState> = _phoneState.asStateFlow()

    /** Every folder under the save folder — the "move to" targets. */
    private val _allFolders = MutableStateFlow<List<FolderRef>>(emptyList())
    val allFolders: StateFlow<List<FolderRef>> = _allFolders.asStateFlow()

    private val _info = MutableStateFlow<MediaDetails?>(null)
    val info: StateFlow<MediaDetails?> = _info.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    /** A system "allow deleting this video?" prompt for a phone video the app does not own. */
    private val _deleteConsent = MutableStateFlow<IntentSender?>(null)
    val deleteConsent: StateFlow<IntentSender?> = _deleteConsent.asStateFlow()

    private var tree: Uri? = null
    private var path: List<FolderLevel> = emptyList()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            container.settings.settings
                .map { it.saveFolder }
                .distinctUntilChanged()
                .collect { openRoot(it) }
        }
    }

    // ---- save folder ------------------------------------------------------------------------

    private suspend fun openRoot(saveFolder: String?) {
        val uri = saveFolder?.toUri()
        tree = uri
        if (uri == null) {
            _folderState.value = FolderState.NeedsAccess
            return
        }
        val name = store.displayName(uri)
        if (name == null) {
            // Access was revoked or the folder deleted; ask again rather than show an error.
            _folderState.value = FolderState.NeedsAccess
            return
        }
        path = listOf(FolderLevel(store.rootId(uri), name))
        load(reloadFolderList = true)
    }

    /** Makes sure the default folder exists so the system picker can open right inside it. */
    fun prepareDefaultFolder() {
        runCatching {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                DownloadSaver.DEFAULT_FOLDER,
            ).mkdirs()
        }
    }

    /**
     * Keeps read/write access to the picked folder across restarts. Without the persistable
     * grant, the permission dies with this process and the library goes blank next launch.
     */
    fun onFolderGranted(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch { container.settings.setSaveFolder(uri.toString()) }
    }

    /** Files change outside the app, so this runs whenever the screen comes back. */
    fun refresh() {
        if (tree != null && path.isNotEmpty()) load(reloadFolderList = true)
    }

    fun open(folder: StoreItem) {
        path = path + FolderLevel(folder.docId, folder.name)
        load(reloadFolderList = false)
    }

    /** Returns false at the root so the caller can let the system handle Back. */
    fun up(): Boolean {
        if (path.size <= 1) return false
        path = path.dropLast(1)
        load(reloadFolderList = false)
        return true
    }

    fun createFolder(name: String) = mutate {
        store.createFolder(tree!!, path.last().docId, name)
    }

    fun rename(item: StoreItem, newName: String) = mutate {
        val renamed = store.rename(tree!!, item.docId, newName) ?: return@mutate
        if (item.isFolder) container.fileLocator.folderMoved(item.docId, renamed)
        else container.fileLocator.fileMoved(item.docId, renamed)
    }

    /** Deletes files and folders from the device. Folders go with everything inside. */
    fun delete(items: List<StoreItem>) = mutate {
        items.forEach { store.delete(tree!!, it.docId) }
    }

    /** A real move on disk; the download log is told so it can still find each file. */
    fun move(docIds: List<String>, targetFolderId: String) = mutate {
        val state = _folderState.value as? FolderState.Ready ?: return@mutate
        val from = state.current.docId
        if (from == targetFolderId) return@mutate
        val byId = (state.files + state.folders.map { it.item }).associateBy { it.docId }
        var failed = false
        for (id in docIds) {
            val item = byId[id] ?: continue
            // A folder cannot go inside itself or one of its own children.
            // ("…/YouTube2" starts with "…/YouTube" too, hence the separator in the check.)
            if (item.isFolder && (targetFolderId == item.docId || targetFolderId.startsWith(item.docId + "/"))) continue
            val moved = store.move(tree!!, id, from, targetFolderId)
            when {
                moved == null -> failed = true
                item.isFolder -> container.fileLocator.folderMoved(id, moved)
                else -> container.fileLocator.fileMoved(id, moved)
            }
        }
        if (failed) _message.value = R.string.move_failed
    }

    fun moveToNewFolder(docIds: List<String>, name: String) = mutate {
        val created = store.createFolder(tree!!, path.last().docId, name) ?: return@mutate
        val state = _folderState.value as? FolderState.Ready ?: return@mutate
        val from = state.current.docId
        val folderIds = state.folders.map { it.item.docId }.toSet()
        for (id in docIds) {
            val moved = store.move(tree!!, id, from, created) ?: continue
            if (id in folderIds) container.fileLocator.folderMoved(id, moved)
            else container.fileLocator.fileMoved(id, moved)
        }
    }

    fun showInfo(item: StoreItem) {
        viewModelScope.launch {
            val (duration, width, height) = container.phoneMedia.probe(item.uri)
            _info.value = MediaDetails(
                name = item.name,
                path = item.path,
                sizeBytes = item.sizeBytes,
                date = item.modifiedAt,
                durationMs = duration,
                width = width,
                height = height,
                mimeType = item.mimeType,
                sourceUrl = container.fileLocator.sourceUrlFor(item.path),
            )
        }
    }

    fun dismissInfo() {
        _info.value = null
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun mutate(block: suspend () -> Unit) {
        if (tree == null) return
        viewModelScope.launch {
            block()
            load(reloadFolderList = true)
        }
    }

    private fun load(reloadFolderList: Boolean) {
        val uri = tree ?: return
        val snapshot = path
        val level = snapshot.lastOrNull() ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Keep the previous list on screen while re-reading; only a first load shows Loading.
            if (_folderState.value !is FolderState.Ready) _folderState.value = FolderState.Loading
            val children = runCatching { store.children(uri, level.docId) }.getOrNull()
            if (children == null) {
                _folderState.value = FolderState.Unreadable
                return@launch
            }
            val folders = children.filter { it.isFolder }
                .map { folder -> async { FolderCard(folder, store.summary(uri, folder.docId)) } }
                .awaitAll()
            _folderState.value = FolderState.Ready(snapshot, folders, children.filterNot { it.isFolder })
            if (reloadFolderList || _allFolders.value.isEmpty()) {
                _allFolders.value = store.allFolders(uri)
            }
        }
    }

    // ---- all phone videos -------------------------------------------------------------------

    fun loadPhone(hasPermission: Boolean) {
        if (!hasPermission) {
            _phoneState.value = PhoneState.NeedsPermission
            return
        }
        viewModelScope.launch {
            if (_phoneState.value !is PhoneState.Ready) _phoneState.value = PhoneState.Loading
            _phoneState.value = PhoneState.Ready(container.phoneMedia.allVideos())
        }
    }

    fun showInfo(video: PhoneVideo) {
        viewModelScope.launch {
            _info.value = MediaDetails(
                name = video.name,
                path = video.path,
                sizeBytes = video.sizeBytes,
                date = video.addedAt,
                durationMs = video.durationMs,
                width = video.width,
                height = video.height,
                mimeType = video.mimeType,
                sourceUrl = container.fileLocator.sourceUrlFor(video.path),
            )
        }
    }

    fun deletePhoneVideos(videos: List<PhoneVideo>) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val blocked = videos.filter { video ->
                runCatching { resolver.delete(video.uri, null, null) > 0 }.getOrDefault(false).not()
            }
            if (blocked.isNotEmpty()) {
                _deleteConsent.value = container.phoneMedia.deleteRequest(blocked.map { it.uri })
            }
            loadPhone(hasPermission = true)
        }
    }

    fun onDeleteConsentResult() {
        _deleteConsent.value = null
        loadPhone(hasPermission = true)
    }
}
