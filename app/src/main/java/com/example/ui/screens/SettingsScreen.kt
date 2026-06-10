package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.viewmodel.AccentColor
import com.example.ui.viewmodel.ChatViewModel
import com.example.data.remote.SyncStatus
import com.example.ui.viewmodel.Screen
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    // Observe Live VM States
    val apiEndpointState by viewModel.apiEndpoint.collectAsState()
    val apiKeyState by viewModel.apiKey.collectAsState()
    val selectedModelState by viewModel.selectedModel.collectAsState()
    val temperatureState by viewModel.temperature.collectAsState()
    val maxTokensState by viewModel.maxTokens.collectAsState()
    val accentColorState by viewModel.accentColor.collectAsState()
    val autoSuggestState by viewModel.autoSuggestEnabled.collectAsState()
    val showResearchState by viewModel.showResearchLinks.collectAsState()
    val enableHighlightState by viewModel.enableSyntaxHighlighting.collectAsState()
    val fontSizeState by viewModel.fontSizeLevel.collectAsState()
    val darkThemeState by viewModel.isDarkTheme.collectAsState()

    // Validation VM States
    val isTestingConnection by viewModel.isValidating.collectAsState()
    val validationResult by viewModel.validationResult.collectAsState()

    // Local inputs for editing prior to "Save"
    var apiEndpoint by remember { mutableStateOf(apiEndpointState) }
    var apiKey by remember { mutableStateOf(apiKeyState) }
    var selectedModel by remember { mutableStateOf(selectedModelState) }
    var tempVal by remember { mutableStateOf(temperatureState) }
    var maxTokens by remember { mutableStateOf(maxTokensState.toString()) }
    var selectedAccent by remember { mutableStateOf(accentColorState) }
    var autoSuggest by remember { mutableStateOf(autoSuggestState) }
    var showResearch by remember { mutableStateOf(showResearchState) }
    var enableHighlight by remember { mutableStateOf(enableHighlightState) }

    val syncStatus by viewModel.syncStatus.collectAsState()
    var fontSizeLevel by remember { mutableStateOf(fontSizeState) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // Sync local edits if VM state changes
    LaunchedEffect(apiEndpointState, apiKeyState, selectedModelState, temperatureState, maxTokensState, accentColorState) {
        apiEndpoint = apiEndpointState
        apiKey = apiKeyState
        selectedModel = selectedModelState
        tempVal = temperatureState
        maxTokens = maxTokensState.toString()
        selectedAccent = accentColorState
    }

    // Dropdown Models list
    var modelDropdownExpanded by remember { mutableStateOf(false) }
    val availableModels = listOf("gemini-3.5-flash", "gemini-3.1-pro-preview", "gemini-3.1-flash-lite-preview")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        TopAppBar(
            title = {
                Text(
                    "Settings",
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
            // SECTION 1: AI Configurations
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI CONFIGURATIONS",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Target API Endpoint
                    OutlinedTextField(
                        value = apiEndpoint,
                        onValueChange = {
                            apiEndpoint = it
                            viewModel.clearValidationResult()
                        },
                        label = { Text("API Endpoint URL") },
                        placeholder = { Text("https://generativelanguage.googleapis.com/") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )

                    // live validation indicators
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                viewModel.testConnection(apiEndpoint, apiKey)
                            },
                            enabled = !isTestingConnection,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
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

                        // Test Outcome Display
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

                    // Test failure log
                    validationResult?.takeIf { it != "SUCCESS" }?.let { errorMsg ->
                        Text(
                            text = errorMsg,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Masked API Key Input
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("Enter Google Gemini API Key") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val icon = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(imageVector = icon, contentDescription = "Toggle key display")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    // Model Selection Downward Trigger
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Generative Model Alias") },
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
                            availableModels.forEach { name ->
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Temperature Configuration Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Response Temperature", fontSize = 13.sp)
                        Text(String.format("%.1f", tempVal), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = tempVal,
                        onValueChange = { tempVal = it },
                        valueRange = 0.0f..1.0f,
                        steps = 9,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Limit Tokens Size Validate
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = {
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                maxTokens = it
                            }
                        },
                        label = { Text("Max Token Limits") },
                        placeholder = { Text("2048") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }

            // SECTION 2: Appearance Configurations
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "APPEARANCE & THEMES",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Dark theme toggle synced
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dark Mode Theme", fontSize = 14.sp)
                        Switch(
                            checked = darkThemeState,
                            onCheckedChange = { viewModel.toggleTheme() }
                        )
                    }

                    // Font Size Options Select
                    Text("Response Font Scale", fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Small", "Medium", "Large").forEach { size ->
                            val isActive = fontSizeLevel == size
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(if (isActive) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { fontSizeLevel = size },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = size,
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Accent Colors side-by-side selective checks
                    Text("Primary Branded Accent", fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AccentColor.values().forEach { colorOption ->
                            val isSelected = selectedAccent == colorOption
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(colorOption.hex)))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAccent = colorOption },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: Behavior Configurations
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "RESEARCH DISPATCH BEHAVIORS",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Auto-suggest
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-suggest queries", fontSize = 14.sp)
                            Text("Suggest common/related completions as you type", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoSuggest,
                            onCheckedChange = { autoSuggest = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Research links
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Research links in responses", fontSize = 14.sp)
                            Text("Generates rich card references below AI output", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = showResearch,
                            onCheckedChange = { showResearch = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inline syntax highlighting
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable code syntax highlighting", fontSize = 14.sp)
                            Text("Renders codes inside colored styled panels", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = enableHighlight,
                            onCheckedChange = { enableHighlight = it }
                        )
                    }
                }
            }

            // SECTION 4: Cloud Sync & Authentication
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "CLOUD SYNC & AUTHENTICATION",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusEmoji = when (syncStatus) {
                            SyncStatus.SYNCED -> "✅ Synced to Firestore"
                            SyncStatus.SYNCING -> "🔄 Synchronizing..."
                            SyncStatus.OFFLINE -> "🚫 Offline / Not logged in"
                            SyncStatus.ERROR -> "❌ Sync Error"
                            SyncStatus.NOT_CONFIGURED -> "⚠️ Firebase Not Initialized"
                        }
                        Text(text = "Status: ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(text = statusEmoji, fontSize = 14.sp, color = if (syncStatus == SyncStatus.SYNCED) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)
                    }

                    if (syncStatus == SyncStatus.NOT_CONFIGURED) {
                        Text(
                            text = "To enable real Cloud Firestore sync and Auth across devices, you must provide a valid google-services.json file to your Android project.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    } else if (syncStatus == SyncStatus.OFFLINE) {
                        Button(
                            onClick = { /* Trigger Google Auth Sign In */ },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE3F2FD), contentColor = Color.Black)
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = "Sign in")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In With Google")
                        }
                    } else if (syncStatus == SyncStatus.SYNCED || syncStatus == SyncStatus.SYNCING) {
                        OutlinedButton(
                            onClick = { /* Sign out */ },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign Out")
                        }
                    }
                }
            }
            
            // SECTION 5: Network Diagnostics
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "NETWORK DIAGNOSTICS",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Text(
                        text = "Test connectivity without saving to the local database.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedButton(
                        onClick = { viewModel.sendDiagnosticPing() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = "Ping")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Dummy 'Ping' Request")
                    }
                }
            }

            // Action: Save validated configuration
            var showUrlError by remember { mutableStateOf(false) }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    // URL formatting and validate checks
                    val modelTokens = maxTokens.toIntOrNull() ?: 2048
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
                            viewModel.updateSettings(
                                endpoint = apiEndpoint,
                                key = apiKey,
                                modelName = selectedModel,
                                temp = tempVal,
                                maxToks = modelTokens,
                                color = selectedAccent,
                                autoSuggest = autoSuggest,
                                showResearch = showResearch,
                                enableHighlight = enableHighlight,
                                fontLevel = fontSizeLevel
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
                Text("Save and Apply Settings", fontWeight = FontWeight.Bold)
            }

            AnimatedVisibility(visible = showUrlError) {
                Text(
                    text = "⚠️ Malformed API Endpoint. Must be a valid full protocol URL (e.g. https://generativelanguage.googleapis.com/).",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
