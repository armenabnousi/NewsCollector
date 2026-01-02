package com.example.newscollector.data

import java.time.LocalDateTime
import java.util.UUID

data class Source(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val isRss: Boolean,
    val limit: Int = 10
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
    val id: String, // mapped from canonical_slug or id
    val name: String,
    val architecture: ModelArchitecture,
    val pricing: ModelPricing
)

data class ModelArchitecture(
    val input_modalities: List<String>,
    val output_modalities: List<String>
)

data class ModelPricing(
    val prompt: String,
    val completion: String
)

data class OpenRouterResponse(
    val data: List<OpenRouterModel>
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatResponse(
    val choices: List<ChatChoice>
)

data class ChatChoice(val message: ChatMessage)