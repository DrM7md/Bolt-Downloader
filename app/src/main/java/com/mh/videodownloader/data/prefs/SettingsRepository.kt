package com.mh.videodownloader.data.prefs

import android.content.Context
import android.os.Build
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

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val autoUpdateEngine: Boolean = true,
    val lastEngineUpdate: Long = 0L,
    val hasCookies: Boolean = false,
) {
    val supportsDynamicColor: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[KEY_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC] ?: false,
            language = AppLanguage.entries.firstOrNull { it.tag == prefs[KEY_LANGUAGE] }
                ?: AppLanguage.SYSTEM,
            autoUpdateEngine = prefs[KEY_AUTO_UPDATE] ?: true,
            lastEngineUpdate = prefs[KEY_LAST_UPDATE] ?: 0L,
            hasCookies = prefs[KEY_HAS_COOKIES] ?: false,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[KEY_DYNAMIC] = enabled }

    suspend fun setLanguage(language: AppLanguage) = edit { it[KEY_LANGUAGE] = language.tag }

    suspend fun setAutoUpdate(enabled: Boolean) = edit { it[KEY_AUTO_UPDATE] = enabled }

    suspend fun setLastEngineUpdate(timestamp: Long) = edit { it[KEY_LAST_UPDATE] = timestamp }

    suspend fun setHasCookies(has: Boolean) = edit { it[KEY_HAS_COOKIES] = has }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val KEY_LANGUAGE = stringPreferencesKey("language")
        val KEY_AUTO_UPDATE = booleanPreferencesKey("auto_update")
        val KEY_LAST_UPDATE = longPreferencesKey("last_engine_update")
        val KEY_HAS_COOKIES = booleanPreferencesKey("has_cookies")
    }
}
