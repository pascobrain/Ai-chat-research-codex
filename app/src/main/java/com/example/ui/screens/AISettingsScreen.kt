package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.Screen
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
    val selectedModelState by viewModel.selectedModel.collectAsState()
    val apiProtocolState by viewModel.apiProtocol.collectAsState()
    val systemPromptState by viewModel.systemPrompt.collectAsState()
    val temperatureState by viewModel.temperature.collectAsState()
    val maxTokensState by viewModel.maxTokens.collectAsState()

    // Connection testing
    val isTestingConnection by viewModel.isValidating.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()

    // Local state managers
    var apiProtocol by remember { mutableStateOf(apiProtocolState) }
    var apiEndpoint by remember { mutableStateOf(apiEndpointState) }
    var apiKey by remember { mutableStateOf(apiKeyState) }
    var selectedModel by remember { mutableStateOf(selectedModelState) }
    var systemPrompt by remember { mutableStateOf(systemPromptState) }
    var tempVal by remember { mutableStateOf(temperatureState) }
    var maxTokens by remember { mutableStateOf(maxTokensState.toString()) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // Sync state
    LaunchedEffect(apiProtocolState, apiEndpointState, apiKeyState, selectedModelState, systemPromptState, temperatureState, maxTokensState) {
        apiProtocol = apiProtocolState
        apiEndpoint = apiEndpointState
        apiKey = apiKeyState
        selectedModel = selectedModelState
        systemPrompt = systemPromptState
        tempVal = temperatureState
        maxTokens = maxTokensState.toString()
    }

    // Dropdown expanding state
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    val geminiModels = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-3.1-flash-lite-preview")

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
            // PROTOCOL SELECTOR CARDS
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI PROVIDER PROTOCOL",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Gemini Button Option
                        val isGemini = apiProtocol == "gemini"
                        Button(
                            onClick = {
                                apiProtocol = "gemini"
                                apiEndpoint = "https://generativelanguage.googleapis.com/"
                                selectedModel = "gemini-3.5-flash"
                                viewModel.clearValidationResult()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isGemini) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isGemini) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Gemini API", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        // OpenAI Option
                        val isOpenAI = apiProtocol == "openai"
                        Button(
                            onClick = {
                                apiProtocol = "openai"
                                apiEndpoint = "https://api.openai.com/v1/"
                                selectedModel = "gpt-4o"
                                viewModel.clearValidationResult()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOpenAI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                contentColor = if (isOpenAI) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OpenAI Compat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // CONNECTION AND KEY PANEL
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "CONNECTION CONFIGURATIONS",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Target Custom API Endpoint
                    OutlinedTextField(
                        value = apiEndpoint,
                        onValueChange = {
                            apiEndpoint = it
                            viewModel.clearValidationResult()
                        },
                        label = { Text("API Endpoint Service URL") },
                        placeholder = {
                            if (apiProtocol == "openai") Text("https://api.openai.com/v1/")
                            else Text("https://generativelanguage.googleapis.com/")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )

                    // Masked API Key setting Input
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key Settings") },
                        placeholder = { Text("Enter private credentials...") },
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
                            .padding(bottom = 12.dp)
                    )

                    // Generative Model Name Configuration
                    if (apiProtocol == "openai") {
                        // Custom standard input field freeform for custom OpenAI models
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { selectedModel = it },
                            label = { Text("Generative Model Alias (Standard/Custom)") },
                            placeholder = { Text("gpt-4o") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    } else {
                        // Dropdown choice limit for Gemini models
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            OutlinedTextField(
                                value = selectedModel,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Generative Model (Gemini)") },
                                trailingIcon = {
                                    IconButton(onClick = { modelDropdownExpanded = true }) {
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Dropdown models")
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { modelDropdownExpanded = true }
                            )

                            DropdownMenu(
                                expanded = modelDropdownExpanded,
                                onDismissRequest = { modelDropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth(0.9f)
                            ) {
                                geminiModels.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name, fontSize = 14.sp) },
                                        onClick = {
                                            selectedModel = name
                                            modelDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Connection test triggers
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
                                viewModel.testConnection(apiEndpoint, apiKey, apiProtocol)
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
                                Text("Test Connection", fontSize = 12.sp)
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
                                    Text("Ping Success", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Ping Failed", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            .padding(top = 8.dp)
                    )
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
                                key = apiKey,
                                modelName = selectedModel,
                                protocol = apiProtocol,
                                systemPromptText = systemPrompt,
                                temp = tempVal,
                                maxToks = parsedLimit
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
                    text = "⚠️ Malformed API Endpoint. Setup requires a full valid address protocol (e.g. https://api.openai.com/v1/).",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
