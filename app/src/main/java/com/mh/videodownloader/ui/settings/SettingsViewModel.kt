package com.mh.videodownloader.ui.settings

import android.app.Application
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mh.videodownloader.App
import com.mh.videodownloader.R
import com.mh.videodownloader.data.engine.YtDlpEngine
import com.mh.videodownloader.data.prefs.AppLanguage
import com.mh.videodownloader.data.prefs.AppSettings
import com.mh.videodownloader.data.prefs.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as App).container

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _engineVersion = MutableStateFlow<String?>(null)
    val engineVersion: StateFlow<String?> = _engineVersion.asStateFlow()

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private val _message = MutableStateFlow<Int?>(null)
    val message: StateFlow<Int?> = _message.asStateFlow()

    init {
        refreshEngineVersion()
    }

    private fun refreshEngineVersion() {
        viewModelScope.launch {
            container.engine.ensureInitialized()
            _engineVersion.value = container.engine.version()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settings.setDynamicColor(enabled) }
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

    fun setAutoUpdate(enabled: Boolean) {
        viewModelScope.launch { container.settings.setAutoUpdate(enabled) }
    }

    fun updateEngine() {
        if (_isUpdating.value) return
        viewModelScope.launch {
            _isUpdating.value = true
            val result = container.engine.update()
            _isUpdating.value = false
            _message.value = when (result) {
                is YtDlpEngine.UpdateResult.Updated -> R.string.engine_updated
                YtDlpEngine.UpdateResult.AlreadyUpToDate -> R.string.engine_up_to_date
                is YtDlpEngine.UpdateResult.Failed -> R.string.engine_update_failed_short
            }
            if (result !is YtDlpEngine.UpdateResult.Failed) {
                container.settings.setLastEngineUpdate(System.currentTimeMillis())
            }
            _engineVersion.value = container.engine.version()
        }
    }

    fun importCookies(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        container.cookiesFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("cannot open cookies file")
                }.isSuccess
            }
            container.settings.setHasCookies(ok && container.cookiesFile.length() > 0)
            _message.value = if (ok) R.string.cookies_imported else R.string.cannot_open_file
        }
    }

    fun removeCookies() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.cookiesFile.delete() }
            container.settings.setHasCookies(false)
            _message.value = R.string.cookies_removed
        }
    }

    fun consumeMessage() {
        _message.value = null
    }
}
