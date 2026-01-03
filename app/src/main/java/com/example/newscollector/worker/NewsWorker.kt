package com.example.newscollector.worker

import android.content.Context
import android.util.Log
import androidx.compose.ui.input.key.type
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.newscollector.NewsViewModel
import com.example.newscollector.api.ApiClient
import com.example.newscollector.data.*
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.LocalDateTime
import kotlin.text.contains
import kotlin.text.substringBeforeLast

class NewsWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val userPrefs = UserPreferences(appContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (ApiClient.BEARER_TOKEN.isBlank()) return@withContext Result.failure()
        try {
            NewsViewModel._isRefreshing.value = true

            val modelId = userPrefs.selectedModelId.first() ?: return@withContext Result.failure()
            val sources = userPrefs.savedSources.first()
            val allExtractedNews = mutableListOf<News>()

            // 1. Scraping Loop
            sources.forEach { source ->
                try {
                    val doc = Jsoup.connect(source.url).get()
                    val pageText = doc.body().text()
                    val chunks = pageText.chunked(5000)

                    chunks.forEach { chunk ->
                        val currentCount = allExtractedNews.count { it.source == source }
                        if (currentCount < source.limit) {
                            val extracted = callLLMToExtractNews(chunk, source, modelId)
                            allExtractedNews.addAll(extracted.take(source.limit - currentCount))
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NewsWorker", "Error scraping ${source.name}", e)
                }
            }

            // 2. Unification
            if (allExtractedNews.isNotEmpty()) {
                val unified = unifyNewsWithLLM(allExtractedNews, modelId)
                NewsViewModel._unifiedNewsList.value = unified
            }

            NewsViewModel._isRefreshing.value = false
            Result.success()
        } catch (e: Exception) {
            NewsViewModel._isRefreshing.value = false
            val writer = java.io.StringWriter()
            e.printStackTrace(java.io.PrintWriter(writer))
            val fullStackTrace = writer.toString()
            NewsViewModel.errorMessage.value = "error: ${e.message}\n${fullStackTrace}"
            Log.e("NewsWorker", "Error in NewsWorker: ${e.message}", e)
            Result.failure()
        }
    }

    private suspend fun callLLMToExtractNews(text: String, source: Source, modelId: String): List<News> {
        val prompt = "Extract headlines/summaries as JSON: [{\"title\":\"..\",\"summary\":\"..\"}] from: $text"
        val request = ChatRequest(model = modelId, messages = listOf(ChatMessage("user", prompt)))
        return try {
            val response =
                ApiClient.api.getChatCompletion("Bearer ${ApiClient.BEARER_TOKEN}", request)
            var json = response.choices.firstOrNull()?.message?.content ?: ""
            json = cleanJson(json)

            // Use a concrete Array class reference. This is safe for R8/ProGuard.
            val rawArray = Gson().fromJson(json, Array<ExtractedNewsDTO>::class.java)

            return rawArray.map {
                News(
                    it.title ?: "",
                    it.summary ?: "",
                    source.url,
                    LocalDateTime.now(),
                    source
                )
            }
        } catch (e: retrofit2.HttpException) {
            val errorJson = e.response()?.errorBody()?.string()
            // Log.e("NewsWorker", "LLM Error: $errorJson")
            emptyList()
        }
        catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun unifyNewsWithLLM(allNews: List<News>, modelId: String): List<UnifiedNews> {
        val listText = allNews.mapIndexed { i, n -> "ID: $i | Title: ${n.title}" }.joinToString("\n")
        val prompt = "Group these into events. Return JSON: [{\"title\":\"..\",\"summary\":\"..\",\"ids\":[0,1],\"importance\":8}]. Data: $listText"

        val request = ChatRequest(model = modelId, messages = listOf(ChatMessage("user", prompt)))

        return try {
            val response = ApiClient.api.getChatCompletion("Bearer ${ApiClient.BEARER_TOKEN}", request)
            var json = cleanJson(response.choices.firstOrNull()?.message?.content ?: "")

            // Use the concrete DTO array
            val rawArray = Gson().fromJson(json, Array<UnifiedNewsDTO>::class.java)

            return rawArray.map { group ->
                val ids = group.ids ?: emptyList()
                val matching = ids.mapNotNull { allNews.getOrNull(it) }

                UnifiedNews(
                    title = group.title ?: "Untitled Event",
                    mainContent = group.summary ?: "",
                    publishedDate = LocalDateTime.now(),
                    sources = matching.map { it.source }.distinct(),
                    originalArticles = matching,
                    importanceScore = group.importance ?: 0
                )
            }.sortedByDescending { it.importanceScore }
        } catch (e: Exception) {
            Log.e("NewsWorker", "Unification Step Failed: ${e.message}")
            // Fallback: If unification fails, return articles as individual events
            allNews.map {
                UnifiedNews(
                    title = it.title,
                    mainContent = it.content,
                    publishedDate = it.date,
                    sources = listOf(it.source),
                    originalArticles = listOf(it),
                    importanceScore = 0
                )
            }
        }
    }

    private fun cleanJson(raw: String): String {
        var clean = raw.trim()
        if (clean.contains("json")) {clean = clean.substringAfter("json").substringBeforeLast("```")}
        else if (clean.contains("```")) {clean = clean.substringAfter("```").substringBeforeLast("```")}
        return clean.trim()
    }
    }

private data class ExtractedNewsDTO(
    @SerializedName("title") val title: String?,
    @SerializedName("summary") val summary: String?
)
private data class UnifiedNewsDTO(
    @SerializedName("title") val title: String?,
    @SerializedName("summary") val summary: String?,
    @SerializedName("ids") val ids: List<Int>?,
    @SerializedName("importance") val importance: Int?
)