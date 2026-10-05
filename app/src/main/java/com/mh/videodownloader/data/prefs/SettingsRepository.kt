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

/** Where the Library screen reads from. */
enum class LibrarySource { APP_DOWNLOADS, DEVICE_FOLDER }

/** Bundled UI faces. Each one covers Arabic and Latin so the app never mixes two voices. */
enum class AppFont(val key: String, val sample: String) {
    CAIRO("cairo", "كايرو"),
    TAJAWAL("tajawal", "تجوال"),
    READEX("readex", "ريدكس"),
    NOTO_KUFI("noto_kufi", "نوتو كوفي"),
    ALMARAI("almarai", "المراعي"),
    SYSTEM("system", "خط النظام"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val font: AppFont = AppFont.CAIRO,
    val autoUpdateEngine: Boolean = true,
    /** Keep audio playing when the app leaves the screen. Off by default. */
    val backgroundPlayback: Boolean = false,
    val librarySource: LibrarySource = LibrarySource.APP_DOWNLOADS,
    /** SAF tree URI of the folder picked for [LibrarySource.DEVICE_FOLDER]. */
    val libraryFolder: String? = null,
    val lastEngineUpdate: Long = 0L,
    val hasCookies: Boolean = false,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            language = AppLanguage.entries.firstOrNull { it.tag == prefs[KEY_LANGUAGE] }
                ?: AppLanguage.SYSTEM,
            font = AppFont.entries.firstOrNull { it.key == prefs[KEY_FONT] } ?: AppFont.CAIRO,
            autoUpdateEngine = prefs[KEY_AUTO_UPDATE] ?: true,
            backgroundPlayback = prefs[KEY_BACKGROUND_PLAYBACK] ?: false,
            librarySource = prefs[KEY_LIBRARY_SOURCE]
                ?.let { runCatching { LibrarySource.valueOf(it) }.getOrNull() }
                ?: LibrarySource.APP_DOWNLOADS,
            libraryFolder = prefs[KEY_LIBRARY_FOLDER],
            lastEngineUpdate = prefs[KEY_LAST_UPDATE] ?: 0L,
            hasCookies = prefs[KEY_HAS_COOKIES] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.name }

    suspend fun setLanguage(language: AppLanguage) = edit { it[KEY_LANGUAGE] = language.tag }

    suspend fun setFont(font: AppFont) = edit { it[KEY_FONT] = font.key }

    suspend fun setAutoUpdate(enabled: Boolean) = edit { it[KEY_AUTO_UPDATE] = enabled }

    suspend fun setLibrarySource(source: LibrarySource) =
        edit { it[KEY_LIBRARY_SOURCE] = source.name }

    suspend fun setLibraryFolder(treeUri: String) = edit {
        it[KEY_LIBRARY_FOLDER] = treeUri
        it[KEY_LIBRARY_SOURCE] = LibrarySource.DEVICE_FOLDER.name
    }

    suspend fun setBackgroundPlayback(enabled: Boolean) =
        edit { it[KEY_BACKGROUND_PLAYBACK] = enabled }

    suspend fun setLastEngineUpdate(timestamp: Long) = edit { it[KEY_LAST_UPDATE] = timestamp }

    suspend fun setHasCookies(has: Boolean) = edit { it[KEY_HAS_COOKIES] = has }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_FONT = stringPreferencesKey("font")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val KEY_BACKGROUND_PLAYBACK = booleanPreferencesKey("background_playback")
        val KEY_LIBRARY_SOURCE = stringPreferencesKey("library_source")
        val KEY_LIBRARY_FOLDER = stringPreferencesKey("library_folder")
        val KEY_LAST_UPDATE = longPreferencesKey("last_engine_update")
        val KEY_HAS_COOKIES = booleanPreferencesKey("has_cookies")
    }
}
