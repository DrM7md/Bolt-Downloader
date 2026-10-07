package com.mh.videodownloader.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.data.prefs.AppLanguage
import com.mh.videodownloader.data.prefs.AppSettings
import com.mh.videodownloader.data.prefs.LibrarySource
import com.mh.videodownloader.data.prefs.ThemeMode
import com.mh.videodownloader.data.storage.DownloadSaver
import com.mh.videodownloader.data.storage.storagePath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Readable path of the save folder, e.g. "Download/VideoDownloader". */
    private val _saveFolderPath = MutableStateFlow<String?>(null)
    val saveFolderPath: StateFlow<String?> = _saveFolderPath.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    fun refreshSaveFolder(treeUri: String?) {
        viewModelScope.launch {
            val tree = treeUri?.toUri()
            _saveFolderPath.value = tree
                ?.takeIf { container.folderStore.displayName(it) != null }
                ?.let { storagePath(container.folderStore.rootId(it)) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setThemeMode(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            container.settings.setLanguage(language)
            // The framework owns the per-app locale; DataStore only remembers what to show
            // as selected in this screen.
            AppCompatDelegate.setApplicationLocales(
                if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language.tag)
            )
        }
    }

    fun setBackgroundPlayback(enabled: Boolean) {
        viewModelScope.launch { container.settings.setBackgroundPlayback(enabled) }
    }

    fun setLibrarySource(source: LibrarySource) {
        viewModelScope.launch { container.settings.setLibrarySource(source) }
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
     * grant the permission dies with this process and saving falls back to the default folder.
     */
    fun onSaveFolderPicked(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModelScope.launch { container.settings.setSaveFolder(uri.toString()) }
        refreshSaveFolder(uri.toString())
    }

    fun consumeMessage() {
        _message.value = null
    }
}
