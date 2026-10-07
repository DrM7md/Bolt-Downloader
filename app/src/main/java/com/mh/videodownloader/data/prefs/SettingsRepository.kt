package com.mh.videodownloader.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage(val tag: String) { SYSTEM(""), ARABIC("ar"), ENGLISH("en") }

/** What the Library tab shows. */
enum class LibrarySource {
    /** The save folder, as real folders the user can create, delete and move videos between. */
    SAVE_FOLDER,

    /** Every video on the phone, read-only browsing through MediaStore. */
    ALL_PHONE_VIDEOS,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Keep audio playing when the app leaves the screen. Off by default. */
    val backgroundPlayback: Boolean = false,
    val librarySource: LibrarySource = LibrarySource.SAVE_FOLDER,
    /**
     * SAF tree URI of the save folder. Null means "not granted yet": downloads still land in
     * Download/VideoDownloader through MediaStore, but the library cannot manage folders until
     * the user grants access once.
     */
    val saveFolder: String? = null,
    val lastEngineUpdate: Long = 0L,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            language = AppLanguage.entries.firstOrNull { it.tag == prefs[KEY_LANGUAGE] }
                ?: AppLanguage.SYSTEM,
            backgroundPlayback = prefs[KEY_BACKGROUND_PLAYBACK] ?: false,
            librarySource = prefs[KEY_LIBRARY_SOURCE]
                ?.let { runCatching { LibrarySource.valueOf(it) }.getOrNull() }
                ?: LibrarySource.SAVE_FOLDER,
            saveFolder = prefs[KEY_SAVE_FOLDER],
            lastEngineUpdate = prefs[KEY_LAST_UPDATE] ?: 0L,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.name }

    suspend fun setLanguage(language: AppLanguage) = edit { it[KEY_LANGUAGE] = language.tag }

    suspend fun setLibrarySource(source: LibrarySource) =
        edit { it[KEY_LIBRARY_SOURCE] = source.name }

    suspend fun setSaveFolder(treeUri: String) = edit { it[KEY_SAVE_FOLDER] = treeUri }

    suspend fun clearSaveFolder() = edit { it.remove(KEY_SAVE_FOLDER) }

    suspend fun setBackgroundPlayback(enabled: Boolean) =
        edit { it[KEY_BACKGROUND_PLAYBACK] = enabled }

    suspend fun setLastEngineUpdate(timestamp: Long) = edit { it[KEY_LAST_UPDATE] = timestamp }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        // New keys rather than reusing the 2.0 ones: their values meant something else
        // ("app downloads" / "device folder"), and silently reinterpreting them would surprise.
        val KEY_LIBRARY_SOURCE = stringPreferencesKey("library_view")
        val KEY_SAVE_FOLDER = stringPreferencesKey("save_folder")
        val KEY_LAST_UPDATE = longPreferencesKey("last_engine_update")
    }
}
