package com.github.vermilion10.milea.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_AUTO_DETECT = booleanPreferencesKey("auto_detect_enabled")
    }

    val autoDetectEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_DETECT] ?: false
    }

    suspend fun setAutoDetectEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_DETECT] = enabled
        }
    }
}
