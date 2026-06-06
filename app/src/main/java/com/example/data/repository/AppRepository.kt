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
                val model = "gemini-3.5-flash"
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
        try {
            // Read settings
            val endpoint = getSetting("api_endpoint", "https://generativelanguage.googleapis.com/")
            val rawKey = getSetting("api_key", "")
            val apiKey = if (rawKey.isNotBlank()) rawKey else BuildConfig.GEMINI_API_KEY
            val model = getSetting("selected_model", "gemini-3.5-flash")
            val tempStr = getSetting("temperature", "0.7")
            val maxTokensStr = getSetting("max_tokens", "2048")
            val showResearch = getSetting("show_research_links", "true") == "true"
            val apiProtocol = getSetting("api_protocol", "gemini")

            val temperature = tempStr.toFloatOrNull() ?: 0.7f
            val maxTokens = maxTokensStr.toIntOrNull() ?: 2048

            // Formulate request
            val service = RetrofitClient.getApiService(endpoint)

            val customSystemPrompt = getSetting("system_prompt", "")
            val systemRule = if (customSystemPrompt.isNotBlank()) {
                customSystemPrompt
            } else {
                "You are a professional coding and research assistant. " +
                        "Always design and explain things with exceptional technical depth. " +
                        "Support clean markdown. Use bullet lists, bold text, and code blocks with syntax highlighting language tokens (e.g. ```kotlin ... ```). " +
                        "Whenever referencing any concepts, suggest real-world reference links using markdown format: [Title](https://domain.com/path). " +
                        "For example, when discussing Compose, include links such as [Jetpack Compose State](https://developer.android.com/develop/ui/compose/state) to allow research verification."
            }

            val outputText = if (apiProtocol == "openai") {
                val finalEndpoint = if (endpoint.endsWith("/")) "${endpoint}chat/completions" else "$endpoint/chat/completions"
                val authHeader = "Bearer $apiKey"

                val openAIMessages = mutableListOf<com.example.data.remote.OpenAIMessage>()
                
                val fullSystemRule = if (knowledgeContext.isNotBlank()) {
                    "$systemRule Keep this additional reference knowledge in mind:\n$knowledgeContext"
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
                response.choices?.firstOrNull()?.message?.content
                    ?: "No response from OpenAI compatible assistant."
            } else {
                val requestUrl = "v1beta/models/$model:generateContent?key=$apiKey"

                val systemInstruction = if (knowledgeContext.isNotBlank()) {
                    Content(parts = listOf(Part(text = "$systemRule Keep this additional reference knowledge in mind:\n$knowledgeContext")))
                } else {
                    Content(parts = listOf(Part(text = systemRule)))
                }

                val contentsList = mutableListOf<Content>()
                history.forEach { msg ->
                    contentsList.add(Content(parts = listOf(Part(text = msg.content))))
                }
                contentsList.add(Content(parts = listOf(Part(text = prompt))))

                val request = GenerateContentRequest(
                    contents = contentsList,
                    generationConfig = GenerationConfig(
                        temperature = temperature,
                        maxOutputTokens = maxTokens
                    ),
                    systemInstruction = systemInstruction
                )

                val response = service.generateContent(requestUrl, request)
                response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: "No response from AI assistant."
            }

            // Parse research links
            val parsedLinks = if (showResearch) parseMarkdownLinks(outputText) else emptyList()
            val linksJson = try {
                researchAdapter.toJson(parsedLinks)
            } catch (e: Exception) {
                "[]"
            }

            // Save INITIAL empty message role to start typewriter effect stream
            val initialMsg = MessageEntity(
                conversationId = conversationId,
                role = "model",
                content = "",
                isResearchDone = false,
                researchLinksJson = "[]"
            )
            val msgId = appDao.insertMessage(initialMsg)

            // Simulate stream word-by-word with small delay
            val words = outputText.split(" ")
            var currentText = ""
            val batchSize = 3
            var batchIndex = 0
            while (batchIndex < words.size) {
                val batchEnd = (batchIndex + batchSize).coerceAtMost(words.size)
                val chunk = words.subList(batchIndex, batchEnd).joinToString(" ")
                currentText = if (currentText.isEmpty()) chunk else "$currentText $chunk"
                
                val updatedMsg = initialMsg.copy(id = msgId, content = currentText)
                appDao.insertMessage(updatedMsg)
                
                delay(30)
                batchIndex += batchSize
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
            appDao.insertMessage(responseMsg)

            // Update conversation active status
            val existing = appDao.getConversationById(conversationId)
            if (existing != null) {
                appDao.updateConversationMeta(
                    id = conversationId,
                    title = existing.title,
                    lastActiveAt = System.currentTimeMillis(),
                    previewText = if (outputText.length > 60) outputText.substring(0, 57) + "..." else outputText
                )
            }

            Result.success(outputText)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
