package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.AppDao
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.KnowledgeEntryEntity
import com.example.data.local.AppSettingsEntity
import com.example.data.remote.Content
import com.example.data.remote.GenerateContentRequest
import com.example.data.remote.GenerationConfig
import com.example.data.remote.Part
import com.example.data.remote.ResearchLink
import com.example.data.remote.RetrofitClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.URL
import java.util.regex.Pattern

class AppRepository(private val appDao: AppDao) {

    // --- Local DB Streams ---
    val allConversations: Flow<List<ConversationEntity>> = appDao.getAllConversations()
    val allKnowledgeEntries: Flow<List<KnowledgeEntryEntity>> = appDao.getAllKnowledgeEntries()
    val allSettings: Flow<List<AppSettingsEntity>> = appDao.getAllSettingsFlow()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val researchListType = Types.newParameterizedType(List::class.java, ResearchLink::class.java)
    private val researchAdapter = moshi.adapter<List<ResearchLink>>(researchListType)

    // --- Conversation actions ---
    suspend fun createConversation(title: String, previewText: String = ""): Long = withContext(Dispatchers.IO) {
        val conv = ConversationEntity(title = title, previewText = previewText)
        appDao.insertConversation(conv)
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteConversationById(id)
        appDao.deleteMessagesForConversation(id)
    }

    suspend fun updateConversation(id: Long, title: String) = withContext(Dispatchers.IO) {
        val existing = appDao.getConversationById(id)
        if (existing != null) {
            appDao.updateConversationMeta(id, title, System.currentTimeMillis(), existing.previewText)
        }
    }

    // --- Messages actions ---
    fun getMessagesForConversation(conversationId: Long): Flow<List<MessageEntity>> {
        return appDao.getMessagesForConversation(conversationId)
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteMessageById(id)
    }

    suspend fun insertMessage(message: MessageEntity) = withContext(Dispatchers.IO) {
        appDao.insertMessage(message)
        // Update conversation's last active and preview
        val preview = if (message.content.length > 60) message.content.substring(0, 57) + "..." else message.content
        val existing = appDao.getConversationById(message.conversationId)
        if (existing != null) {
            appDao.updateConversationMeta(
                id = message.conversationId,
                title = existing.title,
                lastActiveAt = System.currentTimeMillis(),
                previewText = preview
            )
        }
    }

    // --- Knowledge base actions ---
    suspend fun insertKnowledgeEntry(title: String, url: String, content: String) = withContext(Dispatchers.IO) {
        appDao.insertKnowledgeEntry(KnowledgeEntryEntity(title = title, url = url, content = content))
    }

    suspend fun deleteKnowledgeEntry(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteKnowledgeEntryById(id)
    }

    fun searchKnowledge(query: String): Flow<List<KnowledgeEntryEntity>> {
        return if (query.isBlank()) allKnowledgeEntries else appDao.searchKnowledgeEntries(query)
    }

