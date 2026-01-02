package com.example.newscollector.data

import java.time.LocalDateTime
import java.util.UUID
import com.google.gson.annotations.SerializedName

data class Source(
    @SerializedName("id") val id: String = UUID.randomUUID().toString(),
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("isRss") val isRss: Boolean,
    @SerializedName("limit") val limit: Int = 10
)

data class News(
    val title: String,
    val content: String,
    val url: String,
    val date: LocalDateTime,
    val source: Source
)

data class UnifiedNews(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val mainContent: String,
    val publishedDate: LocalDateTime,
    val sources: List<Source>,
    val originalArticles: List<News>,
    val importanceScore: Int = 0
)

data class OpenRouterModel(
    @SerializedName("id") val id: String, // mapped from canonical_slug or id
    @SerializedName("name") val name: String,
    @SerializedName("architecture") val architecture: ModelArchitecture?,
    @SerializedName("pricing") val pricing: ModelPricing?
)

data class ModelArchitecture(
    @SerializedName("input_modalities") val input_modalities: List<String>,
    @SerializedName("output_modalities") val output_modalities: List<String>
)

data class ModelPricing(
    @SerializedName("prompt") val prompt: String?,
    @SerializedName("completion") val completion: String?
)

data class OpenRouterResponse(
    @SerializedName("data") val data: List<OpenRouterModel>
)

data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)

data class ChatResponse(
    @SerializedName("choices") val choices: List<ChatChoice>
)

data class ChatChoice(
    @SerializedName("message") val message: ChatMessage
)