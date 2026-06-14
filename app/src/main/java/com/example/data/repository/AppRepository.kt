package com.example.data.repository

import com.example.BuildConfig
import com.example.util.getRealOrEmpty
import com.example.util.isRealKey
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.URL
import java.util.regex.Pattern

class AppRepository(private val appDao: AppDao) {

    // --- Local DB Streams ---
    val allConversations: Flow<List<ConversationEntity>> = appDao.getAllConversations()
    val allKnowledgeEntries: Flow<List<KnowledgeEntryEntity>> = appDao.getAllKnowledgeEntries()
    val allSettings: Flow<List<AppSettingsEntity>> = appDao.getAllSettingsFlow()

    private val moshi: Moshi by lazy {
        val builder = Moshi.Builder()
        try {
            builder.addLast(KotlinJsonAdapterFactory())
        } catch (t: Throwable) {
            android.util.Log.w("AppRepository", "KotlinJsonAdapterFactory not available, reflection fallback skipped", t)
        }
        builder.build()
    }
    private val researchListType = Types.newParameterizedType(List::class.java, ResearchLink::class.java)
    private val researchAdapter = moshi.adapter<List<ResearchLink>>(researchListType)
    private val geminiResponseAdapter by lazy {
        moshi.adapter(com.example.data.remote.GenerateContentResponse::class.java)
    }

    // --- Conversation actions ---
    suspend fun createConversation(title: String, previewText: String = ""): Long = withContext(Dispatchers.IO) {
        val conv = ConversationEntity(title = title, previewText = previewText)
        appDao.insertConversation(conv)
    }

    suspend fun deleteConversation(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteConversationById(id)
        appDao.deleteMessagesForConversation(id)
    }

