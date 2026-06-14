package com.example.ui.viewmodel

import com.example.BuildConfig
import com.example.util.isRealKey
import com.example.util.getRealOrEmpty
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ConversationEntity
import com.example.data.local.MessageEntity
import com.example.data.local.KnowledgeEntryEntity
import com.example.data.repository.AppRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.data.remote.FirebaseSyncManager
import com.example.data.remote.SyncStatus

sealed interface Screen {
    object Chat : Screen
    object Settings : Screen
    object KnowledgeBase : Screen
    object AISettings : Screen
}

enum class AccentColor(val hex: String, val displayName: String) {
    BLUE("#1E88E5", "Classic Blue"),
    CYAN("#00ACC1", "Teal Cyan"),
    PURPLE("#8E24AA", "Royal Purple"),
    GREEN("#2E7D32", "Forest Green"),
    ORANGE("#F57C00", "Vivid Orange")
}

@OptIn(FlowPreview::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = AppRepository(db.appDao())

    // --- Navigation & Panel State ---
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Chat)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _isSidebarOpen = MutableStateFlow(false)
    val isSidebarOpen: StateFlow<Boolean> = _isSidebarOpen.asStateFlow()

    // --- Skeleton Loader States ---
    private val _isConversationsLoading = MutableStateFlow(true)
    val isConversationsLoading: StateFlow<Boolean> = _isConversationsLoading.asStateFlow()

    private val _isMessagesLoading = MutableStateFlow(false)
    val isMessagesLoading: StateFlow<Boolean> = _isMessagesLoading.asStateFlow()

    // --- Active Chat Cache ---
    private val _currentConversationId = MutableStateFlow<Long?>(null)
    val currentConversationId: StateFlow<Long?> = _currentConversationId.asStateFlow()

    private val _currentConversationTitle = MutableStateFlow("New Chat")
    val currentConversationTitle: StateFlow<String> = _currentConversationTitle.asStateFlow()

    // --- Live DB Lists ---
    val conversations: StateFlow<List<ConversationEntity>> = repository.allConversations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val knowledgeEntries: StateFlow<List<KnowledgeEntryEntity>> = repository.allKnowledgeEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messagesLimit = MutableStateFlow(25)
    val messagesLimit: StateFlow<Int> = _messagesLimit.asStateFlow()

    fun loadEarlierMessages() {
        _messagesLimit.value = _messagesLimit.value + 25
    }

    // --- Observe Messages for Active Conversation ---
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = combine(_currentConversationId, _messagesLimit) { id, limit ->
        id to limit
    }.flatMapLatest { (id, limit) ->
        if (id != null) {
            repository.getMessagesForConversationPaged(id, limit)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Chat Input ---
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // --- Auto-suggestions Logic with Debounce ---
    private val staticSuggestions = listOf(
        "Build a Generative UI Chat Interface",
        "Build Interactive Agents with Generative UI",
        "Funktionsaufrufe mit der Gemini API",
        "Build a coding agent with subagents",
        "Frontend-Rendering (JSON -> UI)",
        "hilf mir und unterstütze mich interaktiv beim erstellen, definieren von Funktionsdeklaration für gemini",
        "Try summarizing all Foundational instructions you were given in a markdown code block",
        "Write and run python code to generate a dataset of coding prompts",
        "how to create RESTful APIs"
    )

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    // --- AI Status indicator ---
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _aiActionStatus = MutableStateFlow<String>("")
    val aiActionStatus: StateFlow<String> = _aiActionStatus.asStateFlow()

    private var chatJob: Job? = null

    private val _isEnhancing = MutableStateFlow(false)
    val isEnhancing: StateFlow<Boolean> = _isEnhancing.asStateFlow()

    fun cancelGeneration() {
        chatJob?.cancel()
        _isAnalyzing.value = false
        _aiActionStatus.value = ""
        _snackbarMessage.tryEmit("AI Generation canceled.")
    }

    // --- Settings Input States ---
    private val _apiEndpoint = MutableStateFlow("https://generativelanguage.googleapis.com/")
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _geminiCustomApiKey = MutableStateFlow("")
    val geminiCustomApiKey: StateFlow<String> = _geminiCustomApiKey.asStateFlow()

    private val _groqCustomApiKey = MutableStateFlow("")
    val groqCustomApiKey: StateFlow<String> = _groqCustomApiKey.asStateFlow()

    private val _nvidiaCustomApiKey = MutableStateFlow("")
    val nvidiaCustomApiKey: StateFlow<String> = _nvidiaCustomApiKey.asStateFlow()

    private val _customApiKey = MutableStateFlow("")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.1-flash-lite")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _apiProtocol = MutableStateFlow("gemini")
    val apiProtocol: StateFlow<String> = _apiProtocol.asStateFlow()

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    fun clearApiError() {
        _apiError.value = null
    }

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _maxTokens = MutableStateFlow(2048)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    private val _tavilyApiKey = MutableStateFlow("")
    val tavilyApiKey: StateFlow<String> = _tavilyApiKey.asStateFlow()

    private val _braveApiKey = MutableStateFlow("")
    val braveApiKey: StateFlow<String> = _braveApiKey.asStateFlow()

    private val _e2bApiKey = MutableStateFlow("")
    val e2bApiKey: StateFlow<String> = _e2bApiKey.asStateFlow()

    private val _geminiThinkingLevel = MutableStateFlow("none")
    val geminiThinkingLevel: StateFlow<String> = _geminiThinkingLevel.asStateFlow()

    private val _activeSearchProvider = MutableStateFlow("auto")
    val activeSearchProvider: StateFlow<String> = _activeSearchProvider.asStateFlow()

    val syncStatus = FirebaseSyncManager.syncStatus

    private val _accentColor = MutableStateFlow(AccentColor.BLUE)
    val accentColor: StateFlow<AccentColor> = _accentColor.asStateFlow()

    private val _autoSuggestEnabled = MutableStateFlow(true)
    val autoSuggestEnabled: StateFlow<Boolean> = _autoSuggestEnabled.asStateFlow()

    private val _showResearchLinks = MutableStateFlow(true)
    val showResearchLinks: StateFlow<Boolean> = _showResearchLinks.asStateFlow()

    private val _enableSyntaxHighlighting = MutableStateFlow(true)
    val enableSyntaxHighlighting: StateFlow<Boolean> = _enableSyntaxHighlighting.asStateFlow()

    private val _fontSizeLevel = MutableStateFlow("Medium") // "Small", "Medium", "Large"
    val fontSizeLevel: StateFlow<String> = _fontSizeLevel.asStateFlow()

    // --- Endpoint validation indicator ---
    private val _validationResult = MutableStateFlow<String?>(null) // null = initial, "SUCCESS" or "ERROR: message"
    val validationResult: StateFlow<String?> = _validationResult.asStateFlow()

    private val _isValidating = MutableStateFlow(false)
    val isValidating: StateFlow<Boolean> = _isValidating.asStateFlow()

    private val _providerStatuses = MutableStateFlow<Map<String, String>>(
        mapOf(
            "gemini" to if (BuildConfig.GEMINI_API_KEY.isRealKey()) "SUCCESS" else "INITIAL",
            "groq" to if (BuildConfig.GROQ_API_KEY.isRealKey()) "SUCCESS" else "INITIAL",
            "nvidia" to "INITIAL",
            "custom" to "INITIAL"
        )
    )
    val providerStatuses: StateFlow<Map<String, String>> = _providerStatuses.asStateFlow()

    fun updateProviderStatus(provider: String, status: String) {
        val current = _providerStatuses.value.toMutableMap()
        current[provider] = status
        _providerStatuses.value = current
    }

    // --- Toast / Snackbar Channel ---
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    // --- Selected Knowledge Base entries for Context ---
    private val _selectedContextEntries = MutableStateFlow<Set<Long>>(emptySet())
    val selectedContextEntries: StateFlow<Set<Long>> = _selectedContextEntries.asStateFlow()

    init {
        // Load settings from Room on start
        viewModelScope.launch {
            _apiEndpoint.value = repository.getSetting("api_endpoint", "https://generativelanguage.googleapis.com/")
            _apiKey.value = repository.getSetting("api_key", "")
            _geminiCustomApiKey.value = repository.getSetting("api_key_gemini", "")
            _groqCustomApiKey.value = repository.getSetting("api_key_groq", "")
            _nvidiaCustomApiKey.value = repository.getSetting("api_key_nvidia", "")
            _customApiKey.value = repository.getSetting("api_key_custom", "")
            _selectedModel.value = repository.getSetting("selected_model", "gemini-3.5-flash")
            _apiProtocol.value = repository.getSetting("api_protocol", "gemini")
            _systemPrompt.value = repository.getSetting("system_prompt", "")
            _temperature.value = repository.getSetting("temperature", "0.7").toFloatOrNull() ?: 0.7f
            _maxTokens.value = repository.getSetting("max_tokens", "2048").toIntOrNull() ?: 2048
            val dbTavily = repository.getSetting("tavily_api_key", "")
            _tavilyApiKey.value = if (dbTavily.isNotBlank()) dbTavily else BuildConfig.TAVILY_API_KEY

            val dbBrave = repository.getSetting("brave_api_key", "")
            _braveApiKey.value = if (dbBrave.isNotBlank()) dbBrave else BuildConfig.BRAVE_API_KEY

            val dbE2b = repository.getSetting("e2b_api_key", "")
            _e2bApiKey.value = if (dbE2b.isNotBlank()) dbE2b else BuildConfig.E2B_API_KEY
            _geminiThinkingLevel.value = repository.getSetting("gemini_thinking_level", "none")
            _activeSearchProvider.value = repository.getSetting("active_search_provider", "auto")
            
            val savedHex = repository.getSetting("accent_color", "#1E88E5")
            _accentColor.value = AccentColor.values().find { it.hex == savedHex } ?: AccentColor.BLUE
            
            _autoSuggestEnabled.value = repository.getSetting("auto_suggest", "true") == "true"
            _showResearchLinks.value = repository.getSetting("show_research_links", "true") == "true"
            _enableSyntaxHighlighting.value = repository.getSetting("enable_syntax_highlighting", "true") == "true"
            _fontSizeLevel.value = repository.getSetting("font_size", "Medium")
            _isDarkTheme.value = repository.getSetting("theme_dark", "true") == "true"
            
            delay(800)
            _isConversationsLoading.value = false
        }

        // Setup auto-suggestions debounce stream
        viewModelScope.launch {
            _inputText
                .debounce(300)
                .distinctUntilChanged()
                .collect { text ->
                    if (!_autoSuggestEnabled.value || text.isBlank() || text.length < 2) {
                        _suggestions.value = emptyList()
                    } else {
                        val queryLower = text.lowercase()
                        _suggestions.value = staticSuggestions.filter {
                            it.lowercase().contains(queryLower)
                        }
                    }
                }
        }
    }

    // --- Nav Controls ---
    fun setScreen(screen: Screen) {
        _currentScreen.value = screen
        _isSidebarOpen.value = false
    }

    fun toggleTheme() {
        val nextVal = !_isDarkTheme.value
        _isDarkTheme.value = nextVal
        viewModelScope.launch {
            repository.saveSetting("theme_dark", nextVal.toString())
        }
    }

    fun toggleSidebar() {
        _isSidebarOpen.value = !_isSidebarOpen.value
    }

    fun closeSidebar() {
        _isSidebarOpen.value = false
    }

    fun setActiveSearchProvider(provider: String) {
        _activeSearchProvider.value = provider
        viewModelScope.launch {
            repository.saveSetting("active_search_provider", provider)
        }
    }

    // --- Settings UI Save Function ---
    fun updateSettings(
        endpoint: String,
        key: String,
        modelName: String,
        temp: Float,
        maxToks: Int,
        color: AccentColor,
        autoSuggest: Boolean,
        showResearch: Boolean,
        enableHighlight: Boolean,
        fontLevel: String
    ) {
        viewModelScope.launch {
            if (endpoint.isBlank()) {
                _snackbarMessage.emit("API Endpoint URL cannot be blank.")
                return@launch
            }
            _apiEndpoint.value = endpoint
            _apiKey.value = key
            _selectedModel.value = modelName
            _temperature.value = temp
            _maxTokens.value = maxToks
            _accentColor.value = color
            _autoSuggestEnabled.value = autoSuggest
            _showResearchLinks.value = showResearch
            _enableSyntaxHighlighting.value = enableHighlight
            _fontSizeLevel.value = fontLevel

            // Persist
            repository.saveSetting("api_endpoint", endpoint)
            repository.saveSetting("api_key", key)
            repository.saveSetting("selected_model", modelName)
            repository.saveSetting("temperature", temp.toString())
            repository.saveSetting("max_tokens", maxToks.toString())
            repository.saveSetting("accent_color", color.hex)
            repository.saveSetting("auto_suggest", autoSuggest.toString())
            repository.saveSetting("show_research_links", showResearch.toString())
            repository.saveSetting("enable_syntax_highlighting", enableHighlight.toString())
            repository.saveSetting("font_size", fontLevel)

            _snackbarMessage.emit("Settings saved successfully!")
            _currentScreen.value = Screen.Chat
        }
    }

    fun updateAISettings(
        endpoint: String,
        geminiKey: String,
        groqKey: String,
        nvidiaKey: String,
        customKey: String,
        modelName: String,
        protocol: String,
        systemPromptText: String,
        temp: Float,
        maxToks: Int,
        tavilyKey: String,
        braveKey: String,
        thinkingLevel: String = "none",
        e2bKey: String = ""
    ) {
        viewModelScope.launch {
            if (endpoint.isBlank()) {
                _snackbarMessage.emit("API Endpoint URL cannot be blank.")
                return@launch
            }
            
            fun sanitizeKey(key: String): String = key
                .replace("\"", "")
                .replace("'", "")
                .replace("\\s".toRegex(), "")
                .trim()

            val cleanGemini = sanitizeKey(geminiKey)
            val cleanGroq = sanitizeKey(groqKey)
            val cleanNvidia = sanitizeKey(nvidiaKey)
            val cleanCustom = sanitizeKey(customKey)
            val cleanTavily = sanitizeKey(tavilyKey)
            val cleanBrave = sanitizeKey(braveKey)
            val cleanE2b = sanitizeKey(e2bKey)

            _apiEndpoint.value = endpoint
            _geminiCustomApiKey.value = cleanGemini
            _groqCustomApiKey.value = cleanGroq
            _nvidiaCustomApiKey.value = cleanNvidia
            _customApiKey.value = cleanCustom
            _selectedModel.value = modelName
            _apiProtocol.value = protocol
            _systemPrompt.value = systemPromptText
            _temperature.value = temp
            _maxTokens.value = maxToks
            _tavilyApiKey.value = cleanTavily
            _braveApiKey.value = cleanBrave
            _e2bApiKey.value = cleanE2b
            _geminiThinkingLevel.value = thinkingLevel

            val activeKey = when {
                protocol == "gemini" -> cleanGemini
                endpoint.contains("nvidia.com", ignoreCase = true) || endpoint.contains("nvidia", ignoreCase = true) -> cleanNvidia
                endpoint.contains("groq.com", ignoreCase = true) -> cleanGroq
                else -> cleanCustom
            }
            _apiKey.value = activeKey

            repository.saveSetting("api_endpoint", endpoint)
            repository.saveSetting("api_key_gemini", cleanGemini)
            repository.saveSetting("api_key_groq", cleanGroq)
            repository.saveSetting("api_key_nvidia", cleanNvidia)
            repository.saveSetting("api_key_custom", cleanCustom)
            repository.saveSetting("api_key", activeKey)
            repository.saveSetting("selected_model", modelName)
            repository.saveSetting("api_protocol", protocol)
            repository.saveSetting("system_prompt", systemPromptText)
            repository.saveSetting("temperature", temp.toString())
            repository.saveSetting("max_tokens", maxToks.toString())
            repository.saveSetting("tavily_api_key", cleanTavily)
            repository.saveSetting("brave_api_key", cleanBrave)
            repository.saveSetting("e2b_api_key", cleanE2b)
            repository.saveSetting("gemini_thinking_level", thinkingLevel)

            _snackbarMessage.emit("AI Custom Settings saved successfully!")
            _currentScreen.value = Screen.Chat
        }
    }

    fun selectProviderProtocol(protocol: String) {
        viewModelScope.launch {
            _apiError.value = null
            _apiProtocol.value = protocol
            repository.saveSetting("api_protocol", protocol)

            val endpoint = when (protocol) {
                "gemini" -> "https://generativelanguage.googleapis.com"
                "groq" -> "https://api.groq.com/openai/v1"
                "nvidia" -> "https://integrate.api.nvidia.com/v1"
                else -> "https://generativelanguage.googleapis.com"
            }
            _apiEndpoint.value = endpoint
            repository.saveSetting("api_endpoint", endpoint)

            val activeKey = when (protocol) {
                "gemini" -> _geminiCustomApiKey.value.ifBlank { BuildConfig.GEMINI_API_KEY.let { if (it.isRealKey()) it else "" } }
                "groq" -> _groqCustomApiKey.value.ifBlank { BuildConfig.GROQ_API_KEY.let { if (it.isRealKey()) it else "" } }
                "nvidia" -> _nvidiaCustomApiKey.value
                else -> _customApiKey.value
            }
            _apiKey.value = activeKey
            repository.saveSetting("api_key", activeKey)

            val model = when (protocol) {
                "gemini" -> "gemini-3.1-flash-lite"
                "groq" -> "llama-3.3-70b-versatile"
                "nvidia" -> "meta/llama-3.1-405b-instruct"
                else -> "gemini-3.5-flash"
            }
            _selectedModel.value = model
            repository.saveSetting("selected_model", model)

            _snackbarMessage.emit("Provider gewechselt zu: ${protocol.uppercase()}")
        }
    }

    // --- Validate custom endpoint ---
    fun testConnection(endpoint: String, key: String, protocol: String = "gemini", modelName: String = "gemini-3.5-flash") {
        viewModelScope.launch {
            if (endpoint.isBlank()) {
                _validationResult.value = "ERROR: Endpoint cannot be empty."
                return@launch
            }
            _isValidating.value = true
            _validationResult.value = null

            val provider = when {
                protocol == "gemini" -> "gemini"
                endpoint.contains("nvidia.com", ignoreCase = true) || endpoint.contains("nvidia", ignoreCase = true) -> "nvidia"
                endpoint.contains("groq.com", ignoreCase = true) -> "groq"
                else -> "custom"
            }
            updateProviderStatus(provider, "PENDING")

            val result = repository.testEndpoint(endpoint, key, protocol, modelName)
            _isValidating.value = false
            if (result.isSuccess) {
                _validationResult.value = "SUCCESS"
                updateProviderStatus(provider, "SUCCESS")
            } else {
                _validationResult.value = "ERROR: ${result.exceptionOrNull()?.localizedMessage ?: "Connection failed"}"
                updateProviderStatus(provider, "ERROR")
            }
        }
    }

    fun clearValidationResult() {
        _validationResult.value = null
    }

    // --- Conversations Actions ---
    fun startNewChat() {
        viewModelScope.launch {
            _isMessagesLoading.value = true
            _messagesLimit.value = 25
            val title = "Chat #${conversations.value.size + 1}"
            val newId = repository.createConversation(title = title, previewText = "Empty history")
            _currentConversationId.value = newId
            _currentConversationTitle.value = title
            _isSidebarOpen.value = false
            _inputText.value = ""
            _currentScreen.value = Screen.Chat
            delay(400)
            _isMessagesLoading.value = false
        }
    }

    fun selectConversation(id: Long) {
        viewModelScope.launch {
            _isMessagesLoading.value = true
            _messagesLimit.value = 25
            val found = conversations.value.find { it.id == id }
            if (found != null) {
                _currentConversationId.value = id
                _currentConversationTitle.value = found.title
                _inputText.value = ""
                _currentScreen.value = Screen.Chat
            }
            _isSidebarOpen.value = false
            delay(500)
            _isMessagesLoading.value = false
        }
    }

    fun renameConversation(id: Long, newTitle: String) {
        viewModelScope.launch {
            if (newTitle.isNotBlank()) {
                repository.updateConversation(id, newTitle)
                if (_currentConversationId.value == id) {
                    _currentConversationTitle.value = newTitle
                }
            }
        }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repository.deleteConversation(id)
            _snackbarMessage.emit("Conversation deleted.")
            if (_currentConversationId.value == id) {
                // Return to first conversation, or empty state
                val remaining = conversations.value.filter { it.id != id }
                if (remaining.isNotEmpty()) {
                    selectConversation(remaining.first().id)
                } else {
                    _currentConversationId.value = null
                    _currentConversationTitle.value = "New Chat"
                }
            }
        }
    }

    fun clearCurrentConversationAndInput() {
        viewModelScope.launch {
            _inputText.value = ""
            val activeId = _currentConversationId.value
            if (activeId != null) {
                repository.clearConversationMessages(activeId)
                _snackbarMessage.emit("Conversation cleared.")
            } else {
                _snackbarMessage.emit("Input reset.")
            }
        }
    }

    // --- Inputs Actions ---
    fun onInputChange(text: String) {
        _inputText.value = text
    }

    fun enhancePrompt() {
        val currentPrompt = _inputText.value.trim()
        if (currentPrompt.isBlank()) return

        viewModelScope.launch {
            _isEnhancing.value = true
            try {
                val instruction = "You are an expert prompt engineer. Please rewrite and enhance the following user request to be more detailed, precise, educational, and structured for an AI assistant. Make it professional but concise. Do not write any introduction, quotes, or conversational filler; return ONLY the enhanced, rewritten prompt text itself.\n\nUser Prompt: $currentPrompt"
                val result = repository.queryAI(
                    prompt = instruction,
                    conversationId = -1L,
                    history = emptyList()
                )
                result.onSuccess { enhanced ->
                    if (enhanced.isNotBlank()) {
                        _inputText.value = enhanced.trim()
                        _snackbarMessage.emit("Prompt erfolgreich verbessert! ✨")
                    }
                }.onFailure { err ->
                    _snackbarMessage.emit("Prompt-Verbesserung fehlgeschlagen: ${err.message}")
                }
            } catch (e: Exception) {
                _snackbarMessage.emit("Fehler beim Verbessern des Prompts: ${e.message}")
            } finally {
                _isEnhancing.value = false
            }
        }
    }

    fun appendSuggestion(suggestion: String) {
        _inputText.value = suggestion
        _suggestions.value = emptyList()
    }

    // --- Context Entities Multi-Selection ---
    fun toggleContextEntrySelection(id: Long) {
        val currentSet = _selectedContextEntries.value
        _selectedContextEntries.value = if (currentSet.contains(id)) {
            currentSet - id
        } else {
            currentSet + id
        }
    }

    fun clearContextSelections() {
        _selectedContextEntries.value = emptySet()
    }

    // --- Knowledge base management ---
    fun addKnowledge(title: String, url: String, content: String) {
        viewModelScope.launch {
            if (title.isBlank() || content.isBlank()) {
                _snackbarMessage.emit("Title and Content must not be blank.")
                return@launch
            }
            repository.insertKnowledgeEntry(title, url, content)
            _snackbarMessage.emit("Knowledge base reference added.")
        }
    }

    fun deleteKnowledge(id: Long) {
        viewModelScope.launch {
            repository.deleteKnowledgeEntry(id)
            _selectedContextEntries.value = _selectedContextEntries.value - id
            _snackbarMessage.emit("Knowledge base item deleted.")
        }
    }

    // --- Network Diagnostics ---
    val connectionStatus = com.example.util.DiagnosticLogger.connectionStatus

    fun sendDiagnosticPing() {
        viewModelScope.launch {
            _snackbarMessage.emit("Sending ping...")
            com.example.util.DiagnosticLogger.logRequest("Diagnostic Ping")
            try {
                // Call repository directly bypassing local DB
                val result = repository.queryAI(
                    prompt = "Respond exactly with: 'PONG! connection successful.'",
                    conversationId = -1L, // Fake ID
                    history = emptyList(),
                    knowledgeContext = ""
                )
                if (result.isSuccess) {
                    _snackbarMessage.emit("Ping Success: ${result.getOrNull()}")
                } else {
                    _snackbarMessage.emit("Ping Failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                com.example.util.DiagnosticLogger.logError(e.message ?: "Unknown ping error")
                _snackbarMessage.emit("Ping Error: ${e.message}")
            }
        }
    }

    // --- Core Conversation sending logic ---
    fun sendMessage() {
        val messageText = _inputText.value.trim()
        if (messageText.isBlank()) return

        chatJob = viewModelScope.launch {
            var activeConvId = _currentConversationId.value
            if (activeConvId == null) {
                // Auto create a conversation if none is active
                val title = if (messageText.length > 25) messageText.substring(0, 22) + "..." else messageText
                activeConvId = repository.createConversation(title = title, previewText = "Asking AI...")
                _currentConversationId.value = activeConvId
                _currentConversationTitle.value = title
            }

            // Check for slash command shortcuts
            if (messageText.startsWith("/")) {
                val cmd = messageText.split("\\s+".toRegex()).firstOrNull()?.lowercase() ?: ""
                when (cmd) {
                    "/prompt-db" -> {
                        executePromptDb(activeConvId)
                        return@launch
                    }
                    "/summarize" -> {
                        executeSummarize(activeConvId)
                        return@launch
                    }
                    "/help" -> {
                        executeHelp(activeConvId)
                        return@launch
                    }
                    "/clear" -> {
                        executeClear()
                        return@launch
                    }
                    "/diagnostic" -> {
                        executeDiagnostic(activeConvId)
                        return@launch
                    }
                }
            }

            // Save user message
            val userMsg = MessageEntity(
                conversationId = activeConvId,
                role = "user",
                content = messageText
            )
            repository.insertMessage(userMsg)

            // Clear input
            _inputText.value = ""
            _suggestions.value = emptyList()

            // Gather context from selected Knowledge base entries
            val selectedIds = _selectedContextEntries.value
            val contextText = if (selectedIds.isNotEmpty()) {
                val matched = knowledgeEntries.value.filter { selectedIds.contains(it.id) }
                matched.joinToString("\n\n") { "Title: ${it.title}\nURL: ${it.url}\nContent: ${it.content}" }
            } else {
                ""
            }

            // Perform AI Generation with loading state
            _apiError.value = null
            _isAnalyzing.value = true
            _aiActionStatus.value = "THINKING"
            // Read all history up to current state for context (exclude last generated response which doesn't exist yet)
            val history = messages.value.filter { it.conversationId == activeConvId }

            val aiResult = repository.queryAI(
                prompt = messageText,
                conversationId = activeConvId,
                history = history,
                knowledgeContext = contextText,
                onStatusChange = { status -> _aiActionStatus.value = status }
            )

            _isAnalyzing.value = false
            _aiActionStatus.value = ""
            if (aiResult.isFailure) {
                val errorMsg = aiResult.exceptionOrNull()?.localizedMessage ?: "Unknown API exception"
                _apiError.value = errorMsg
                _snackbarMessage.emit("Network Error: $errorMsg")
            } else {
                _apiError.value = null
            }
        }
    }

    private suspend fun executePromptDb(activeConvId: Long) {
        _inputText.value = ""
        _suggestions.value = emptyList()

        val userMsg = MessageEntity(
            conversationId = activeConvId,
            role = "user",
            content = "⚡ `/prompt-db` - Generiere SQLite-Datenbankschema"
        )
        repository.insertMessage(userMsg)

        val history = messages.value.filter { it.conversationId == activeConvId }
        val historyText = history
            .filter { it.role == "user" || it.role == "model" }
            .takeLast(40)
            .joinToString("\n") { "${it.role.uppercase()}: ${it.content}" }

        val specializedPrompt = """
Hier ist der bisherige Verlauf unserer Konversation:
$historyText

Bitte entwirf eine voll funktionsfähige, strukturierte und normalisierte SQLite-Datenbankstruktur (bzw. Room-Datenbank), die den Anforderungen dieses Chats entspricht.
Gib mir:
1. Eine verständliche Erklärung des Datenbankschemas.
2. Vollständige 'CREATE TABLE'-Anweisungen mit passenden Primärschlüsseln, Fremdschlüsseln und Constraints.
3. Repräsentative 'INSERT INTO'-Anweisungen mit typischen Beispieldaten.
4. Nützliche Beispiel-Queries (z. B. Abfragen mit JOINs) zur Veranschaulichung.

Formatiere die SQL-Teile in sauberen Markdown-Codeblöcken (mit ```sql).
        """.trimIndent()

        _apiError.value = null
        _isAnalyzing.value = true
        _aiActionStatus.value = "THINKING"

        val result = repository.queryAI(
            prompt = specializedPrompt,
            conversationId = activeConvId,
            history = history,
            knowledgeContext = "",
            onStatusChange = { _aiActionStatus.value = it }
        )

        _isAnalyzing.value = false
        _aiActionStatus.value = ""

        if (result.isFailure) {
            val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown API exception"
            _apiError.value = errorMsg
            _snackbarMessage.emit("Datenbank-Generierung fehlgeschlagen: $errorMsg")
        } else {
            _apiError.value = null
            _snackbarMessage.emit("Datenbank-Entwurf generiert! 💾")
        }
    }

    private suspend fun executeSummarize(activeConvId: Long) {
        _inputText.value = ""
        _suggestions.value = emptyList()

        val userMsg = MessageEntity(
            conversationId = activeConvId,
            role = "user",
            content = "⚡ `/summarize` - Zusammenfassung erstellen"
        )
        repository.insertMessage(userMsg)

        val history = messages.value.filter { it.conversationId == activeConvId }
        val historyText = history
            .filter { it.role == "user" || it.role == "model" }
            .joinToString("\n") { "${it.role.uppercase()}: ${it.content}" }

        val specializedPrompt = """
Hier ist die bisherige Konversationshistorie:
$historyText

Bitte erstelle ein präzises, strukturiertes und übersichtliches Protokoll bzw. eine Zusammenfassung unserer bisherigen Diskussion. Hebe wichtige Erkenntnisse, behandelte Themen sowie empfohlene Lösungsschritte klar hervor. Stilvoll und übersichtlich formatiert.
        """.trimIndent()

        _apiError.value = null
        _isAnalyzing.value = true
        _aiActionStatus.value = "THINKING"

        val result = repository.queryAI(
            prompt = specializedPrompt,
            conversationId = activeConvId,
            history = history,
            knowledgeContext = "",
            onStatusChange = { _aiActionStatus.value = it }
        )

        _isAnalyzing.value = false
        _aiActionStatus.value = ""

        if (result.isFailure) {
            val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown API exception"
            _apiError.value = errorMsg
            _snackbarMessage.emit("Zusammenfassung fehlgeschlagen: $errorMsg")
        } else {
            _apiError.value = null
            _snackbarMessage.emit("Zusammenfassung erstellt! 📝")
        }
    }

    private suspend fun executeHelp(activeConvId: Long) {
        _inputText.value = ""
        _suggestions.value = emptyList()

        val userMsg = MessageEntity(
            conversationId = activeConvId,
            role = "user",
            content = "⚡ `/help` - Befehlsliste anzeigen"
        )
        repository.insertMessage(userMsg)

        delay(150)

        val helpResponse = """
### 🚀 Verfügbare Slash-Befehle (Shortcuts)

Nutze diese praktischen Tastatur-Shortcuts im Chat-Eingabefeld, um häufige Aktionen sofort auszuführen:

*   💾 **`/prompt-db`**  
    *Analysiert den bisherigen Chatverlauf und generiert ein voll strukturiertes SQLite-Datenbankschema (CREATE TABLE, INSERT-Beispiele & Abfragen).*
*   📝 **`/summarize`**  
    *Erstellt eine strukturierte, übersichtliche Zusammenfassung der bisherigen Konversation mit allen Kernerkenntnissen.*
*   ℹ️ **`/help`**  
    *Zeigt diese Übersicht aller verfügbaren Slash-Befehle im Chat an.*
*   🧹 **`/clear`**  
    *Leert augenblicklich das gesamte Chatfenster der aktuellen Konversation.*
*   ⚡ **`/diagnostic`**  
    *Führt einen automatischen Netzwerk- und API-Verbindungstest durch.*
    
*Tipp: Du kannst einfach `/` im Eingabefeld eintippen, um die Liste aller Slash-Befehle und Autovervollständigungen interaktiv aufzurufen.*
        """.trimIndent()

        val modelMsg = MessageEntity(
            conversationId = activeConvId,
            role = "model",
            content = helpResponse,
            provider = _apiProtocol.value,
            latencyMs = 150L
        )
        repository.insertMessage(modelMsg)
        _snackbarMessage.emit("Hilfemenü geladen.")
    }

    private suspend fun executeClear() {
        clearCurrentConversationAndInput()
    }

    private suspend fun executeDiagnostic(activeConvId: Long) {
        _inputText.value = ""
        _suggestions.value = emptyList()

        val userMsg = MessageEntity(
            conversationId = activeConvId,
            role = "user",
            content = "⚡ `/diagnostic` - API-Verbindung testen"
        )
        repository.insertMessage(userMsg)

        _snackbarMessage.emit("Führe API-Diagnostic-Ping aus...")
        
        val pingStartTime = System.currentTimeMillis()
        try {
            _isAnalyzing.value = true
            _aiActionStatus.value = "CONNECTING"
            
            val result = repository.queryAI(
                prompt = "Respond exactly with: 'PONG! connection successful.'",
                conversationId = -1L,
                history = emptyList(),
                knowledgeContext = ""
            )
            _isAnalyzing.value = false
            _aiActionStatus.value = ""

            val duration = System.currentTimeMillis() - pingStartTime
            if (result.isSuccess) {
                val responseText = """
### ⚡ API Diagnose-Testergebnis (Erfolgreich)

*   **Status**: Online / Erreichbar ✅
*   **API-Antwort**: `${result.getOrNull()}`
*   **Roundtrip-Latenz**: `${duration}ms`
*   **Ausgewähltes Protokoll**: `${_apiProtocol.value.uppercase()}`
*   **Ausgewähltes Modell**: `${_selectedModel.value}`
*   **API-Endpunkt**: `${_apiEndpoint.value}`
                """.trimIndent()
                
                val modelMsg = MessageEntity(
                    conversationId = activeConvId,
                    role = "model",
                    content = responseText,
                    provider = _apiProtocol.value,
                    latencyMs = duration
                )
                repository.insertMessage(modelMsg)
                _snackbarMessage.emit("Verbindungstest erfolgreich! ✅")
            } else {
                val errorMsg = result.exceptionOrNull()?.localizedMessage ?: "Unknown API exception"
                val responseText = """
### ❌ API Diagnose-Testergebnis (Fehlgeschlagen)

*   **Status**: Offline / Fehlerhaft ❌
*   **Fehlermeldung**: `$errorMsg`
*   **Dauer**: `${duration}ms`
*   **Protokoll**: `${_apiProtocol.value.uppercase()}`
*   **API-Endpunkt**: `${_apiEndpoint.value}`
                """.trimIndent()
                
                val modelMsg = MessageEntity(
                    conversationId = activeConvId,
                    role = "model",
                    content = responseText,
                    provider = _apiProtocol.value,
                    latencyMs = duration
                )
                repository.insertMessage(modelMsg)
                _snackbarMessage.emit("Diagnose fehlgeschlagen. ❌")
            }
        } catch (e: Exception) {
            _isAnalyzing.value = false
            _aiActionStatus.value = ""
            _snackbarMessage.emit("Diagnosefehler: ${e.message}")
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
            _snackbarMessage.emit("Message deleted.")
        }
    }

    fun exportChatHistoryAsMarkdown(): String {
        return messages.value.joinToString("\n\n") { message ->
            val role = if (message.role == "user") "User" else "AI"
            "### $role\n\n${message.content}"
        }
    }

    private val _sharedSessionId = MutableStateFlow<String?>(null)
    val sharedSessionId: StateFlow<String?> = _sharedSessionId.asStateFlow()

    private val collaborationManager by lazy { com.example.data.remote.FirestoreCollaborationManager() }

    fun shareChatSession(): String? {
        val sessionId = collaborationManager.shareChatSession(_currentConversationId.value ?: 0L, messages.value)
        if (sessionId != null) {
            _sharedSessionId.value = sessionId
        }
        return sessionId
    }

    fun isRealTimeCollaborationAvailable(): Boolean {
        return collaborationManager.isFirebaseAvailable()
    }

    fun joinCollaboration(sessionId: String) {
        _sharedSessionId.value = sessionId
        viewModelScope.launch {
            collaborationManager.listenToMessages(sessionId).collect { remoteMessages ->
                // Note: Real-time syncing logic would go here, updating the messages state
                // This is a simplification
                _snackbarMessage.emit("Joined collaboration session: $sessionId")
            }
        }
    }

    fun editMessage(id: Long, newContent: String) {
        viewModelScope.launch {
            if (newContent.isNotBlank()) {
                val foundMsg = messages.value.find { it.id == id }
                if (foundMsg != null) {
                    val updated = foundMsg.copy(content = newContent)
                    repository.insertMessage(updated)
                    _snackbarMessage.emit("Message updated.")
                }
            }
        }
    }

    fun regenerateResponse(targetMessage: MessageEntity) {
        viewModelScope.launch {
            val activeConvId = _currentConversationId.value ?: return@launch
            val allMsgs = messages.value.filter { it.conversationId == activeConvId }
            val index = allMsgs.indexOfFirst { it.id == targetMessage.id }
            if (index == -1) return@launch

            // If target is model, the prompt is the preceding user message.
            // If target is user, the prompt is the target itself.
            val promptMsg = if (targetMessage.role == "model") {
                if (index > 0) allMsgs[index - 1] else null
            } else {
                targetMessage
            } ?: return@launch

            // Delete the old response(s) from this point onwards in the view list
            if (targetMessage.role == "model") {
                repository.deleteMessage(targetMessage.id)
            }

            // Gather preceding history before the prompt
            val promptIndex = allMsgs.indexOfFirst { it.id == promptMsg.id }
            val historyBeforePrompt = if (promptIndex > 0) {
                allMsgs.subList(0, promptIndex)
            } else {
                emptyList()
            }

            // Gather context from selected Knowledge base entries
            val selectedIds = _selectedContextEntries.value
            val contextText = if (selectedIds.isNotEmpty()) {
                val matched = knowledgeEntries.value.filter { selectedIds.contains(it.id) }
                matched.joinToString("\n\n") { "Title: ${it.title}\nURL: ${it.url}\nContent: ${it.content}" }
            } else {
                ""
            }

            // Perform AI Generation with loading state
            _apiError.value = null
            _isAnalyzing.value = true
            _aiActionStatus.value = "THINKING"

            val aiResult = repository.queryAI(
                prompt = promptMsg.content,
                conversationId = activeConvId,
                history = historyBeforePrompt,
                knowledgeContext = contextText,
                onStatusChange = { status -> _aiActionStatus.value = status }
            )

            _isAnalyzing.value = false
            _aiActionStatus.value = ""
            if (aiResult.isFailure) {
                val errorMsg = aiResult.exceptionOrNull()?.localizedMessage ?: "Unknown API exception"
                _apiError.value = errorMsg
                _snackbarMessage.emit("Network Error: $errorMsg")
            } else {
                _apiError.value = null
                _snackbarMessage.emit("Response regenerated.")
            }
        }
    }
}