    // --- Settings actions ---
    suspend fun saveSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        appDao.insertSetting(AppSettingsEntity(key, value))
    }

    suspend fun getSetting(key: String, defaultValue: String): String = withContext(Dispatchers.IO) {
        appDao.getSettingByKey(key)?.value ?: defaultValue
    }

    // --- Endpoint validation ---
    suspend fun testEndpoint(endpoint: String, key: String, protocol: String = "gemini"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val service = RetrofitClient.getApiService(endpoint)
            if (protocol == "openai") {
                val finalEndpoint = if (endpoint.endsWith("/")) "${endpoint}chat/completions" else "$endpoint/chat/completions"
                val request = com.example.data.remote.OpenAIContentRequest(
                    model = "gpt-3.5-turbo",
                    messages = listOf(com.example.data.remote.OpenAIMessage(role = "user", content = "ping")),
                    max_tokens = 5
                )
                val response = service.generateOpenAICompletion(finalEndpoint, "Bearer $key", request)
                if (response.choices != null) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Invalid OpenAI compatible response format."))
                }
            } else {
                val model = "gemini-3.1-flash-lite-preview"
                val requestUrl = "v1beta/models/$model:generateContent?key=$key"
                val testRequest = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = "ping")))),
                    generationConfig = GenerationConfig(maxOutputTokens = 5)
                )
                val response = service.generateContent(requestUrl, testRequest)
                if (response.candidates != null) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Invalid API response format."))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- AI Request dispatch ---
    suspend fun queryAI(
        prompt: String,
        conversationId: Long,
        history: List<MessageEntity>,
        knowledgeContext: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        com.example.util.DiagnosticLogger.logRequest(prompt)
        try {
            // Read settings
            val endpoint = getSetting("api_endpoint", "https://generativelanguage.googleapis.com/")
            val rawKey = getSetting("api_key", "")
            val apiKey = if (rawKey.isNotBlank()) rawKey else BuildConfig.GEMINI_API_KEY
            val model = getSetting("selected_model", "gemini-3.1-flash-lite-preview")
            val tempStr = getSetting("temperature", "0.7")
            val maxTokensStr = getSetting("max_tokens", "2048")
            val showResearch = getSetting("show_research_links", "true") == "true"
            val apiProtocol = getSetting("api_protocol", "gemini")

            val temperature = tempStr.toFloatOrNull() ?: 0.7f
            val maxTokens = maxTokensStr.toIntOrNull() ?: 2048

            val tavilyKey = getSetting("tavily_api_key", "")
            val braveKey = getSetting("brave_api_key", "")
            val activeSearchProvider = getSetting("active_search_provider", "auto")
            
            var augmentedKnowledgeContext = knowledgeContext

            val useTavily = when (activeSearchProvider) {
                "tavily" -> tavilyKey.isNotBlank()
                "brave" -> false
                "google" -> false
                "none" -> false
                else -> tavilyKey.isNotBlank() // "auto" uses tavily first
            }

            val useBrave = when (activeSearchProvider) {
                "tavily" -> false
                "wikipedia" -> false
                "brave" -> braveKey.isNotBlank()
                "google" -> false
                "none" -> false
                else -> !useTavily && braveKey.isNotBlank() // "auto" uses brave if tavily not set
            }

            val useWikipedia = when (activeSearchProvider) {
                "wikipedia" -> true
                "none" -> false
                else -> !useTavily && !useBrave // "auto" uses wikipedia if tavily and brave not set
            }

            val useGoogle = activeSearchProvider == "google" || (activeSearchProvider == "auto" && !useTavily && !useBrave && !useWikipedia)

            if (useTavily) {
                try {
                    val searchService = RetrofitClient.getApiService("https://api.tavily.com/")
                    val searchReq = com.example.data.remote.TavilySearchRequest(
                        api_key = tavilyKey,
                        query = prompt
                    )
                    val searchRes = searchService.searchTavily(searchReq)
                    val contexts = searchRes.results?.joinToString("\n---\n") { "${it.title} (${it.url}):\n${it.content}" } ?: ""
                    if (contexts.isNotBlank()) {
                        augmentedKnowledgeContext = if (augmentedKnowledgeContext.isBlank()) "Web Search Results:\n$contexts" else "$augmentedKnowledgeContext\n\nWeb Search Results:\n$contexts"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (useBrave) {
                try {
                    val searchService = RetrofitClient.getApiService("https://api.search.brave.com/")
                    val searchRes = searchService.searchBrave(apiKey = braveKey, query = prompt)
                    val contexts = searchRes.web?.results?.joinToString("\n---\n") { "${it.title} (${it.url}):\n${it.description}" } ?: ""
                    if (contexts.isNotBlank()) {
                        augmentedKnowledgeContext = if (augmentedKnowledgeContext.isBlank()) "Web Search Results:\n$contexts" else "$augmentedKnowledgeContext\n\nWeb Search Results:\n$contexts"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (useWikipedia) {
                try {
                    val searchService = RetrofitClient.getApiService("https://en.wikipedia.org/")
                    val searchRes = searchService.searchWikipedia(query = prompt)
                    val contexts = searchRes.query?.search?.take(3)?.joinToString("\n---\n") { 
                        val cleanSnippet = it.snippet?.replace(Regex("<[^>]*>"), "") ?: ""
                        "${it.title} (https://en.wikipedia.org/wiki/${it.title?.replace(" ", "_")}):\n$cleanSnippet" 
                    } ?: ""
                    if (contexts.isNotBlank()) {
                        augmentedKnowledgeContext = if (augmentedKnowledgeContext.isBlank()) "Wikipedia Search Results:\n$contexts" else "$augmentedKnowledgeContext\n\nWikipedia Search Results:\n$contexts"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Formulate request
            val service = RetrofitClient.getApiService(endpoint)

            val customSystemPrompt = getSetting("system_prompt", "")
            val systemRule = if (customSystemPrompt.isNotBlank()) {
                customSystemPrompt
            } else {
                "You are a professional coding and research assistant. " +
                        "Always design and explain things with exceptional technical depth. " +
                        "Support clean markdown. Use bullet lists, bold text, and code blocks with syntax highlighting language tokens (e.g. ```tsx ... ```). " +
                        "Whenever referencing any concepts, suggest real-world reference links using markdown format: [Title](https://domain.com/path). " +
                        "For example, when discussing Compose, include links such as [Jetpack Compose State](https://developer.android.com/develop/ui/compose/state) to allow research verification."
            }

            com.example.util.DiagnosticLogger.logStreamingStart()

            var accumulatedText = ""
            val initialMsg = MessageEntity(
                conversationId = conversationId,
                role = "model",
                content = "",
                isResearchDone = false,
                researchLinksJson = "[]"
            )
            val msgId = if (conversationId != -1L) {
                appDao.insertMessage(initialMsg)
            } else {
                -1L
            }

            try {
                if (apiProtocol == "openai") {
                    val finalEndpoint = if (endpoint.endsWith("/")) "${endpoint}chat/completions" else "$endpoint/chat/completions"
                    val authHeader = "Bearer $apiKey"

                    val openAIMessages = mutableListOf<com.example.data.remote.OpenAIMessage>()
                    val fullSystemRule = if (augmentedKnowledgeContext.isNotBlank()) {
                        "$systemRule Keep this additional reference knowledge in mind:\n$augmentedKnowledgeContext"
                    } else {
                        systemRule
                    }

                    if (fullSystemRule.isNotBlank()) {
                        openAIMessages.add(com.example.data.remote.OpenAIMessage(role = "system", content = fullSystemRule))
                    }

                    history.forEach { msg ->
                        val rawRole = if (msg.role == "model") "assistant" else "user"
                        openAIMessages.add(com.example.data.remote.OpenAIMessage(role = rawRole, content = msg.content))
                    }

                    openAIMessages.add(com.example.data.remote.OpenAIMessage(role = "user", content = prompt))

                    val request = com.example.data.remote.OpenAIContentRequest(
                        model = model,
                        messages = openAIMessages,
                        temperature = temperature,
                        max_tokens = maxTokens,
                        stream = true
                    )

                    val responseBody = service.generateOpenAICompletionStream(finalEndpoint, authHeader, request)
                    responseBody.byteStream().bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val textChunk = extractTextFromOpenAIChunk(line!!)
                            if (textChunk != null && textChunk.isNotEmpty()) {
                                accumulatedText += textChunk
                                if (conversationId != -1L) {
                                    val updatedMsg = initialMsg.copy(id = msgId, content = accumulatedText)
                                    appDao.insertMessage(updatedMsg)
                                }
                                com.example.util.DiagnosticLogger.logStreamChunk(textChunk)
                            }
                        }
                    }
                } else {
                    val requestUrl = "v1beta/models/$model:streamGenerateContent?key=$apiKey"

                    val systemInstruction = if (augmentedKnowledgeContext.isNotBlank()) {
                        Content(parts = listOf(Part(text = "$systemRule Keep this additional reference knowledge in mind:\n$augmentedKnowledgeContext")))
                    } else {
                        Content(parts = listOf(Part(text = systemRule)))
                    }

                    val contentsList = mutableListOf<Content>()
                    history.forEach { msg ->
                        contentsList.add(Content(parts = listOf(Part(text = msg.content))))
                    }
                    contentsList.add(Content(parts = listOf(Part(text = prompt))))

                    val geminiThinkingLevel = getSetting("gemini_thinking_level", "none")
                    val thinkingConfig = if (geminiThinkingLevel != "none") {
                        com.example.data.remote.ThinkingConfig(thinkingLevel = geminiThinkingLevel)
                    } else null

                    val toolsList = if (apiProtocol == "gemini" && useGoogle) {
                        listOf(com.example.data.remote.Tool(googleSearch = com.example.data.remote.GoogleSearch())) // Trigger Google Search
                    } else null

                    val request = GenerateContentRequest(
                        contents = contentsList,
                        generationConfig = GenerationConfig(
                            temperature = temperature,
                            maxOutputTokens = maxTokens,
                            thinkingConfig = thinkingConfig
                        ),
                        systemInstruction = systemInstruction,
                        tools = toolsList
                    )

                    val responseBody = service.generateContentStream(requestUrl, request)
                    responseBody.byteStream().bufferedReader().use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            val textChunk = extractTextFromGeminiChunk(line!!)
                            if (textChunk != null && textChunk.isNotEmpty()) {
                                accumulatedText += textChunk
                                if (conversationId != -1L) {
                                    val updatedMsg = initialMsg.copy(id = msgId, content = accumulatedText)
                                    appDao.insertMessage(updatedMsg)
                                }
                                com.example.util.DiagnosticLogger.logStreamChunk(textChunk)
                            }
                        }
                    }
                }
            } catch (streamError: Exception) {
                streamError.printStackTrace()
                com.example.util.DiagnosticLogger.logError("Streaming failed: ${streamError.localizedMessage}. Falling back to standard generation.")
                if (accumulatedText.isBlank()) {
                    if (apiProtocol == "openai") {
                        val finalEndpoint = if (endpoint.endsWith("/")) "${endpoint}chat/completions" else "$endpoint/chat/completions"
                        val authHeader = "Bearer $apiKey"

                        val openAIMessages = mutableListOf<com.example.data.remote.OpenAIMessage>()
                        val fullSystemRule = if (augmentedKnowledgeContext.isNotBlank()) {
                            "$systemRule Keep this additional reference knowledge in mind:\n$augmentedKnowledgeContext"
                        } else {
                            systemRule
                        }

                        if (fullSystemRule.isNotBlank()) {
                            openAIMessages.add(com.example.data.remote.OpenAIMessage(role = "system", content = fullSystemRule))
                        }

                        history.forEach { msg ->
                            val rawRole = if (msg.role == "model") "assistant" else "user"
                            openAIMessages.add(com.example.data.remote.OpenAIMessage(role = rawRole, content = msg.content))
                        }

                        openAIMessages.add(com.example.data.remote.OpenAIMessage(role = "user", content = prompt))

                        val request = com.example.data.remote.OpenAIContentRequest(
                            model = model,
                            messages = openAIMessages,
                            temperature = temperature,
                            max_tokens = maxTokens
                        )

                        val response = service.generateOpenAICompletion(finalEndpoint, authHeader, request)
                        accumulatedText = response.choices?.firstOrNull()?.message?.content ?: ""
                    } else {
                        val requestUrl = "v1beta/models/$model:generateContent?key=$apiKey"

                        val systemInstruction = if (augmentedKnowledgeContext.isNotBlank()) {
                            Content(parts = listOf(Part(text = "$systemRule Keep this additional reference knowledge in mind:\n$augmentedKnowledgeContext")))
                        } else {
                            Content(parts = listOf(Part(text = systemRule)))
                        }

                        val contentsList = mutableListOf<Content>()
                        history.forEach { msg ->
                            contentsList.add(Content(parts = listOf(Part(text = msg.content))))
                        }
                        contentsList.add(Content(parts = listOf(Part(text = prompt))))

                        val geminiThinkingLevel = getSetting("gemini_thinking_level", "none")
                        val thinkingConfig = if (geminiThinkingLevel != "none") {
                            com.example.data.remote.ThinkingConfig(thinkingLevel = geminiThinkingLevel)
                        } else null

                        val toolsList = if (apiProtocol == "gemini" && useGoogle) {
                            listOf(com.example.data.remote.Tool(googleSearch = com.example.data.remote.GoogleSearch())) // Trigger Google Search
                        } else null

                        val request = GenerateContentRequest(
                            contents = contentsList,
                            generationConfig = GenerationConfig(
                                temperature = temperature,
                                maxOutputTokens = maxTokens,
                                thinkingConfig = thinkingConfig
                            ),
                            systemInstruction = systemInstruction,
                            tools = toolsList
                        )

                        val response = service.generateContent(requestUrl, request)
                        accumulatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                    }
                }
            }

            val outputText = accumulatedText.ifBlank { "No response from AI assistant." }

            // Parse research links
            val parsedLinks = if (showResearch) parseMarkdownLinks(outputText) else emptyList()
            val linksJson = try {
                researchAdapter.toJson(parsedLinks)
            } catch (e: Exception) {
                "[]"
            }

            // Finally, write exact final output with parsed reference links
            val responseMsg = MessageEntity(
                id = msgId,
                conversationId = conversationId,
                role = "model",
                content = outputText,
                isResearchDone = parsedLinks.isNotEmpty(),
                researchLinksJson = linksJson
            )
            if (conversationId != -1L) {
                appDao.insertMessage(responseMsg)
            }

            // Update conversation active status
            if (conversationId != -1L) {
                val existing = appDao.getConversationById(conversationId)
                if (existing != null) {
                    appDao.updateConversationMeta(
                        id = conversationId,
                        title = existing.title,
                        lastActiveAt = System.currentTimeMillis(),
                        previewText = if (outputText.length > 60) outputText.substring(0, 57) + "..." else outputText
                    )
                }
            }
            
            com.example.util.DiagnosticLogger.logComplete()

            Result.success(outputText)
        } catch (e: Exception) {
            com.example.util.DiagnosticLogger.logError(e.localizedMessage ?: "Unknown error")
            Result.failure(e)
        }
    }

    private fun extractTextFromGeminiChunk(line: String): String? {
        val pattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val matcher = pattern.matcher(line)
        if (matcher.find()) {
            val rawGroup = matcher.group(1) ?: return null
            return unescapeJsonString(rawGroup)
        }
        return null
    }

    private fun extractTextFromOpenAIChunk(line: String): String? {
        val pattern = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val matcher = pattern.matcher(line)
        if (matcher.find()) {
            val rawGroup = matcher.group(1) ?: return null
            return unescapeJsonString(rawGroup)
        }
        return null
    }

    private fun unescapeJsonString(input: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < input.length) {
            val c = input[i]
            if (c == '\\' && i + 1 < input.length) {
                val next = input[i + 1]
                when (next) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'u' -> {
                        if (i + 5 < input.length) {
                            try {
                                val code = input.substring(i + 2, i + 6).toInt(16)
                                sb.append(code.toChar())
                                i += 4
                            } catch (e: Exception) {
                                sb.append("\\u")
                            }
                        } else {
                            sb.append("\\u")
                        }
                    }
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    private fun parseMarkdownLinks(text: String): List<ResearchLink> {
        val links = mutableListOf<ResearchLink>()
        val pattern = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)")
        val matcher = pattern.matcher(text)

        while (matcher.find()) {
            val title = matcher.group(1) ?: "Research Link"
            val url = matcher.group(2) ?: ""
            if (url.startsWith("http")) {
                val host = try {
                    val uri = URL(url)
                    uri.host ?: ""
                } catch (e: Exception) {
                    ""
                }
                val cleanHost = host.replace("www.", "")
                val favicon = "https://www.google.com/s2/favicons?sz=64&domain=$cleanHost"
                links.add(
                    ResearchLink(
                        title = title,
                        url = url,
                        domain = cleanHost,
                        faviconUrl = favicon,
                        snippet = "Verify source in research databases or official docs."
                    )
                )
            }
            // Limit to max 4 reference cards for elegant display
            if (links.size >= 4) break
        }
        return links
    }
}