    suspend fun clearConversationMessages(id: Long) = withContext(Dispatchers.IO) {
        appDao.deleteMessagesForConversation(id)
        val existing = appDao.getConversationById(id)
        if (existing != null) {
            appDao.updateConversationMeta(id, existing.title, System.currentTimeMillis(), "Empty history")
        }
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

    fun getMessagesForConversationPaged(conversationId: Long, limit: Int): Flow<List<MessageEntity>> {
        return appDao.getMessagesForConversationPaged(conversationId, limit).map { it.reversed() }
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
    suspend fun testEndpoint(endpoint: String, key: String, protocol: String = "gemini", modelName: String = "gemini-3.5-flash"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val service = RetrofitClient.getApiService(endpoint)
            val finalKey = if (key.isNotBlank()) {
                key
            } else {
                if (protocol == "openai" || endpoint.contains("groq.com", ignoreCase = true) || endpoint.contains("nvidia", ignoreCase = true)) {
                    val groq = BuildConfig.GROQ_API_KEY.getRealOrEmpty()
                    if (groq.isNotBlank()) groq else BuildConfig.GEMINI_API_KEY.getRealOrEmpty()
                } else {
                    BuildConfig.GEMINI_API_KEY.getRealOrEmpty()
                }
            }
            if (protocol == "openai") {
                val finalEndpoint = if (endpoint.endsWith("/")) "${endpoint}chat/completions" else "$endpoint/chat/completions"
                val request = com.example.data.remote.OpenAIContentRequest(
                    model = if (modelName.isBlank() || modelName == "gemini-3.1-flash-lite-preview" || modelName == "gemini-3.5-flash") "openai/gpt-oss-20b" else modelName,
                    messages = listOf(com.example.data.remote.OpenAIMessage(role = "user", content = "ping")),
                    max_tokens = 5
                )
                val response = service.generateOpenAICompletion(finalEndpoint, "Bearer $finalKey", request)
                if (response.choices != null) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Invalid OpenAI compatible response format."))
                }
            } else {
                val model = if (modelName.isBlank()) "gemini-3.5-flash" else modelName
                val requestUrl = "v1beta/models/$model:generateContent?key=$finalKey"
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
            Result.failure(wrapWithFriendlyError(e))
        }
    }

    // --- AI Request dispatch ---
    suspend fun queryAI(
        prompt: String,
        conversationId: Long,
        history: List<MessageEntity>,
        knowledgeContext: String = "",
        onStatusChange: ((String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val queryStartTime = System.currentTimeMillis()
        com.example.util.DiagnosticLogger.logRequest(prompt)
        try {
            // Read settings
            val endpoint = getSetting("api_endpoint", "https://generativelanguage.googleapis.com/")
            val apiProtocol = getSetting("api_protocol", "gemini")
            val rawKey = when {
                apiProtocol == "gemini" -> getSetting("api_key_gemini", "")
                endpoint.contains("nvidia.com", ignoreCase = true) || endpoint.contains("nvidia", ignoreCase = true) -> getSetting("api_key_nvidia", "")
                endpoint.contains("groq.com", ignoreCase = true) -> getSetting("api_key_groq", "")
                else -> getSetting("api_key_custom", "")
            }.ifBlank { getSetting("api_key", "") }

            val apiKey = if (rawKey.isNotBlank()) {
                rawKey
            } else {
                val isNvidia = endpoint.contains("nvidia", ignoreCase = true)
                val isGroq = endpoint.contains("groq.com", ignoreCase = true)
                
                if (apiProtocol == "openai") {
                    BuildConfig.GROQ_API_KEY.getRealOrEmpty().ifBlank { BuildConfig.GEMINI_API_KEY.getRealOrEmpty() }
                } else if (isGroq) {
                    BuildConfig.GROQ_API_KEY.getRealOrEmpty().ifBlank { BuildConfig.GEMINI_API_KEY.getRealOrEmpty() }
                } else if (isNvidia) {
                    BuildConfig.NVIDIA_API_KEY.getRealOrEmpty().ifBlank { BuildConfig.GEMINI_API_KEY.getRealOrEmpty() }
                } else {
                    BuildConfig.GEMINI_API_KEY.getRealOrEmpty()
                }
            }
            val model = getSetting("selected_model", "gemini-3.1-flash-lite-preview")
            val tempStr = getSetting("temperature", "0.7")
            val maxTokensStr = getSetting("max_tokens", "2048")
            val showResearch = getSetting("show_research_links", "true") == "true"

            val temperature = tempStr.toFloatOrNull() ?: 0.7f
            val maxTokens = maxTokensStr.toIntOrNull() ?: 2048

            val rawTavilyKey = getSetting("tavily_api_key", "")
            val tavilyKey = if (rawTavilyKey.isNotBlank()) rawTavilyKey else BuildConfig.TAVILY_API_KEY

            val rawE2bKey = getSetting("e2b_api_key", "")
            val e2bApiKey = if (rawE2bKey.isNotBlank()) rawE2bKey else BuildConfig.E2B_API_KEY.getRealOrEmpty()

            val rawBraveKey = getSetting("brave_api_key", "")
            val braveKey = if (rawBraveKey.isNotBlank()) rawBraveKey else BuildConfig.BRAVE_API_KEY

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
                onStatusChange?.invoke("SEARCHING_TAVILY")
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
                onStatusChange?.invoke("SEARCHING_BRAVE")
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
                onStatusChange?.invoke("SEARCHING_WIKIPEDIA")
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
            } else if (useGoogle && apiProtocol == "gemini") {
                onStatusChange?.invoke("SEARCHING_GOOGLE")
            }

            // Formulate request
            val service = RetrofitClient.getApiService(endpoint)

            val customSystemPrompt = getSetting("system_prompt", "")
            val systemRule = customSystemPrompt.ifBlank {
                "You are a professional coding and research assistant. " +
                "Always design and explain things with exceptional technical depth. " +
                "Support clean markdown. Use bullet lists, bold text, and code blocks with syntax highlighting language tokens."
            }

            com.example.util.DiagnosticLogger.logStreamingStart()
            
            val isProviderOpenAiCompatible = apiProtocol == "openai" || apiProtocol == "nvidia" || apiProtocol == "groq"

            var accumulatedText = ""
            val initialMsg = MessageEntity(
                conversationId = conversationId,
                role = "model",
                content = "",
                isResearchDone = false,
                researchLinksJson = "[]",
                provider = apiProtocol
            )
            val msgId = if (conversationId != -1L) {
                appDao.insertMessage(initialMsg)
            } else {
                -1L
            }

            onStatusChange?.invoke("THINKING")
            try {
                if (isProviderOpenAiCompatible) {
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

                    val requestModel = if (model.isBlank() || model.startsWith("gemini")) {
                        "openai/gpt-oss-20b"
                    } else {
                        model
                    }

                    val request = com.example.data.remote.OpenAIContentRequest(
                        model = requestModel,
                        messages = openAIMessages,
                        temperature = temperature,
                        max_tokens = maxTokens,
                        stream = true
                    )

                    val responseBody = service.generateOpenAICompletionStream(finalEndpoint, authHeader, request)
                    responseBody.byteStream().bufferedReader().use { reader ->
                        var line: String?
                        var streamStarted = false
                        while (reader.readLine().also { line = it } != null) {
                            val textChunk = extractTextFromOpenAIChunk(line!!)
                            if (textChunk != null && textChunk.isNotEmpty()) {
                                if (!streamStarted) {
                                    streamStarted = true
                                    onStatusChange?.invoke("GENERATING")
                                }
                                accumulatedText += textChunk
                                if (conversationId != -1L) {
                                    val updatedMsg = initialMsg.copy(id = msgId, content = accumulatedText)
                                    appDao.insertMessage(updatedMsg)
                                }
                                com.example.util.DiagnosticLogger.logStreamChunk(textChunk)
                            }
                        }
                    }
                } else if (apiProtocol == "gemini" && (tavilyKey.isNotBlank() || e2bApiKey.isNotBlank())) {
                    val requestModel = if (model.isBlank() || !model.startsWith("gemini")) {
                        "gemini-3.5-flash"
                    } else {
                        model
                    }
                    val requestUrlNonStream = "v1beta/models/$requestModel:generateContent?key=$apiKey"

                    val systemInstruction = if (augmentedKnowledgeContext.isNotBlank()) {
                        Content(parts = listOf(Part(text = "$systemRule Keep this additional reference knowledge in mind:\n$augmentedKnowledgeContext")))
                    } else {
                        Content(parts = listOf(Part(text = systemRule)))
                    }

                    val contentsList = mutableListOf<Content>()
                    history.forEach { msg ->
                        val turnRole = if (msg.role == "model") "model" else "user"
                        contentsList.add(Content(parts = listOf(Part(text = msg.content)), role = turnRole))
                    }
                    contentsList.add(Content(parts = listOf(Part(text = prompt)), role = "user"))

                    val functionDeclarations = mutableListOf<com.example.data.remote.FunctionDeclaration>()
                    if (tavilyKey.isNotBlank()) {
                        functionDeclarations.add(
                            com.example.data.remote.FunctionDeclaration(
                                name = "search_tavily",
                                description = "Search the web using the Tavily Search API. Returns raw web snippet contexts. Use this when the user needs real-time, grounded facts, news, or up-to-date web lookups.",
                                parameters = com.example.data.remote.Schema(
                                    type = "OBJECT",
                                    properties = mapOf(
                                        "query" to com.example.data.remote.SchemaProperty(
                                            type = "STRING",
                                            description = "The specific text search query to run"
                                        )
                                    ),
                                    required = listOf("query")
                                )
                            )
                        )
                    }

                    if (e2bApiKey.isNotBlank()) {
                        functionDeclarations.add(
                            com.example.data.remote.FunctionDeclaration(
                                name = "execute_code",
                                description = "Execute raw python or javascript code inside a secure sandboxed environment using the E2B SDK. Returns stdout, stderr, and exit code. Use this for calculations, processing data, verifying logic, or running user scripts.",
                                parameters = com.example.data.remote.Schema(
                                    type = "OBJECT",
                                    properties = mapOf(
                                        "language" to com.example.data.remote.SchemaProperty(
                                            type = "STRING",
                                            description = "Language of script, must be 'python', 'javascript', or 'typescript'"
                                        ),
                                        "code" to com.example.data.remote.SchemaProperty(
                                            type = "STRING",
                                            description = "The raw source code block to execute"
                                        )
                                    ),
                                    required = listOf("language", "code")
                                )
                            )
                        )
                    }

                    val toolsList = if (functionDeclarations.isNotEmpty()) {
                        listOf(com.example.data.remote.Tool(functionDeclarations = functionDeclarations))
                    } else null

                    val geminiThinkingLevel = getSetting("gemini_thinking_level", "none")
                    val thinkingConfig = if (geminiThinkingLevel != "none") {
                        com.example.data.remote.ThinkingConfig(thinkingLevel = geminiThinkingLevel)
                    } else null

                    var loopCount = 0
                    var finished = false

                    while (loopCount < 5 && !finished) {
                        loopCount++
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

                        val response = service.generateContent(requestUrlNonStream, request)
                        val candidate = response.candidates?.firstOrNull()
                        val responseContent = candidate?.content
                        val firstPart = responseContent?.parts?.firstOrNull()

                        if (firstPart != null) {
                            if (firstPart.functionCall != null) {
                                val call = firstPart.functionCall
                                val funcName = call.name
                                val args = call.args ?: emptyMap()

                                // Model's call turn
                                contentsList.add(com.example.data.remote.Content(
                                    parts = listOf(com.example.data.remote.Part(functionCall = call)),
                                    role = "model"
                                ))

                                if (funcName == "search_tavily") {
                                    val searchQuery = args["query"] ?: ""
                                    onStatusChange?.invoke("SEARCHING_TAVILY")
                                    var searchResultStr = ""
                                    try {
                                        val searchService = RetrofitClient.getApiService("https://api.tavily.com/")
                                        val searchReq = com.example.data.remote.TavilySearchRequest(
                                            api_key = tavilyKey,
                                            query = searchQuery
                                        )
                                        val searchRes = searchService.searchTavily(searchReq)
                                        searchResultStr = searchRes.results?.joinToString("\n---\n") { "${it.title} (${it.url}):\n${it.content}" } ?: "No search results found."
                                    } catch (e: Exception) {
                                        searchResultStr = "Error during Tavily search: ${e.message}"
                                    }

                                    contentsList.add(com.example.data.remote.Content(
                                        parts = listOf(com.example.data.remote.Part(
                                            functionResponse = com.example.data.remote.FunctionResponse(
                                                name = "search_tavily",
                                                response = mapOf("result" to searchResultStr)
                                            )
                                        )),
                                        role = "user"
                                    ))

                                    if (conversationId != -1L) {
                                        accumulatedText += "\n\n*[System: Executing Tavily search for \"$searchQuery\"]*\n\n"
                                        val updatedMsg = initialMsg.copy(id = msgId, content = accumulatedText)
                                        appDao.insertMessage(updatedMsg)
                                    }
                                } else if (funcName == "execute_code") {
                                    val codeToRun = args["code"] ?: ""
                                    val lang = args["language"] ?: "python"
                                    onStatusChange?.invoke("RUNNING_CODE")
                                    var execResultStr = ""
                                    try {
                                        val res = com.example.util.E2BCodeExecutor.executeCode(
                                            language = lang,
                                            code = codeToRun,
                                            apiKey = e2bApiKey
                                        )
                                        execResultStr = if (res.isSuccess) {
                                            "STDOUT:\n${res.stdout}\n"
                                        } else {
                                            "STDOUT:\n${res.stdout}\nSTDERR:\n${res.stderr}\nERROR:\n${res.error ?: ""}\n"
                                        }
                                    } catch (e: Exception) {
                                        execResultStr = "Error executing code via E2B: " + (e.localizedMessage ?: e.javaClass.simpleName) //
                                    }

                                    contentsList.add(com.example.data.remote.Content(
                                        parts = listOf(com.example.data.remote.Part(
                                            functionResponse = com.example.data.remote.FunctionResponse(
                                                name = "execute_code",
                                                response = mapOf("result" to execResultStr)
                                            )
                                        )),
                                        role = "user"
                                    ))

                                    if (conversationId != -1L) {
                                        accumulatedText += "\n\n*[System: Running $lang script in sandboxed E2B environment]*\n\n"
                                        val updatedMsg = initialMsg.copy(id = msgId, content = accumulatedText)
                                        appDao.insertMessage(updatedMsg)
                                    }
                                }
                            } else {
                                val text = firstPart.text ?: ""
                                accumulatedText = text
                                if (conversationId != -1L) {
                                    val updatedMsg = initialMsg.copy(id = msgId, content = accumulatedText)
                                    appDao.insertMessage(updatedMsg)
                                }
                                finished = true
                            }
                        } else {
                            finished = true
                        }
                    }
                } else {
                    val requestModel = if (model.isBlank() || !model.startsWith("gemini")) {
                        "gemini-3.5-flash"
                    } else {
                        model
                    }
                    val requestUrl = "v1beta/models/$requestModel:streamGenerateContent?alt=sse&key=$apiKey"

                    val systemInstruction = if (augmentedKnowledgeContext.isNotBlank()) {
                        Content(parts = listOf(Part(text = "$systemRule Keep this additional reference knowledge in mind:\n$augmentedKnowledgeContext")))
                    } else {
                        Content(parts = listOf(Part(text = systemRule)))
                    }

                    val contentsList = mutableListOf<Content>()
                    history.forEach { msg ->
                        val turnRole = if (msg.role == "model") "model" else "user"
                        contentsList.add(Content(parts = listOf(Part(text = msg.content)), role = turnRole))
                    }
                    contentsList.add(Content(parts = listOf(Part(text = prompt)), role = "user"))

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
                        var streamStarted = false
                        while (reader.readLine().also { line = it } != null) {
                            val textChunk = extractTextFromGeminiChunk(line!!)
                            if (textChunk != null && textChunk.isNotEmpty()) {
                                if (!streamStarted) {
                                    streamStarted = true
                                    onStatusChange?.invoke("GENERATING")
                                }
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
                            val turnRole = if (msg.role == "model") "model" else "user"
                            contentsList.add(Content(parts = listOf(Part(text = msg.content)), role = turnRole))
                        }
                        contentsList.add(Content(parts = listOf(Part(text = prompt)), role = "user"))

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
            val elapsedQueryTime = System.currentTimeMillis() - queryStartTime
            val responseMsg = MessageEntity(
                id = msgId,
                conversationId = conversationId,
                role = "model",
                content = outputText,
                isResearchDone = parsedLinks.isNotEmpty(),
                researchLinksJson = linksJson,
                provider = apiProtocol,
                latencyMs = elapsedQueryTime
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
            val wrapped = wrapWithFriendlyError(e)
            com.example.util.DiagnosticLogger.logError(wrapped.localizedMessage ?: "Unknown error")
            Result.failure(wrapped)
        }
    }

    private fun wrapWithFriendlyError(e: Exception): Exception {
        val baseMessage = e.localizedMessage ?: e.message ?: "API-Verbindung fehlgeschlagen"
        val errorBody = if (e is retrofit2.HttpException) {
            try {
                e.response()?.errorBody()?.string() ?: ""
            } catch (ex: Exception) {
                ""
            }
        } else ""

        val fullText = "$baseMessage | $errorBody"
        
        val friendlyMessage = when {
            fullText.contains("invalid auth provider token", ignoreCase = true) -> {
                "401 Invalid Auth Provider Token: Authentifizierung am Endpoint fehlgeschlagen.\n\n" +
                "Dies tritt auf, wenn der API-Schlüssel für deinen gewählten Provider (z. B. Gemini, Groq, NVIDIA oder OpenRouter) ungültig, abgelaufen oder falsch eingetragen ist.\n\n" +
                "Lösung: Bitte überprüfe deine eingetragenen API-Schlüssel in den AI Provider Settings und stelle sicher, dass kein Standard-Platzhalter (z. B. MY_GEMINI_API_KEY) verwendet wird."
            }
            e is retrofit2.HttpException && e.code() == 401 -> {
                "401 Unauthorized: Zugriff verweigert.\n\n" +
                "Der Server hat die Anfrage abgelehnt, weil der übergebene API-Schlüssel nicht gültig ist.\n\n" +
                "Lösung: Bitte wechsle zu den Einstellungen und hinterlege einen passenden, funktionierenden API-Schlüssel für diesen Provider."
            }
            e is retrofit2.HttpException && e.code() == 404 -> {
                "404 Not Found: Das ausgewählte Modell wurde auf diesem Server nicht gefunden.\n\n" +
                "Lösung: Wähle in den Einstellungen einen kompatiblen Modellnamen aus (z. B. gemini-3.5-flash)."
            }
            else -> {
                if (errorBody.isNotBlank()) {
                    "$baseMessage (Error Body: $errorBody)"
                } else {
                    baseMessage
                }
            }
        }
        return Exception(friendlyMessage, e)
    }

    private fun extractTextFromGeminiChunk(line: String): String? {
        val trimmed = line.trim()
        val jsonStr = when {
            trimmed.startsWith("data: ") -> trimmed.substring(6)
            trimmed.startsWith("{") || trimmed.startsWith("[") -> trimmed
            else -> trimmed
        }
        try {
            val response = geminiResponseAdapter.fromJson(jsonStr)
            val text = response?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (text != null) return text
        } catch (e: Exception) {
            // fallback to regex pattern
        }

        val pattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val matcher = pattern.matcher(jsonStr)
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
