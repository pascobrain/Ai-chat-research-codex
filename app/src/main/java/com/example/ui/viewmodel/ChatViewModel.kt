package com.example.ui.viewmodel

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

    // --- Observe Messages for Active Conversation ---
    val messages: StateFlow<List<MessageEntity>> = _currentConversationId
        .flatMapLatest { id ->
            if (id != null) {
                repository.getMessagesForConversation(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Chat Input ---
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // --- Auto-suggestions Logic with Debounce ---
    private val staticSuggestions = listOf(
        "how to implement Jetpack Compose LazyColumn",
        "how does Kotlin coroutines Dispatchers.IO work",
        "explain Generative AI model temperature and topP",
        "best practices for SQLite or Room database in Android",
        "how to structure clean architecture with MVVM in Kotlin",
        "how to resolve Android memory leaks with LeakCanary",
        "explain differences between Gemini 1.5 Flash and Pro",
        "how to code a simple custom drawing Canvas in Compose",
        "how to secure API keys utilizing BuildConfig in Android"
    )

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    // --- AI Status indicator ---
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    // --- Settings Input States ---
    private val _apiEndpoint = MutableStateFlow("https://generativelanguage.googleapis.com/")
    val apiEndpoint: StateFlow<String> = _apiEndpoint.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-3.5-flash")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _apiProtocol = MutableStateFlow("gemini")
    val apiProtocol: StateFlow<String> = _apiProtocol.asStateFlow()

    private val _systemPrompt = MutableStateFlow("")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _temperature = MutableStateFlow(0.7f)
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    private val _maxTokens = MutableStateFlow(2048)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

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
            _selectedModel.value = repository.getSetting("selected_model", "gemini-3.5-flash")
            _apiProtocol.value = repository.getSetting("api_protocol", "gemini")
            _systemPrompt.value = repository.getSetting("system_prompt", "")
            _temperature.value = repository.getSetting("temperature", "0.7").toFloatOrNull() ?: 0.7f
            _maxTokens.value = repository.getSetting("max_tokens", "2048").toIntOrNull() ?: 2048
            
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
        key: String,
        modelName: String,
        protocol: String,
        systemPromptText: String,
        temp: Float,
        maxToks: Int
    ) {
        viewModelScope.launch {
            if (endpoint.isBlank()) {
                _snackbarMessage.emit("API Endpoint URL cannot be blank.")
                return@launch
            }
            _apiEndpoint.value = endpoint
            _apiKey.value = key
            _selectedModel.value = modelName
            _apiProtocol.value = protocol
            _systemPrompt.value = systemPromptText
            _temperature.value = temp
            _maxTokens.value = maxToks

            repository.saveSetting("api_endpoint", endpoint)
            repository.saveSetting("api_key", key)
            repository.saveSetting("selected_model", modelName)
            repository.saveSetting("api_protocol", protocol)
            repository.saveSetting("system_prompt", systemPromptText)
            repository.saveSetting("temperature", temp.toString())
            repository.saveSetting("max_tokens", maxToks.toString())

            _snackbarMessage.emit("AI Custom Settings saved successfully!")
            _currentScreen.value = Screen.Chat
        }
    }

    // --- Validate custom endpoint ---
    fun testConnection(endpoint: String, key: String, protocol: String = "gemini") {
        viewModelScope.launch {
            if (endpoint.isBlank()) {
                _validationResult.value = "ERROR: Endpoint cannot be empty."
                return@launch
            }
            _isValidating.value = true
            _validationResult.value = null

            val result = repository.testEndpoint(endpoint, key, protocol)
            _isValidating.value = false
            if (result.isSuccess) {
                _validationResult.value = "SUCCESS"
            } else {
                _validationResult.value = "ERROR: ${result.exceptionOrNull()?.localizedMessage ?: "Connection failed"}"
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

    // --- Inputs Actions ---
    fun onInputChange(text: String) {
        _inputText.value = text
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

    // --- Core Conversation sending logic ---
    fun sendMessage() {
        val messageText = _inputText.value.trim()
        if (messageText.isBlank()) return

        viewModelScope.launch {
            var activeConvId = _currentConversationId.value
            if (activeConvId == null) {
                // Auto create a conversation if none is active
                val title = if (messageText.length > 25) messageText.substring(0, 22) + "..." else messageText
                activeConvId = repository.createConversation(title = title, previewText = "Asking AI...")
                _currentConversationId.value = activeConvId
                _currentConversationTitle.value = title
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
            _isAnalyzing.value = true
            // Read all history up to current state for context (exclude last generated response which doesn't exist yet)
            val history = messages.value.filter { it.conversationId == activeConvId }

            val aiResult = repository.queryAI(
                prompt = messageText,
                conversationId = activeConvId,
                history = history,
                knowledgeContext = contextText
            )

            _isAnalyzing.value = false
            if (aiResult.isFailure) {
                _snackbarMessage.emit("Network Error: " + (aiResult.exceptionOrNull()?.localizedMessage ?: "Unknown API exception"))
            }
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            repository.deleteMessage(id)
            _snackbarMessage.emit("Message deleted.")
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
            _isAnalyzing.value = true

            val aiResult = repository.queryAI(
                prompt = promptMsg.content,
                conversationId = activeConvId,
                history = historyBeforePrompt,
                knowledgeContext = contextText
            )

            _isAnalyzing.value = false
            if (aiResult.isFailure) {
                _snackbarMessage.emit("Network Error: " + (aiResult.exceptionOrNull()?.localizedMessage ?: "Unknown API exception"))
            } else {
                _snackbarMessage.emit("Response regenerated.")
            }
        }
    }
}
