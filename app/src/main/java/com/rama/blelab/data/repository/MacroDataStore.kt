package com.rama.blelab.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rama.blelab.domain.repository.Macro
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "macros")

class MacroDataStore(private val context: Context) {
    private val macrosKey = stringPreferencesKey("macros_list")

    val macros: Flow<List<Macro>> = context.dataStore.data.map { preferences ->
        val json = preferences[macrosKey] ?: return@map emptyList()
        try {
            Json.decodeFromString<List<Macro>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveMacros(macros: List<Macro>) {
        context.dataStore.edit { preferences ->
            preferences[macrosKey] = Json.encodeToString(macros)
        }
    }
}
