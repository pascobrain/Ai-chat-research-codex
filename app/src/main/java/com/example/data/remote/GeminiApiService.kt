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
    val systemInstruction: Content? = null,
    val tools: List<Tool>? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>,
    val role: String? = null
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val functionCall: FunctionCall? = null,
    val functionResponse: FunctionResponse? = null
)

@JsonClass(generateAdapter = true)
data class FunctionCall(
    val name: String,
    val args: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class FunctionResponse(
    val name: String,
    val response: Map<String, String>? = null
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val temperature: Float? = null,
    val maxOutputTokens: Int? = null,
    val responseMimeType: String? = "text/plain",
    val thinkingConfig: ThinkingConfig? = null
)

@JsonClass(generateAdapter = true)
data class ThinkingConfig(
    val thinkingLevel: String
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>? = null
)

@JsonClass(generateAdapter = true)
data class Tool(
    val googleSearch: GoogleSearch? = null, // Just needs to be present for basic search grounding
    val googleSearchRetrieval: GoogleSearchRetrieval? = null,
    val functionDeclarations: List<FunctionDeclaration>? = null
)

@JsonClass(generateAdapter = true)
data class FunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: Schema? = null
)

@JsonClass(generateAdapter = true)
data class Schema(
    val type: String, // e.g., "OBJECT"
    val properties: Map<String, SchemaProperty>? = null,
    val required: List<String>? = null
)

@JsonClass(generateAdapter = true)
data class SchemaProperty(
    val type: String, // e.g., "STRING"
    val description: String? = null
)

@JsonClass(generateAdapter = true)
class GoogleSearch

@JsonClass(generateAdapter = true)
data class GoogleSearchRetrieval(
    val dynamicRetrievalConfig: DynamicRetrievalConfig? = null
)

@JsonClass(generateAdapter = true)
data class DynamicRetrievalConfig(
    val dynamicThreshold: Float? = null,
    val mode: String? = "MODE_DYNAMIC"
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
    @Streaming
    suspend fun generateContentStream(
        @Url url: String,
        @Body request: GenerateContentRequest
    ): okhttp3.ResponseBody

    @POST
    suspend fun generateOpenAICompletion(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAIContentRequest
    ): OpenAIContentResponse

    @POST
    @Streaming
    suspend fun generateOpenAICompletionStream(
        @Url url: String,
        @Header("Authorization") authHeader: String,
        @Body request: OpenAIContentRequest
    ): okhttp3.ResponseBody

    @POST("https://api.tavily.com/search")
    suspend fun searchTavily(
        @Body request: TavilySearchRequest
    ): TavilySearchResponse

    @GET("https://api.search.brave.com/res/v1/web/search")
    suspend fun searchBrave(
        @Header("Accept") acceptHeader: String = "application/json",
        @Header("Accept-Encoding") acceptEncoding: String = "gzip",
        @Header("X-Subscription-Token") apiKey: String,
        @Query("q") query: String
    ): BraveSearchResponse

    @GET("https://en.wikipedia.org/w/api.php?action=query&list=search&utf8=&format=json")
    suspend fun searchWikipedia(
        @Query("srsearch") query: String
    ): WikipediaSearchResponse
}

@JsonClass(generateAdapter = true)
data class WikipediaSearchResponse(
    val query: WikipediaQuery? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaQuery(
    val search: List<WikipediaResult>? = null
)

@JsonClass(generateAdapter = true)
data class WikipediaResult(
    val title: String? = null,
    val snippet: String? = null
)

@JsonClass(generateAdapter = true)
data class TavilySearchRequest(
    val api_key: String,
    val query: String,
    val search_depth: String = "basic",
    val include_answer: Boolean = false,
    val max_results: Int = 3
)

@JsonClass(generateAdapter = true)
data class TavilySearchResponse(
    val results: List<TavilyResult>? = null
)

@JsonClass(generateAdapter = true)
data class TavilyResult(
    val title: String? = null,
    val url: String? = null,
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class BraveSearchResponse(
    val web: BraveWeb? = null
)

@JsonClass(generateAdapter = true)
data class BraveWeb(
    val results: List<BraveResult>? = null
)

@JsonClass(generateAdapter = true)
data class BraveResult(
    val title: String? = null,
    val url: String? = null,
    val description: String? = null
)

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
    val role: String? = null,
    val content: String? = null
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
    private val moshi: Moshi by lazy {
        val builder = Moshi.Builder()
        try {
            builder.addLast(KotlinJsonAdapterFactory())
        } catch (t: Throwable) {
            android.util.Log.w("RetrofitClient", "KotlinJsonAdapterFactory not available, reflection fallback skipped", t)
        }
        builder.build()
    }

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
