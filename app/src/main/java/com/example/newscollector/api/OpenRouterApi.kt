package com.example.newscollector.api

import com.example.newscollector.data.ChatRequest
import com.example.newscollector.data.ChatResponse
import com.example.newscollector.data.OpenRouterResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApi {
    @GET("api/v1/models") // OpenRouter's model endpoint
    suspend fun getModels(
        @Header("Authorization") token: String
    ): OpenRouterResponse

    @POST("api/v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") token: String,
        @Body request: ChatRequest
    ): ChatResponse
}