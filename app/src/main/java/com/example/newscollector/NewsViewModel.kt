package com.example.newscollector

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.newscollector.data.*
import com.example.newscollector.worker.NewsWorker
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.example.newscollector.api.ApiClient
import kotlinx.coroutines.flow.first

class NewsViewModel(application: Application) : AndroidViewModel(application) {
    private val _sources = mutableStateListOf<Source>()
    val sources: List<Source> = _sources

    var isFetchingModels = mutableStateOf(false)

    // Note: In a production app, these should be observed from a Room Database
    // For now, we use a static flow that the Worker can update
    companion object {
        val _unifiedNewsList = MutableStateFlow<List<UnifiedNews>>(emptyList())
        val _isRefreshing = MutableStateFlow(false)
        val errorMessage = mutableStateOf<String?>(null)
    }

    val unifiedNewsList: StateFlow<List<UnifiedNews>> = _unifiedNewsList
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val userPrefs = UserPreferences(application)
    var availableModels = mutableStateListOf<OpenRouterModel>()
    var selectedModel = mutableStateOf<OpenRouterModel?>(null)
    var savedDisplayName = mutableStateOf("None")
    val openRouterToken = userPrefs.openRouterBearerToken
    val errorMessage = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            userPrefs.selectedModelName.collect { name -> savedDisplayName.value = name ?: "None" }
        }
        viewModelScope.launch {
            userPrefs.savedSources.first().let { loaded ->
                _sources.clear()
                _sources.addAll(loaded)
            }
        }
        viewModelScope.launch {
            userPrefs.openRouterBearerToken.collect {
                savedToken ->
                if (!savedToken.isNullOrBlank()) {
                    ApiClient.BEARER_TOKEN = savedToken
                }
            }

        }
    }

    fun refreshNews() {
        if (ApiClient.BEARER_TOKEN.isBlank()) {
            errorMessage.value = "Please enter your OpenRouter Bearer Token in Settings first."
            return
        }
        try {
            val workRequest = OneTimeWorkRequestBuilder<NewsWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "refresh_news_task",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        } catch (e: Exception) {
            val writer = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(writer))
            val fullStackTrace = writer.toString()
            errorMessage.value = "Failed to refresh news. Error:  ${e.javaClass.simpleName} - ${e.message} \n {$fullStackTrace}"
        }
    }

    fun addSource(name: String, url: String, isRss: Boolean, limit: Int) {
        val newSource = Source(name = name, url = url, isRss = isRss, limit = limit)
        _sources.add(newSource)
        viewModelScope.launch { userPrefs.saveSources(_sources.toList()) }
    }

    fun removeSource(source: Source) {
        _sources.remove(source)
        viewModelScope.launch { userPrefs.saveSources(_sources.toList()) }
    }

    fun fetchModels() {
        if (ApiClient.BEARER_TOKEN.isBlank()) {
            errorMessage.value = "Please enter your OpenRouter Bearer Token"
            return
        }
        viewModelScope.launch {
            isFetchingModels.value = true
            try {
                val response = ApiClient.api.getModels("Bearer ${ApiClient.BEARER_TOKEN}")

                // 2. FIXED FILTER & CONTAINS (Standard Kotlin versions now used)
                val filtered = response.data.filter { model ->
                    val inputIsText = model.architecture?.input_modalities?.contains("text") ?: false
                    val outputIsText = model.architecture?.output_modalities?.contains("text") ?: false
                    inputIsText && outputIsText
                }

                availableModels.clear()
                availableModels.addAll(filtered)

                val savedId = userPrefs.selectedModelId.first()
                if (savedId != null) {
                    val previouslySelected = filtered.find {
                        it.id == savedId
                    }
                    if (previouslySelected != null) {
                        selectedModel.value = previouslySelected
                    }
                }
            } catch (e: Exception) {
                val writer = java.io.StringWriter()
                e.printStackTrace(java.io.PrintWriter(writer))
                val fullStackTrace = writer.toString()

                errorMessage.value = "{$fullStackTrace} \nFailed to fetch models. Check your token or internet. Error: ${e.message}"
                e.printStackTrace()
            } finally {
                isFetchingModels.value = false
            }
        }
    }

    fun selectAndSaveModel(model: OpenRouterModel) {
        val displayName = "${model.name} (${model.pricing?.prompt ?: "nan"}, ${model.pricing?.completion ?: "nan"})"
        selectedModel.value = model
        savedDisplayName.value = displayName

        viewModelScope.launch {
            userPrefs.saveSelectedModelId(model.id, displayName)
        }
    }

    fun updateApiToken(newToken: String) {
        viewModelScope.launch {
            userPrefs.saveApiToken(newToken)
        }
    }
}