package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.Screen
import com.example.util.isRealKey
import com.example.util.getRealOrEmpty
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISettingsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Observe DB configurations
    val apiEndpointState by viewModel.apiEndpoint.collectAsState()
    val apiKeyState by viewModel.apiKey.collectAsState()
    val geminiApiKeyState by viewModel.geminiCustomApiKey.collectAsState()
    val groqApiKeyState by viewModel.groqCustomApiKey.collectAsState()
    val nvidiaApiKeyState by viewModel.nvidiaCustomApiKey.collectAsState()
    val customApiKeyState by viewModel.customApiKey.collectAsState()
    val selectedModelState by viewModel.selectedModel.collectAsState()
    val apiProtocolState by viewModel.apiProtocol.collectAsState()
    val systemPromptState by viewModel.systemPrompt.collectAsState()
    val temperatureState by viewModel.temperature.collectAsState()
    val maxTokensState by viewModel.maxTokens.collectAsState()
    val tavilyApiKeyState by viewModel.tavilyApiKey.collectAsState()
    val braveApiKeyState by viewModel.braveApiKey.collectAsState()
    val e2bApiKeyState by viewModel.e2bApiKey.collectAsState()
    val thinkingLevelState by viewModel.geminiThinkingLevel.collectAsState()

    // Connection testing
    val isTestingConnection by viewModel.isValidating.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()
    val providerStatuses by viewModel.providerStatuses.collectAsState()

    // Local state managers
    var apiProtocol by remember { mutableStateOf(apiProtocolState) }
    var apiEndpoint by remember { mutableStateOf(apiEndpointState) }
    var apiKey by remember { mutableStateOf(apiKeyState) }
    var geminiApiKey by remember { mutableStateOf(geminiApiKeyState) }
    var groqApiKey by remember { mutableStateOf(groqApiKeyState) }
    var nvidiaApiKey by remember { mutableStateOf(nvidiaApiKeyState) }
    var customApiKey by remember { mutableStateOf(customApiKeyState) }
    var searchTavilyKey by remember { mutableStateOf(tavilyApiKeyState) }
    var searchBraveKey by remember { mutableStateOf(braveApiKeyState) }
    var e2bKey by remember { mutableStateOf(e2bApiKeyState) }
    var selectedModel by remember { mutableStateOf(selectedModelState) }
    var systemPrompt by remember { mutableStateOf(systemPromptState) }
    var tempVal by remember { mutableStateOf(temperatureState) }
    var maxTokens by remember { mutableStateOf(maxTokensState.toString()) }
    var thinkingLevel by remember { mutableStateOf(thinkingLevelState) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var userHasModified by remember { mutableStateOf(false) }

    var providerSelection by remember {
        mutableStateOf(
            when {
                apiProtocolState == "gemini" -> "gemini"
                apiEndpointState.trimEnd('/').lowercase() == "https://api.nvidia.com/v1" || apiEndpointState.contains("nvidia") -> "nvidia"
                apiEndpointState.trimEnd('/').lowercase() == "https://api.groq.com/openai/v1" -> "groq"
                else -> "custom"
            }
        )
    }

    // Sync state
    LaunchedEffect(apiProtocolState, apiEndpointState, apiKeyState, geminiApiKeyState, groqApiKeyState, nvidiaApiKeyState, customApiKeyState, selectedModelState, systemPromptState, temperatureState, maxTokensState, tavilyApiKeyState, braveApiKeyState, thinkingLevelState, e2bApiKeyState) {
        if (!userHasModified) {
            apiProtocol = apiProtocolState
            apiEndpoint = apiEndpointState
            apiKey = apiKeyState
            geminiApiKey = geminiApiKeyState
            groqApiKey = groqApiKeyState
            nvidiaApiKey = nvidiaApiKeyState
            customApiKey = customApiKeyState
            searchTavilyKey = tavilyApiKeyState
            searchBraveKey = braveApiKeyState
            e2bKey = e2bApiKeyState
            selectedModel = selectedModelState
            systemPrompt = systemPromptState
            tempVal = temperatureState
            maxTokens = maxTokensState.toString()
            thinkingLevel = thinkingLevelState
            providerSelection = when {
                apiProtocolState == "gemini" -> "gemini"
                apiEndpointState.trimEnd('/').lowercase() == "https://api.nvidia.com/v1" || apiEndpointState.contains("nvidia") -> "nvidia"
                apiEndpointState.trimEnd('/').lowercase() == "https://api.groq.com/openai/v1" -> "groq"
                else -> "custom"
            }
        }
    }

    val geminiModels = listOf("gemini-3.1-flash-lite", "gemini-2.5-flash", "gemini-3.5-flash", "gemma-4-31b-it")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        TopAppBar(
            title = {
                Text(
                    "AI Provider Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            navigationIcon = {
                IconButton(onClick = { viewModel.setScreen(Screen.Chat) }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Chat",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // INTEGRATED CORE PROVIDER, MODEL, KEY SECTIONS
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Header title for the integrated structure
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "PROVIDER & MODEL CONFIGURATION",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    // --- STEP 1: PROVIDER SELECTOR ---
                    Text(
                        "1. Provider auswählen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Wähle den passenden KI-Dienstleister für deine Chats",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val providers = listOf(
                            "gemini" to "Google Gemini",
                            "nvidia" to "Nvidia NIM",
                            "groq" to "Groq Cloud",
                            "custom" to "Custom OpenAI"
                        )

                        providers.forEach { (id, name) ->
                            val isSelected = providerSelection == id
                            val selectedBgColor = when (id) {
                                "gemini" -> Color(0xFF1E88E5).copy(alpha = 0.08f)
                                "nvidia" -> Color(0xFF76B900).copy(alpha = 0.08f)
                                "groq" -> Color(0xFFFF5722).copy(alpha = 0.08f)
                                else -> Color(0xFF00A67E).copy(alpha = 0.08f)
                            }
                            val selectedBorderBrush = when (id) {
                                "gemini" -> Brush.linearGradient(listOf(Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE91E63)))
                                "nvidia" -> Brush.linearGradient(listOf(Color(0xFF76B900), Color(0xFF4CAF50)))
                                "groq" -> Brush.linearGradient(listOf(Color(0xFFFF3D00), Color(0xFFF57C00)))
                                else -> Brush.linearGradient(listOf(Color(0xFF00A67E), Color(0xFF26A69A)))
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) selectedBgColor
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .then(
                                        if (isSelected) Modifier.border(
                                            width = 1.5.dp,
                                            brush = selectedBorderBrush,
                                            shape = RoundedCornerShape(12.dp)
                                        ) else Modifier.border(
                                            width = 1.6.dp,
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    )
                                    .clickable {
                                        userHasModified = true
                                        providerSelection = id
                                        if (id == "gemini") {
                                            apiProtocol = "gemini"
                                            apiEndpoint = "https://generativelanguage.googleapis.com/"
                                            selectedModel = "gemini-3.1-flash-lite"
                                        } else if (id == "nvidia") {
                                            apiProtocol = "openai"
                                            apiEndpoint = "https://integrate.api.nvidia.com/v1"
                                            selectedModel = "minimaxai/minimax-m2.7"
                                        } else if (id == "groq") {
                                            apiProtocol = "openai"
                                            apiEndpoint = "https://api.groq.com/openai/v1"
                                            selectedModel = "openai/gpt-oss-20b"
                                        } else {
                                            apiProtocol = "openai"
                                            apiEndpoint = "https://api.openai.com/v1"
                                            selectedModel = "gpt-4o"
                                        }
                                        viewModel.clearValidationResult()
                                    }
                                    .padding(vertical = 12.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Custom Logo components drawn on Canvas!
                                    when (id) {
                                        "gemini" -> GeminiLogo(isSelected = isSelected, modifier = Modifier.size(36.dp))
                                        "nvidia" -> NvidiaLogo(isSelected = isSelected, modifier = Modifier.size(36.dp))
                                        "groq" -> GroqLogo(isSelected = isSelected, modifier = Modifier.size(36.dp))
                                        else -> OpenAILogo(isSelected = isSelected, modifier = Modifier.size(36.dp))
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isSelected && id == "gemini" -> Color(0xFF1E88E5)
                                            isSelected && id == "nvidia" -> Color(0xFF76B900)
                                            isSelected && id == "groq" -> Color(0xFFF57C00)
                                            isSelected && id == "custom" -> Color(0xFF00A67E)
                                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        }
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    val statusVal = providerStatuses[id] ?: "INITIAL"
                                    val statusColor = when (statusVal) {
                                        "SUCCESS" -> Color(0xFF4CAF50)
                                        "PENDING" -> Color(0xFF1E88E5)
                                        "ERROR" -> Color(0xFFE53935)
                                        else -> { // "INITIAL"
                                            if (id == "gemini" && com.example.BuildConfig.GEMINI_API_KEY.isRealKey()) Color(0xFF4CAF50)
                                            else if (id == "groq" && com.example.BuildConfig.GROQ_API_KEY.isRealKey()) Color(0xFF4CAF50)
                                            else if (id == "nvidia" && nvidiaApiKey.isNotBlank()) Color(0xFF4CAF50)
                                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                        }
                                    }
                                    val statusLabel = when (statusVal) {
                                        "SUCCESS" -> "Aktiv"
                                        "PENDING" -> "Prüfe..."
                                        "ERROR" -> "Ungültig"
                                        else -> { // "INITIAL"
                                            if (id == "gemini" && com.example.BuildConfig.GEMINI_API_KEY.isRealKey()) "Aktiv"
                                            else if (id == "groq" && com.example.BuildConfig.GROQ_API_KEY.isRealKey()) "Aktiv"
                                            else if (id == "nvidia" && nvidiaApiKey.isNotBlank()) "Aktiv"
                                            else "Inaktiv"
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(statusColor.copy(alpha = 0.12f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(statusColor)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = statusLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- STEP 2: MODEL SELECTOR ---
                    Text(
                        "2. Model auswählen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Wähle das spezifische KI-Sprachmodell, das ausgeführt werden soll",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    when (providerSelection) {
                        "gemini" -> {
                            var geminiDropdownExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                OutlinedTextField(
                                    value = selectedModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Gemini Model") },
                                    trailingIcon = {
                                        IconButton(onClick = { geminiDropdownExpanded = true }) {
                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown models")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { geminiDropdownExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = geminiDropdownExpanded,
                                    onDismissRequest = { geminiDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    geminiModels.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontSize = 14.sp) },
                                            onClick = {
                                                userHasModified = true
                                                selectedModel = name
                                                geminiDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        "nvidia" -> {
                            var nvidiaDropdownExpanded by remember { mutableStateOf(false) }
                            val nvidiaModels = listOf(
                                "z-ai/glm-5.1",
                                "deepseek-ai/deepseek-v4-pro",
                                "nvidia/nemotron-3-super-120b-a12b",
                                "minimaxai/minimax-m2.7",
                                "stepfun-ai/step-3.7-flash"
                            )
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                OutlinedTextField(
                                    value = selectedModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Nvidia Model") },
                                    trailingIcon = {
                                        IconButton(onClick = { nvidiaDropdownExpanded = true }) {
                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown nvidia models")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { nvidiaDropdownExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = nvidiaDropdownExpanded,
                                    onDismissRequest = { nvidiaDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    nvidiaModels.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontSize = 14.sp) },
                                            onClick = {
                                                userHasModified = true
                                                selectedModel = name
                                                nvidiaDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        "groq" -> {
                            var groqDropdownExpanded by remember { mutableStateOf(false) }
                            val groqModels = listOf(
                                "openai/gpt-oss-20b",
                                "openai/gpt-oss-120b",
                                "llama-3.3-70b-versatile",
                                "llama-3.1-8b-instant",
                                "gemma2-9b-it",
                                "deepseek-r1-distill-llama-70b"
                            )
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                OutlinedTextField(
                                    value = selectedModel,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Groq Model") },
                                    trailingIcon = {
                                        IconButton(onClick = { groqDropdownExpanded = true }) {
                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown groq models")
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { groqDropdownExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = groqDropdownExpanded,
                                    onDismissRequest = { groqDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    groqModels.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontSize = 14.sp) },
                                            onClick = {
                                                userHasModified = true
                                                selectedModel = name
                                                groqDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        "custom" -> {
                            OutlinedTextField(
                                value = apiEndpoint,
                                onValueChange = {
                                    userHasModified = true
                                    apiEndpoint = it
                                    viewModel.clearValidationResult()
                                },
                                label = { Text("API Service Endpoint-URL") },
                                placeholder = { Text("https://api.openai.com/v1") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp)
                            )

                            var openAiDropdownExpanded by remember { mutableStateOf(false) }
                            val customModelsList = listOf("gpt-4o", "gpt-4o-mini", "o1-mini", "claude-3-5-sonnet")
                            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                OutlinedTextField(
                                    value = selectedModel,
                                    onValueChange = {
                                        userHasModified = true
                                        selectedModel = it
                                    },
                                    label = { Text("Model Name (z.B. gpt-4o)") },
                                    placeholder = { Text("gpt-4o") },
                                    trailingIcon = {
                                        IconButton(onClick = { openAiDropdownExpanded = true }) {
                                            Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown Custom types")
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                DropdownMenu(
                                    expanded = openAiDropdownExpanded,
                                    onDismissRequest = { openAiDropdownExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    customModelsList.forEach { name ->
                                        DropdownMenuItem(
                                            text = { Text(name, fontSize = 14.sp) },
                                            onClick = {
                                                userHasModified = true
                                                selectedModel = name
                                                openAiDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- STEP 3: API KEY CONFIG ---
                    Text(
                        "3. API Key eingeben/verwalten",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Hinterlege deinen API-Schlüssel oder nutze den vorkonfigurierten Key aus den Secrets",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    val activeApiKey = when (providerSelection) {
                        "gemini" -> geminiApiKey
                        "nvidia" -> nvidiaApiKey
                        "groq" -> groqApiKey
                        else -> customApiKey
                    }

                    OutlinedTextField(
                        value = activeApiKey,
                        onValueChange = { newValue ->
                            userHasModified = true
                            when (providerSelection) {
                                "gemini" -> geminiApiKey = newValue
                                "nvidia" -> nvidiaApiKey = newValue
                                "groq" -> groqApiKey = newValue
                                else -> customApiKey = newValue
                            }
                        },
                        label = { Text("API Key Settings") },
                        placeholder = {
                            when (providerSelection) {
                                "gemini" -> Text("Secure App Secrets Key")
                                "nvidia" -> Text("Secure Nvidia NIM Key")
                                "groq" -> Text("Secure Groq Secrets Key")
                                else -> Text("Enter custom private credentials...")
                            }
                        },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(imageVector = icon, contentDescription = "Toggle key mask")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )

                    // Helper/status badges for empty credentials
                    if (activeApiKey.isBlank()) {
                        val isPreconfiguredReal = when (providerSelection) {
                            "gemini" -> com.example.BuildConfig.GEMINI_API_KEY.isRealKey()
                            "nvidia" -> nvidiaApiKey.isNotBlank()
                            "groq" -> com.example.BuildConfig.GROQ_API_KEY.isRealKey()
                            else -> com.example.BuildConfig.GEMINI_API_KEY.isRealKey()
                        }
                        val hintText = when (providerSelection) {
                            "gemini" -> if (com.example.BuildConfig.GEMINI_API_KEY.isRealKey()) "Automatisch aktiv: Vorkonfigurierter Gemini Key aus Secrets." else "⚠️ Kein Gemini Key in Secrets gefunden! Bitte API Key manuell eingeben."
                            "nvidia" -> "⚠️ NVIDIA NIM Key ist leer! Bitte API Key manuell eingeben."
                            "groq" -> if (com.example.BuildConfig.GROQ_API_KEY.isRealKey()) "Automatisch aktiv: Vorkonfigurierter Groq Key aus Secrets." else "⚠️ Kein Groq Key in Secrets gefunden! Bitte API Key manuell eingeben."
                            else -> if (com.example.BuildConfig.GEMINI_API_KEY.isRealKey()) "Optional: Nutzt standardmäßig den Gemini Key aus den Secrets." else "Optional: Nutzt standardmäßig den Gemini Key aus den Secrets (nicht gefunden)."
                        }
                        val hintColor = if (isPreconfiguredReal) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isPreconfiguredReal) Icons.Default.VpnKey else Icons.Default.Warning,
                                contentDescription = "Zustand Verbindung",
                                tint = hintColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = hintText,
                                color = hintColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp, start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Manueller Override",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Manueller API Key überschreibt die standardmäßig hinterlegten Secrets.",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Integrated connection test
                    Divider(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.testConnection(apiEndpoint, activeApiKey, apiProtocol, selectedModel)
                            },
                            enabled = !isTestingConnection,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp).padding(end = 6.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text("Pinging...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.NetworkCheck,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Verbindung testen", fontSize = 12.sp)
                            }
                        }

                        // Validator Status Output
                        validationResult?.let { res ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (res == "SUCCESS") {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ping erfolgreich", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ping fehlgeschlagen", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    validationResult?.takeIf { it != "SUCCESS" }?.let { errorMsg ->
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // BEHAVIOR AND SYSTEM PROMPT CONFIG
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI BEHAVIORAL CONTROLS",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // System Prompt setting block
                    Text(
                        "System Prompt Settings (Instructions)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    OutlinedTextField(
                        value = systemPrompt,
                        onValueChange = { systemPrompt = it },
                        placeholder = {
                            Text("Instruct the AI on personality, system constraints, styling expectations, or roles...")
                        },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )

                    // Response temperature control slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Response Temperature", fontSize = 13.sp)
                        Text(
                            text = String.format("%.1f", tempVal),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = tempVal,
                        onValueChange = { tempVal = it },
                        valueRange = 0.0f..1.0f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Limits Tokens Size Input settings
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = {
                            if (it.isEmpty() || it.all { ch -> ch.isDigit() }) {
                                maxTokens = it
                            }
                        },
                        label = { Text("Response Max Token Limit") },
                        placeholder = { Text("2048") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                    )

                    if (apiProtocol == "gemini") {
                        Text(
                            "Gemini Thinking Level",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        var thinkingDropdownExpanded by remember { mutableStateOf(false) }
                        val thinkingOptions = listOf("none", "low", "high")
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            OutlinedTextField(
                                value = thinkingLevel,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { thinkingDropdownExpanded = true }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown thinking")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { thinkingDropdownExpanded = true }
                            )

                            DropdownMenu(
                                expanded = thinkingDropdownExpanded,
                                onDismissRequest = { thinkingDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                thinkingOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.capitalize(), fontSize = 14.sp) },
                                        onClick = {
                                            thinkingLevel = option
                                            thinkingDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SEARCH API KEYS
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "ZUSÄTZLICHE SUCH-APIS",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = searchTavilyKey,
                        onValueChange = { 
                            userHasModified = true
                            searchTavilyKey = it 
                        },
                        label = { Text("Tavily API Key (Optional)") },
                        placeholder = { Text("Automatisch aktiv aus Secrets...") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    if (searchTavilyKey.isBlank()) {
                        Text(
                            text = "💡 Aktiv: Nutzt den sicheren Tavily Key aus deinen App Secrets.",
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    OutlinedTextField(
                        value = searchBraveKey,
                        onValueChange = { 
                            userHasModified = true
                            searchBraveKey = it 
                        },
                        label = { Text("Brave Search API Key (Optional)") },
                        placeholder = { Text("Automatisch aktiv aus Secrets...") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    if (searchBraveKey.isBlank()) {
                        Text(
                            text = "💡 Aktiv: Nutzt den sicheren Brave Key aus deinen App Secrets.",
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            // E2B API KEY (FOR CODE EXECUTION)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "E2B SANDBOX CODE-AUSFÜHRUNG",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = e2bKey,
                        onValueChange = { 
                            userHasModified = true
                            e2bKey = it 
                        },
                        label = { Text("E2B API Key (Optional)") },
                        placeholder = { Text("Automatisch aktiv aus Secrets...") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                    if (e2bKey.isBlank()) {
                        Text(
                            text = "💡 Aktiv: Nutzt den sicheren E2B Key aus deinen App Secrets.",
                            color = Color(0xFF4CAF50),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            // Save and Apply Configuration settings Action
            var showUrlError by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    val parsedLimit = maxTokens.toIntOrNull() ?: 2048
                    if (apiEndpoint.isBlank()) {
                        showUrlError = true
                    } else {
                        val isValidUrl = try {
                            URL(apiEndpoint)
                            true
                        } catch (e: Exception) {
                            false
                        }
                        if (!isValidUrl) {
                            showUrlError = true
                        } else {
                            showUrlError = false
                            viewModel.updateAISettings(
                                endpoint = apiEndpoint,
                                geminiKey = geminiApiKey,
                                groqKey = groqApiKey,
                                nvidiaKey = nvidiaApiKey,
                                customKey = customApiKey,
                                modelName = selectedModel,
                                protocol = apiProtocol,
                                systemPromptText = systemPrompt,
                                temp = tempVal,
                                maxToks = parsedLimit,
                                tavilyKey = searchTavilyKey,
                                braveKey = searchBraveKey,
                                thinkingLevel = thinkingLevel,
                                e2bKey = e2bKey
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save and Apply Model Config", fontWeight = FontWeight.Bold)
            }

            AnimatedVisibility(visible = showUrlError) {
                Text(
                    text = "⚠️ Malformed API Endpoint. Setup requires a full valid address protocol (e.g. https://api.groq.com/openai/v1).",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun GeminiLogo(isSelected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        
        // Large sparkle path (Gemini sparkle)
        val path1 = Path().apply {
            val cx = w * 0.45f
            val cy = h * 0.55f
            val rx = w * 0.35f
            val ry = h * 0.35f
            
            moveTo(cx, cy - ry)
            quadraticTo(cx, cy, cx + rx, cy)
            quadraticTo(cx, cy, cx, cy + ry)
            quadraticTo(cx, cy, cx - rx, cy)
            quadraticTo(cx, cy, cx, cy - ry)
        }
        
        // Small sparkle path (Gemini secondary sparkle)
        val path2 = Path().apply {
            val cx = w * 0.75f
            val cy = h * 0.25f
            val rx = w * 0.18f
            val ry = h * 0.18f
            
            moveTo(cx, cy - ry)
            quadraticTo(cx, cy, cx + rx, cy)
            quadraticTo(cx, cy, cx, cy + ry)
            quadraticTo(cx, cy, cx - rx, cy)
            quadraticTo(cx, cy, cx, cy - ry)
        }
        
        if (isSelected) {
            drawPath(
                path = path1,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFE91E63))
                )
            )
            drawPath(
                path = path2,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF90CAF9), Color(0xFFE1BEE7), Color(0xFFFF80AB))
                )
            )
        } else {
            drawPath(
                path = path1,
                color = Color.Gray.copy(alpha = 0.4f)
            )
            drawPath(
                path = path2,
                color = Color.Gray.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun GroqLogo(isSelected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        
        if (isSelected) {
            // Speed orange base disk glow
            drawCircle(
                color = Color(0xFFFF5722).copy(alpha = 0.12f),
                radius = w * 0.45f,
                center = center
            )
            
            val boltPath = Path().apply {
                moveTo(cx + w * 0.12f, h * 0.12f)
                lineTo(cx - w * 0.22f, h * 0.58f)
                lineTo(cx + w * 0.04f, h * 0.58f)
                lineTo(cx - w * 0.14f, h * 0.90f)
                lineTo(cx + w * 0.26f, h * 0.46f)
                lineTo(cx + 0f, h * 0.46f)
                close()
            }
            
            drawPath(
                path = boltPath,
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFFF3D00), Color(0xFFFF9100))
                )
            )
        } else {
            val boltPath = Path().apply {
                moveTo(cx + w * 0.12f, h * 0.12f)
                lineTo(cx - w * 0.22f, h * 0.58f)
                lineTo(cx + w * 0.04f, h * 0.58f)
                lineTo(cx - w * 0.14f, h * 0.90f)
                lineTo(cx + w * 0.26f, h * 0.46f)
                lineTo(cx + 0f, h * 0.46f)
                close()
            }
            
            drawPath(
                path = boltPath,
                color = Color.Gray.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun OpenAILogo(isSelected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.35f
        
        if (isSelected) {
            drawCircle(
                color = Color(0xFF00A67E).copy(alpha = 0.10f),
                radius = w * 0.48f
            )
            
            for (i in 0 until 6) {
                val angleDegrees = i * 60f
                val angleRad = angleDegrees * (Math.PI / 180.0)
                
                val petalOffset = r * 0.46f
                val px = cx + (petalOffset * Math.cos(angleRad)).toFloat()
                val py = cy + (petalOffset * Math.sin(angleRad)).toFloat()
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00A67E).copy(alpha = 0.85f), Color(0xFF00897B).copy(alpha = 0.20f)),
                        center = Offset(px, py),
                        radius = r * 0.72f
                    ),
                    radius = r * 0.68f,
                    center = Offset(px, py),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.2f)
                )
            }
        } else {
            for (i in 0 until 6) {
                val angleDegrees = i * 60f
                val angleRad = angleDegrees * (Math.PI / 180.0)
                
                val petalOffset = r * 0.46f
                val px = cx + (petalOffset * Math.cos(angleRad)).toFloat()
                val py = cy + (petalOffset * Math.sin(angleRad)).toFloat()
                
                drawCircle(
                    color = Color.Gray.copy(alpha = 0.35f),
                    radius = r * 0.68f,
                    center = Offset(px, py),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f)
                )
            }
        }
    }
}

@Composable
fun NvidiaLogo(isSelected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val maxRadius = w * 0.45f

        if (isSelected) {
            // Draw background halo
            drawCircle(
                color = Color(0xFF76B900).copy(alpha = 0.12f),
                radius = w * 0.48f,
                center = center
            )
            // Beautiful green arc spiral lines winding outwards
            for (i in 1..4) {
                val r = maxRadius * (i / 4f)
                drawArc(
                    brush = Brush.linearGradient(listOf(Color(0xFF76B900), Color(0xFF388E3C))),
                    startAngle = 180f - (i * 15f),
                    sweepAngle = 180f + (i * 20f),
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f)
                )
            }
            // Center core node
            drawCircle(
                color = Color(0xFF76B900),
                radius = 4f * density,
                center = Offset(cx, cy)
            )
        } else {
            // Inactive gray representation
            for (i in 1..4) {
                val r = maxRadius * (i / 4f)
                drawArc(
                    color = Color.Gray.copy(alpha = 0.4f),
                    startAngle = 180f - (i * 15f),
                    sweepAngle = 180f + (i * 20f),
                    useCenter = false,
                    topLeft = Offset(cx - r, cy - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.0f)
                )
            }
        }
    }
}

