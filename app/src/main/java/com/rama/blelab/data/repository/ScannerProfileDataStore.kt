package com.rama.blelab.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rama.blelab.domain.model.ScannerDeviceProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.scannerProfileDataStore by preferencesDataStore(name = "scanner_profiles")

class ScannerProfileDataStore(private val context: Context) {
    private val profilesKey = stringPreferencesKey("profiles")

    val profiles: Flow<Map<String, ScannerDeviceProfile>> = context.scannerProfileDataStore.data.map { preferences ->
        val json = preferences[profilesKey] ?: return@map emptyMap()
        try {
            Json.decodeFromString<List<ScannerDeviceProfile>>(json).associateBy { it.address }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveProfiles(profiles: Map<String, ScannerDeviceProfile>) {
        context.scannerProfileDataStore.edit { preferences ->
            preferences[profilesKey] = Json.encodeToString(profiles.values.toList())
        }
    }
}
