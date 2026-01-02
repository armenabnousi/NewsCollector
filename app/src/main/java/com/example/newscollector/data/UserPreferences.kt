package com.example.newscollector.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create the DataStore instance
private val Context.dataStore by preferencesDataStore(name = "settings")


class UserPreferences(private val context: Context) {

    companion object {
        // Key for storing the model ID (the canonical_slug)
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val SELECTED_MODEL_NAME = stringPreferencesKey("selected_model_name")
        val SOURCES_KEY = stringPreferencesKey("saved_sources")
        val OPENROUTER_TOKEN = stringPreferencesKey("openrouter_bearer_token")
    }

    // Read the saved model ID as a Flow
    val selectedModelId: Flow<String?> = context.dataStore.data
        .map { it[SELECTED_MODEL_ID] }
    val selectedModelName: Flow<String?> = context.dataStore.data
        .map { it[SELECTED_MODEL_NAME] }
    val openRouterBearerToken: Flow<String?> = context.dataStore.data
        .map{ it[OPENROUTER_TOKEN] }
    val savedSources: Flow<List<Source>> = context.dataStore.data.map {
            preferences ->
        val json = preferences[SOURCES_KEY] ?: "[]"
        val type = object: com.google.gson.reflect.TypeToken<List<Source>>() {}.type
        com.google.gson.Gson().fromJson(json, type)
    }

    // Save the model ID
    suspend fun saveSelectedModelId(modelId: String, modelName: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MODEL_ID] = modelId
            preferences[SELECTED_MODEL_NAME] = modelName
        }
    }

    suspend fun saveSources(sources: List<Source>) {
        val json = com.google.gson.Gson().toJson(sources)
        context.dataStore.edit { preferences ->
            preferences[SOURCES_KEY] = json
        }
    }

    suspend fun saveApiToken(token: String)
    {
        context.dataStore.edit { preferences ->
            preferences[OPENROUTER_TOKEN] = token
        }
    }
}