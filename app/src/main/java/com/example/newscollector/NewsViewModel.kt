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
    }

    val unifiedNewsList: StateFlow<List<UnifiedNews>> = _unifiedNewsList
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val userPrefs = UserPreferences(application)
    var availableModels = mutableStateListOf<OpenRouterModel>()
    var selectedModel = mutableStateOf<OpenRouterModel?>(null)
    var savedDisplayName = mutableStateOf("None")
    val openRouterToken = userPrefs.openRouterBearerToken

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
        val workRequest = OneTimeWorkRequestBuilder<NewsWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            "refresh_news_task",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
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
        viewModelScope.launch {
            isFetchingModels.value = true
            try {
                val response = ApiClient.api.getModels("Bearer ${ApiClient.BEARER_TOKEN}")

                // 2. FIXED FILTER & CONTAINS (Standard Kotlin versions now used)
                val filtered = response.data.filter { model ->
                    model.architecture.input_modalities.contains("text") &&
                            model.architecture.output_modalities.contains("text")
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
                e.printStackTrace()
            } finally {
                isFetchingModels.value = false
            }
        }
    }

    fun selectAndSaveModel(model: OpenRouterModel) {
        val displayName = "${model.name} (${model.pricing.prompt}, ${model.pricing.completion})"
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