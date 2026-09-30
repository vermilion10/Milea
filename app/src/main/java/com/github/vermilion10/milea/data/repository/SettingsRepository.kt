package com.github.vermilion10.milea.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true
)

data class CurrencySettings(
    val symbol: String = "$",
    val decimals: Int = 2
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_AUTO_DETECT = booleanPreferencesKey("auto_detect_enabled")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val KEY_CURRENCY_DECIMALS = intPreferencesKey("currency_decimals")
    }

    val autoDetectEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_DETECT] ?: false
    }

    val appearance: Flow<AppearanceSettings> = dataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = prefs[KEY_THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[KEY_DYNAMIC_COLOR] ?: true
        )
    }

    val currency: Flow<CurrencySettings> = dataStore.data.map { prefs ->
        CurrencySettings(
            symbol = prefs[KEY_CURRENCY_SYMBOL] ?: "$",
            decimals = prefs[KEY_CURRENCY_DECIMALS] ?: 2
        )
    }

    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_DETECT] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[KEY_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setCurrency(symbol: String, decimals: Int) {
        dataStore.edit {
            it[KEY_CURRENCY_SYMBOL] = symbol
            it[KEY_CURRENCY_DECIMALS] = decimals.coerceIn(0, 3)
        }
    }
}
