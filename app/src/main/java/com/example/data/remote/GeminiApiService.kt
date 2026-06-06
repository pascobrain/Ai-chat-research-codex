package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = "text/plain"
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content? = null
)

@JsonClass(generateAdapter = true)
data class ResearchLink(
    val title: String,
    val url: String,
    val domain: String = "",
    val faviconUrl: String = "",
    val snippet: String = ""
)

interface GeminiApiService {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse

    @POST
    suspend fun generateOpenAICompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAIContentRequest
    ): OpenAIContentResponse
}

@JsonClass(generateAdapter = true)
data class OpenAIContentRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val temperature: Float? = null,
    val max_tokens: Int? = null,
    val stream: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OpenAIMessage(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class OpenAIContentResponse(
    val choices: List<OpenAIChoice>? = null
)

@JsonClass(generateAdapter = true)
data class OpenAIChoice(
    val message: OpenAIMessage? = null
)

object RetrofitClient {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getApiService(customBaseUrl: String? = null): GeminiApiService {
        val baseUrl = if (!customBaseUrl.isNullOrBlank()) {
            if (customBaseUrl.endsWith("/")) customBaseUrl else "$customBaseUrl/"
        } else {
            "https://generativelanguage.googleapis.com/"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}
