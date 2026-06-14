package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.SyncStatus
import com.example.data.local.MessageEntity
import com.example.data.remote.ResearchLink
import com.example.ui.viewmodel.ChatViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val clipboardManager = LocalClipboardManager.current
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var currentlySpeakingMsgId by remember { mutableStateOf<Long?>(null) }
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var editingMessageId by remember { mutableStateOf<Long?>(null) }
    var editingText by remember { mutableStateOf("") }
    val mainHandler = remember { android.os.Handler(android.os.Looper.getMainLooper()) }

    DisposableEffect(context) {
        val ttsInstance = android.speech.tts.TextToSpeech(context) { status -> }
        tts = ttsInstance
        onDispose {
            ttsInstance.stop()
            ttsInstance.shutdown()
        }
    }

    LaunchedEffect(tts, currentlySpeakingMsgId) {
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (currentlySpeakingMsgId.toString() == utteranceId) {
                        currentlySpeakingMsgId = null
                    }
                }
            }
            @Deprecated("Deprecated in Java", ReplaceWith("currentlySpeakingMsgId = null"))
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    if (currentlySpeakingMsgId.toString() == utteranceId) {
                        currentlySpeakingMsgId = null
                    }
                }
            }
        })
    }

    // Observe State flows from VM
    val messages by viewModel.messages.collectAsState()
    val isMessagesLoading by viewModel.isMessagesLoading.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val aiActionStatus by viewModel.aiActionStatus.collectAsState()
    val isEnhancing by viewModel.isEnhancing.collectAsState()
    val convTitle by viewModel.currentConversationTitle.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()
    val darkThemeState by viewModel.isDarkTheme.collectAsState()
    val apiProtocol by viewModel.apiProtocol.collectAsState()
    val apiError by viewModel.apiError.collectAsState()
    val e2bApiKey by viewModel.e2bApiKey.collectAsState()

    // Settings & Knowledge references
    val knowledgeEntries by viewModel.knowledgeEntries.collectAsState()
    val selectedContextEntries by viewModel.selectedContextEntries.collectAsState()
    val fontSizeLevel by viewModel.fontSizeLevel.collectAsState()
    val isSyntaxHighlightingEnabled by viewModel.enableSyntaxHighlighting.collectAsState()
    val messagesLimit by viewModel.messagesLimit.collectAsState()

    // Title editing variables
    var isEditingTitle by remember { mutableStateOf(false) }
    var editedTitleInput by remember { mutableStateOf(convTitle) }

    // Attachment popup triggers
    var isAttachPopupOpen by remember { mutableStateOf(false) }

    // Export history state controller
    var isExportMenuOpen by remember { mutableStateOf(false) }

    var isSearchSettingsSheetOpen by remember { mutableStateOf(false) }
    val activeSearchProvider by viewModel.activeSearchProvider.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val connStatus by viewModel.connectionStatus.collectAsState()
    val messageBusTrigger by com.example.util.DiagnosticLogger.messageBus.collectAsState(initial = 0L)

    // Keep editing title synced
    LaunchedEffect(convTitle) {
        editedTitleInput = convTitle
    }

    // AutoScroll to latest messages on change
    val lastMessageLength = messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(messages.size, lastMessageLength, isAnalyzing, messageBusTrigger) {
        if (messages.isNotEmpty()) {
            if (isAnalyzing) {
                listState.scrollToItem(messages.size - 1)
            } else {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // Moshi deserializer for research links
    val moshi = remember {
        val builder = Moshi.Builder()
        try {
            builder.addLast(KotlinJsonAdapterFactory())
        } catch (t: Throwable) {
            android.util.Log.w("MainChatScreen", "KotlinJsonAdapterFactory not available", t)
        }
        builder.build()
    } //
    val researchListType = remember { Types.newParameterizedType(List::class.java, ResearchLink::class.java) }
    val researchAdapter = remember { moshi.adapter<List<ResearchLink>>(researchListType) }

    // Dynamic scale for text elements based on size configs
    val baseFontSize = when (fontSizeLevel) {
        "Small" -> 12.sp
        "Large" -> 16.sp
        else -> 14.sp
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isEditingTitle) {
                        OutlinedTextField(
                            value = editedTitleInput,
                            onValueChange = { editedTitleInput = it },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(56.dp)
                                .testTag("title_edit_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    editedTitleInput = convTitle
                                    isEditingTitle = true
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        ) {
                            AnimatedProviderLogo(protocol = apiProtocol)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = convTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit title",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                    val isWideScreen = configuration.screenWidthDp >= 720
                    if (!isWideScreen) {
                        IconButton(onClick = { viewModel.toggleSidebar() }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Open sidebar Drawer")
                        }
                    }
                },
                actions = {
                    if (isEditingTitle) {
                        IconButton(
                            onClick = {
                                if (editedTitleInput.isNotBlank() && currentConvId != null) {
                                    viewModel.renameConversation(currentConvId!!, editedTitleInput)
                                }
                                isEditingTitle = false
                            },
                            modifier = Modifier.testTag("submit_title_button")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = "Save title", tint = Color(0xFF4CAF50))
                        }
                    }

                    // Streaming Connection Status Indicator
                    val connTint = when (connStatus) {
                        "Connected" -> Color(0xFF4CAF50)
                        "Connecting..." -> Color(0xFFFFA000)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    Text(
                        text = connStatus,
                        fontSize = 10.sp,
                        color = connTint,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(end = 4.dp)
                    )

                    // Sync Status Indicator
                    val syncIconTint = when (syncStatus) {
                        SyncStatus.SYNCING -> Color(0xFF1E88E5)
                        SyncStatus.SYNCED -> Color(0xFF4CAF50)
                        SyncStatus.ERROR -> Color(0xFFD32F2F)
                        else -> MaterialTheme.colorScheme.outline
                    }
                    val syncIcon = when (syncStatus) {
                        SyncStatus.SYNCING -> Icons.Default.Sync
                        SyncStatus.SYNCED -> Icons.Default.CloudDone
                        SyncStatus.ERROR -> Icons.Default.CloudOff
                        else -> Icons.Default.CloudQueue
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = syncIcon,
                            contentDescription = "Cloud Auth & Sync Status",
                            tint = syncIconTint
                        )
                    }

                    // Provider Switcher Pill Dropdown in Toolbar
                    var isProviderDropdownOpen by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.padding(end = 4.dp).align(Alignment.CenterVertically)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { isProviderDropdownOpen = true }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            AnimatedProviderLogo(protocol = apiProtocol, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = apiProtocol.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Provider",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = isProviderDropdownOpen,
                            onDismissRequest = { isProviderDropdownOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Gemini", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                leadingIcon = { AnimatedProviderLogo("gemini", modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.selectProviderProtocol("gemini")
                                    isProviderDropdownOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Groq", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                leadingIcon = { AnimatedProviderLogo("groq", modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.selectProviderProtocol("groq")
                                    isProviderDropdownOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("NVIDIA NIM", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                leadingIcon = { AnimatedProviderLogo("nvidia", modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    viewModel.selectProviderProtocol("nvidia")
                                    isProviderDropdownOpen = false
                                }
                            )
                        }
                    }

                    // Search toggle
                    IconButton(onClick = { isSearchSettingsSheetOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Settings"
                        )
                    }

                    // Theme toggle
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = if (darkThemeState) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle mode theme"
                        )
                    }

                    // Export button
                    IconButton(onClick = {
                        val markdown = viewModel.exportChatHistoryAsMarkdown()
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, markdown)
                            putExtra(Intent.EXTRA_SUBJECT, "Chat History: $convTitle")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export chat"))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export chat history"
                        )
                    }

                    // Share for Collaboration
                    IconButton(onClick = {
                        val sessionId = viewModel.shareChatSession()
                        if (!sessionId.isNullOrBlank()) {
                            val link = "https://aistudio.collaboration/join/$sessionId"
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Join my chat session: $link")
                                putExtra(Intent.EXTRA_SUBJECT, "Collaborative Chat Session")
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share chat session"))
                        } else {
                            Toast.makeText(context, "Collaboration requires active Firebase configuration.", Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Share for collaboration"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { 
                            if (messages.isNotEmpty()) {
                                isExportMenuOpen = true
                            }
                        }
                    )
                }
        ) {
            // Export DropdownMenu container
            Box(modifier = Modifier.align(Alignment.Center)) {
                DropdownMenu(
                    expanded = isExportMenuOpen,
                    onDismissRequest = { isExportMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Export as Markdown (.md)", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            isExportMenuOpen = false
                            exportConversation(messages, convTitle, asJson = false, context = context)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Export as JSON block (.json)", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        onClick = {
                            isExportMenuOpen = false
                            exportConversation(messages, convTitle, asJson = true, context = context)
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // Collapsible Dynamic Error and Recovery Selector Banner
                AnimatedVisibility(
                    visible = apiError != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    apiError?.let { errText ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            tonalElevation = 4.dp,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "API Error",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "API-Verbindung fehlgeschlagen",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = errText,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.15f))
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    var isErrorDropdownOpen by remember { mutableStateOf(false) }
                                    
                                    // Switch Provider Dropdown in Error Card
                                    Box {
                                        OutlinedButton(
                                            onClick = { isErrorDropdownOpen = true },
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.height(36.dp)
                                        ) {
                                            AnimatedProviderLogo(protocol = apiProtocol, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = "Wechseln (${apiProtocol.uppercase()})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                        }
                                        
                                        DropdownMenu(
                                            expanded = isErrorDropdownOpen,
                                            onDismissRequest = { isErrorDropdownOpen = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Gemini", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                                leadingIcon = { AnimatedProviderLogo("gemini", modifier = Modifier.size(14.dp)) },
                                                onClick = {
                                                    viewModel.selectProviderProtocol("gemini")
                                                    isErrorDropdownOpen = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Groq", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                                leadingIcon = { AnimatedProviderLogo("groq", modifier = Modifier.size(14.dp)) },
                                                onClick = {
                                                    viewModel.selectProviderProtocol("groq")
                                                    isErrorDropdownOpen = false
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("NVIDIA NIM", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) },
                                                leadingIcon = { AnimatedProviderLogo("nvidia", modifier = Modifier.size(14.dp)) },
                                                onClick = {
                                                    viewModel.selectProviderProtocol("nvidia")
                                                    isErrorDropdownOpen = false
                                                }
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.weight(1f))
                                    
                                    TextButton(
                                        onClick = { viewModel.clearApiError() },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                                        ),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text("Schließen", fontSize = 11.sp)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            val lastUserMsg = messages.lastOrNull { it.role == "user" }
                                            if (lastUserMsg != null) {
                                                // Trigger regeneration / retry for last sent message
                                                viewModel.clearApiError()
                                                val lastModelMsg = messages.lastOrNull()
                                                if (lastModelMsg != null && lastModelMsg.role == "model") {
                                                    viewModel.regenerateResponse(lastModelMsg)
                                                } else {
                                                    // Fallback, pretend to regenerate on a pseudo-empty message
                                                    val mockModel = MessageEntity(id = -1, conversationId = lastUserMsg.conversationId, role = "model", content = "")
                                                    viewModel.regenerateResponse(mockModel)
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Wiederholen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Scrollable Chat Window Area
                if (isMessagesLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(3) { index ->
                                val isSkeletonUser = index % 2 == 1
                                MessageBubbleSkeleton(isUser = isSkeletonUser)
                            }
                        }
                    }
                } else if (messages.isEmpty() && !isAnalyzing) {
                    // Visual Empty State Illustration with suggestions
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Ask anything about code or research",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                             Text(
                                text = "Ground suggestions and Interactive Generative UI are active. Ask to create quizzes, timers, flashcards, checklists, or polls!",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = "SUGGESTED DISCOVERY QUERIES",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            // Suggested Starting Widgets
                            listOf(
                                "Create an interactive Quiz about Kotlin Coroutines",
                                "Provide a checklist for launching a production Android App",
                                "best practices for SQLite or Room database in Android",
                                "compare the differences between Gemini 1.5 Flash and Pro"
                            ).forEach { suggestPrompt ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { viewModel.onInputChange(suggestPrompt) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowRightAlt,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = suggestPrompt,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    FlatList(
                        data = messages,
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        key = { it.id },
                        headerContent = {
                            if (messages.size >= messagesLimit) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = { viewModel.loadEarlierMessages() },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowUpward,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Ältere Nachrichten laden", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        },
                        footerContent = {
                            if (isAnalyzing) {
                                val latestMessageIsModelStreaming = messages.lastOrNull()?.let { it.role == "model" && it.content.isNotBlank() } ?: false
                                if (!latestMessageIsModelStreaming) {
                                    item {
                                        AIEngineProcessingIndicator(
                                            status = aiActionStatus,
                                            provider = apiProtocol
                                        )
                                    }
                                }
                            }
                        }
                    ) { msg ->
                        AnimatedMessageItem(messageId = msg.id) {
                            val isUser = msg.role == "user"
                            val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                if (!isUser) {
                                    // Animated Provider Logo Avatar
                                    AnimatedProviderLogo(
                                        protocol = if (msg.provider.isNotBlank()) msg.provider else apiProtocol
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.88f),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                ) {
                                    // Chat bubble Card
                                    Box {
                                        if (editingMessageId == msg.id) {
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (darkThemeState) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    OutlinedTextField(
                                                        value = editingText,
                                                        onValueChange = { editingText = it },
                                                        modifier = Modifier.fillMaxWidth().testTag("edit_text_field_${msg.id}"),
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                                        )
                                                    )
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.End,
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        TextButton(
                                                            onClick = { editingMessageId = null },
                                                            modifier = Modifier.testTag("cancel_edit_${msg.id}")
                                                        ) {
                                                            Text("Cancel", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                                        }
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Button(
                                                            onClick = {
                                                                viewModel.editMessage(msg.id, editingText)
                                                                editingMessageId = null
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                            modifier = Modifier.testTag("save_edit_${msg.id}")
                                                        ) {
                                                            Text("Save", fontSize = 12.sp, color = Color.White)
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isUser) {
                                                    Color(0xFF1E88E5) // Perplexity Blue Accent
                                                } else {
                                                    if (darkThemeState) Color(0xFF1E1E1E) else Color(0xFFF3F4F6)
                                                }
                                            ),
                                            shape = RoundedCornerShape(
                                                topStart = 12.dp,
                                                topEnd = 12.dp,
                                                bottomStart = if (isUser) 12.dp else 2.dp,
                                                bottomEnd = if (isUser) 2.dp else 12.dp
                                            ),
                                            modifier = Modifier
                                                .combinedClickable(
                                                    onLongClick = {
                                                        menuMessage = msg
                                                    },
                                                    onClick = { }
                                                )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                if (isUser) {
                                                    val blocks = remember(msg.content) { splitMessageContent(msg.content) }
                                                    blocks.forEach { block ->
                                                        when (block) {
                                                            is MessageBlock.Text -> {
                                                                if (block.content.isNotBlank()) {
                                                                    MarkwonMarkdownText(
                                                                        markdown = block.content,
                                                                        textColor = Color.White,
                                                                        isDarkTheme = true
                                                                    )
                                                                }
                                                            }
                                                            is MessageBlock.Code -> {
                                                                GenerativeUiContainer(
                                                                    jsonCode = block.code,
                                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                                ) {
                                                                    CodeBlockView(
                                                                        language = block.language,
                                                                        code = block.code,
                                                                        modifier = Modifier.padding(vertical = 4.dp),
                                                                        e2bApiKey = e2bApiKey
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    StreamingTextAnimationWrapper(
                                                        text = msg.content,
                                                        enabled = true
                                                    ) { animatedContent ->
                                                        val isCurrentlyStreaming = msg.id == messages.lastOrNull()?.id && isAnalyzing
                                                        if (isCurrentlyStreaming) {
                                                            val blocks = remember(animatedContent) { splitMessageContent(animatedContent) }
                                                            val infiniteTransition = rememberInfiniteTransition(label = "cursor")
                                                            val cursorAlpha by infiniteTransition.animateFloat(
                                                                initialValue = 0f,
                                                                targetValue = 1f,
                                                                animationSpec = infiniteRepeatable(
                                                                    animation = tween(durationMillis = 500, easing = LinearEasing),
                                                                    repeatMode = RepeatMode.Reverse
                                                                ),
                                                                label = "cursorBlink"
                                                            )
                                                            val showCursor = cursorAlpha > 0.5f

                                                            Column {
                                                                blocks.forEachIndexed { idx, block ->
                                                                    when (block) {
                                                                        is MessageBlock.Text -> {
                                                                            val txt = if (idx == blocks.lastIndex) {
                                                                                block.content + (if (showCursor) " ▎" else "  ")
                                                                            } else {
                                                                                block.content
                                                                            }
                                                                            if (txt.isNotBlank() || idx == blocks.lastIndex) {
                                                                                MarkwonMarkdownText(
                                                                                    markdown = txt,
                                                                                    textColor = MaterialTheme.colorScheme.onSurface,
                                                                                    isDarkTheme = darkThemeState,
                                                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                        is MessageBlock.Code -> {
                                                                            GenerativeUiContainer(
                                                                                jsonCode = block.code,
                                                                                modifier = Modifier.padding(vertical = 4.dp)
                                                                            ) {
                                                                                CodeBlockView(
                                                                                    language = block.language,
                                                                                    code = block.code,
                                                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                                                    e2bApiKey = e2bApiKey
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        } else {
                                                            val blocks = remember(animatedContent) { splitMessageContent(animatedContent) }
                                                            Column {
                                                                blocks.forEach { block ->
                                                                    when (block) {
                                                                        is MessageBlock.Text -> {
                                                                            if (block.content.isNotBlank()) {
                                                                                MarkwonMarkdownText(
                                                                                    markdown = block.content,
                                                                                    textColor = MaterialTheme.colorScheme.onSurface,
                                                                                    isDarkTheme = darkThemeState,
                                                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                        is MessageBlock.Code -> {
                                                                            GenerativeUiContainer(
                                                                                jsonCode = block.code,
                                                                                modifier = Modifier.padding(vertical = 4.dp)
                                                                            ) {
                                                                                CodeBlockView(
                                                                                    language = block.language,
                                                                                    code = block.code,
                                                                                    modifier = Modifier.padding(vertical = 4.dp),
                                                                                    e2bApiKey = e2bApiKey
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        }

                                        DropdownMenu(
                                            expanded = menuMessage?.id == msg.id,
                                            onDismissRequest = { menuMessage = null }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Copy text") },
                                                onClick = {
                                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(msg.content))
                                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                                    menuMessage = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = "Copy text", modifier = Modifier.size(16.dp)) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Delete message") },
                                                onClick = {
                                                    viewModel.deleteMessage(msg.id)
                                                    menuMessage = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp)) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Regenerate response") },
                                                onClick = {
                                                    viewModel.regenerateResponse(msg)
                                                    menuMessage = null
                                                },
                                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = "Regenerate", modifier = Modifier.size(16.dp)) }
                                            )
                                        }
                                    }
 
                                     // Display Research links underneath bubble if active
                                     if (!isUser && msg.researchLinksJson.isNotBlank()) {
                                         val links = remember(msg.researchLinksJson) {
                                             try {
                                                 researchAdapter.fromJson(msg.researchLinksJson) ?: emptyList()
                                             } catch (e: Exception) {
                                                 emptyList()
                                             }
                                         }
 
                                         if (links.isNotEmpty()) {
                                             Spacer(modifier = Modifier.height(8.dp))
                                             Text(
                                                 "REFERENCES",
                                                 fontWeight = FontWeight.Bold,
                                                 fontSize = 9.sp,
                                                 letterSpacing = 0.5.sp,
                                                 color = MaterialTheme.colorScheme.primary,
                                                 modifier = Modifier.padding(bottom = 4.dp)
                                             )
 
                                             // Cards row
                                             LazyRow(
                                                 horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                 modifier = Modifier.fillMaxWidth()
                                             ) {
                                                 items(links) { link ->
                                                     Card(
                                                         shape = RoundedCornerShape(8.dp),
                                                         colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                         modifier = Modifier
                                                             .width(180.dp)
                                                             .clickable {
                                                                 val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url))
                                                                 context.startActivity(intent)
                                                             }
                                                     ) {
                                                         Row(
                                                             modifier = Modifier.padding(8.dp),
                                                             verticalAlignment = Alignment.CenterVertically
                                                         ) {
                                                             AsyncImage(
                                                                 model = link.faviconUrl,
                                                                 contentDescription = null,
                                                                 modifier = Modifier
                                                                     .size(16.dp)
                                                                     .clip(CircleShape)
                                                                     .background(Color.White)
                                                             )
                                                             Spacer(modifier = Modifier.width(6.dp))
                                                             Column {
                                                                 Text(
                                                                     text = link.title,
                                                                     fontSize = 11.sp,
                                                                     fontWeight = FontWeight.Bold,
                                                                     maxLines = 1,
                                                                     overflow = TextOverflow.Ellipsis
                                                                 )
                                                                 Text(
                                                                     text = link.domain,
                                                                     fontSize = 9.sp,
                                                                     color = MaterialTheme.colorScheme.outline,
                                                                     maxLines = 1,
                                                                     overflow = TextOverflow.Ellipsis
                                                                 )
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                         }
                                     }
 
                                     // Subtle Timestamp with interaction bar
                                     Spacer(modifier = Modifier.height(4.dp))
                                     Row(
                                         modifier = Modifier.padding(horizontal = 4.dp),
                                         verticalAlignment = Alignment.CenterVertically
                                     ) {
                                         Text(
                                             text = if (!isUser && msg.latencyMs > 0L) "$timeText  •  ⚡ %.2fs".format(msg.latencyMs / 1000.0) else timeText,
                                             fontSize = 10.sp,
                                             color = MaterialTheme.colorScheme.outline
                                         )
                                         Spacer(modifier = Modifier.width(8.dp))
                                         IconButton(
                                             onClick = {
                                                 editingMessageId = msg.id
                                                 editingText = msg.content
                                             },
                                             modifier = Modifier.size(24.dp).testTag("edit_button_${msg.id}")
                                         ) {
                                             Icon(
                                                 imageVector = Icons.Default.Edit,
                                                 contentDescription = "Edit message content",
                                                 tint = MaterialTheme.colorScheme.outline,
                                                 modifier = Modifier.size(13.dp)
                                             )
                                         }
                                         if (!isUser) {
                                             Spacer(modifier = Modifier.width(8.dp))

                                             IconButton(
                                                 onClick = {
                                                     val shareIntent = Intent().apply {
                                                         action = Intent.ACTION_SEND
                                                         type = "text/plain"
                                                         putExtra(Intent.EXTRA_TEXT, msg.content)
                                                     }
                                                     context.startActivity(Intent.createChooser(shareIntent, "Share AI Response"))
                                                 },
                                                 modifier = Modifier.size(24.dp)
                                             ) {
                                                 Icon(
                                                     imageVector = Icons.Default.Share,
                                                     contentDescription = "Share response",
                                                     tint = MaterialTheme.colorScheme.outline,
                                                     modifier = Modifier.size(13.dp)
                                                 )
                                             }
                                         }
                                     }
                                }
                            }
                        }
                    }

                  }

                // BOTTOM DESIGN BAR: Active Suggestions panel, Input box, contexts selection
                AppChatInput(
                    inputText = inputText,
                    onInputChange = { viewModel.onInputChange(it) },
                    onSend = { viewModel.sendMessage() },
                    onCancel = viewModel::cancelGeneration,
                    isAnalyzing = isAnalyzing,
                    isEnhancing = isEnhancing,
                    onEnhanceClick = { viewModel.enhancePrompt() },
                    selectedContextEntries = selectedContextEntries,
                    onClearContext = { viewModel.clearContextSelections() },
                    onAttachClick = { isAttachPopupOpen = true },
                    onSearchClick = { isSearchSettingsSheetOpen = true },
                    suggestions = suggestions,
                    onSelectSuggestion = { viewModel.appendSuggestion(it) },
                    activeSearchProvider = activeSearchProvider,
                    modifier = Modifier
                )
            }
        }
    }

    // Attachment Context ground dialog popup definition
    if (isAttachPopupOpen) {
        AlertDialog(
            onDismissRequest = { isAttachPopupOpen = false },
            title = {
                Text(
                    "Select Knowledge Context",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        "Tag specific reference documents to pass directly into Gemini AI model's context ground memory.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (knowledgeEntries.isEmpty()) {
                        Text(
                            "No saved documents in your Knowledge Base. Please open Left Sidebar drawer menu to add files.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(knowledgeEntries) { entry ->
                                val isSelected = selectedContextEntries.contains(entry.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { viewModel.toggleContextEntrySelection(entry.id) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleContextEntrySelection(entry.id) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = entry.title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = entry.content.take(60) + "...",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { isAttachPopupOpen = false }) {
                    Text("OK Ground")
                }
            }
        )
    }

    if (isSearchSettingsSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSearchSettingsSheetOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Web Search Engine Settings",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Choose a search engine to power real-time internet research capabilities for AI responses. Ensure you have provided API keys in AI Settings.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Search options toggle
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveSearchProvider("auto") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeSearchProvider == "auto",
                            onClick = { viewModel.setActiveSearchProvider("auto") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Auto (Default)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Fallback to Wikipedia if other APIs are unavailable", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveSearchProvider("tavily") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeSearchProvider == "tavily",
                            onClick = { viewModel.setActiveSearchProvider("tavily") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Tavily AI Search", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Specializes in comprehensive AI-curated research responses", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveSearchProvider("brave") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeSearchProvider == "brave",
                            onClick = { viewModel.setActiveSearchProvider("brave") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Brave Search API", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Fast web-scale traditional search results parsing", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveSearchProvider("wikipedia") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeSearchProvider == "wikipedia",
                            onClick = { viewModel.setActiveSearchProvider("wikipedia") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Wikipedia Free Search API", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Access articles recursively; No API key required.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveSearchProvider("google") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeSearchProvider == "google",
                            onClick = { viewModel.setActiveSearchProvider("google") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Google Search API", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Built-in native Google Search grounding for Gemini", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.setActiveSearchProvider("none") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = activeSearchProvider == "none",
                            onClick = { viewModel.setActiveSearchProvider("none") },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.error)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Disable Web Search", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Queries rely strictly on model knowledge and imported documents", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { isSearchSettingsSheetOpen = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// Beautiful typing loader visual component: 3 elegant bouncy slates
@Composable
fun TypingIndicatorView() {
    val infiniteTransition = rememberInfiniteTransition()

    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0f at 0
                -12f at 150
                0f at 300
            },
            repeatMode = RepeatMode.Restart
        )
    )

    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0f at 150
                -12f at 300
                0f at 450
            },
            repeatMode = RepeatMode.Restart
        )
    )

    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -12f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0f at 300
                -12f at 450
                0f at 600
            },
            repeatMode = RepeatMode.Restart
        )
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        listOf(dot1, dot2, dot3).forEach { offset ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = offset.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun AnimatedMessageItem(
    messageId: Any,
    content: @Composable () -> Unit
) {
    var isVisible by remember(messageId) { mutableStateOf(false) }
    LaunchedEffect(messageId) {
        isVisible = true
    }
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 350, easing = EaseOutCubic)) +
                slideInVertically(
                    initialOffsetY = { 32 },
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
                )
    ) {
        content()
    }
}

// Generic, robust FlatList container styled like list component but fully type-safe in Jetpack Compose
@Composable
fun <T> FlatList(
    data: List<T>,
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    key: ((item: T) -> Any)? = null,
    headerContent: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    footerContent: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    renderItem: @Composable androidx.compose.foundation.lazy.LazyItemScope.(item: T) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
        if (headerContent != null) {
            headerContent()
        }
        items(
            items = data,
            key = key
        ) { item ->
            renderItem(item)
        }
        if (footerContent != null) {
            footerContent()
        }
    }
}

// Custom input component handling multi-line text and send actions
@Composable
fun CustomChatInput(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    isAnalyzing: Boolean,
    isEnhancing: Boolean,
    onEnhanceClick: () -> Unit,
    selectedContextEntries: Set<Long>,
    onClearContext: () -> Unit,
    onAttachClick: () -> Unit,
    onSearchClick: () -> Unit,
    suggestions: List<String>,
    onSelectSuggestion: (String) -> Unit,
    activeSearchProvider: String,
    modifier: Modifier = Modifier
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Suggestions dropdown list panel (visible while user typing matches)
        AnimatedVisibility(visible = suggestions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(12.dp)
                    )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .padding(vertical = 4.dp)
                ) {
                    items(suggestions) { completion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectSuggestion(completion) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = completion,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Web Search active visual indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSearchClick() }
                .padding(vertical = 2.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val (iconTint, textStr) = when (activeSearchProvider) {
                "tavily" -> Color(0xFF9C27B0) to "Tavily Search Active"
                "brave" -> Color(0xFFFF5722) to "Brave Search Active"
                "wikipedia" -> Color(0xFF000000) to "Wikipedia Search Active"
                "google" -> Color(0xFF4285F4) to "Google Search Grounding"
                "none" -> MaterialTheme.colorScheme.outline to "Web Search Disabled"
                else -> Color(0xFF1E88E5) to "Auto Search (Wikipedia/Tavily/etc)" 
            }
            Icon(
                imageVector = Icons.Default.Public,
                contentDescription = "Search Provider Indicator",
                tint = if (activeSearchProvider == "wikipedia") MaterialTheme.colorScheme.onSurface else iconTint,
                modifier = Modifier.size(12.dp)
            )
            Text(
                textStr,
                fontSize = 11.sp,
                color = if (activeSearchProvider == "wikipedia") MaterialTheme.colorScheme.onSurface else iconTint,
                fontWeight = FontWeight.Medium
            )
        }

        // Display active Context items grounded
        AnimatedVisibility(visible = selectedContextEntries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "Grounded on ${selectedContextEntries.size} knowledge source(s)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "(Clear)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onClearContext() }
                )
            }
        }

        // Core Chat Box Text Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Context reference attacher
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.testTag("attach_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Context ground templates",
                    tint = if (selectedContextEntries.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                placeholder = { Text("Ask anything about code or research...", fontSize = 13.sp) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 120.dp)
                    .testTag("chat_input_text"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 6, // Supports beautiful multiline expanding content!
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                trailingIcon = {
                    if (inputText.isNotBlank()) {
                        IconButton(
                            onClick = onEnhanceClick,
                            enabled = !isEnhancing && !isAnalyzing,
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isEnhancing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Enhance Prompt",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Send trigger
            IconButton(
                onClick = {
                    if (isAnalyzing) {
                        onCancel()
                    } else {
                        onSend()
                        focusManager.clearFocus()
                    }
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isAnalyzing) Color(0xFFD32F2F)
                        else if (inputText.isNotBlank()) Color(0xFF1E88E5)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = if (isAnalyzing) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (isAnalyzing) "Cancel" else "Send message",
                    tint = if (isAnalyzing || inputText.isNotBlank()) Color.White else MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

// Sparkle/Shimmer pulse effect for smooth message skeleton overlays
@Composable
fun Modifier.shimmerEffect(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    return this.background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
}

@Composable
fun MessageBubbleSkeleton(
    isUser: Boolean,
    showTypingDots: Boolean = false,
    provider: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            AnimatedProviderLogo(protocol = provider)
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.88f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) {
                        Color(0xFF1E88E5).copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    }
                ),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 2.dp,
                    bottomEnd = if (isUser) 2.dp else 12.dp
                ),
                modifier = Modifier.width(if (isUser) 160.dp else 240.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isUser) 0.82f else 0.7f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isUser) 0.5f else 0.9f)
                            .height(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .shimmerEffect()
                    )
                    if (!isUser) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.52f)
                                .height(13.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerEffect()
                        )
                    }
                }
            }
            if (showTypingDots) {
                Spacer(modifier = Modifier.height(2.dp))
                TypingIndicatorView()
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .shimmerEffect()
            )
        }
    }
}

fun exportConversation(
    messages: List<MessageEntity>,
    title: String,
    asJson: Boolean,
    context: android.content.Context
) {
    if (messages.isEmpty()) {
        Toast.makeText(context, "No chat history to export.", Toast.LENGTH_SHORT).show()
        return
    }

    val contentToSend = if (asJson) {
        val list = messages.map { msg ->
            mapOf(
                "id" to msg.id.toString(),
                "role" to msg.role,
                "timestamp" to msg.timestamp.toString(),
                "content" to msg.content
            )
        }
        try {
            val moshi = Moshi.Builder().let { builder ->
                try {
                    builder.addLast(KotlinJsonAdapterFactory())
                } catch (t: Throwable) {
                    android.util.Log.w("MainChatScreen", "KotlinJsonAdapterFactory not available", t)
                }
                builder.build()
            }
            val adapter = moshi.adapter<List<Map<String, String>>>(
                Types.newParameterizedType(List::class.java, Map::class.java)
            ).indent("  ")
            adapter.toJson(list)
        } catch (e: Exception) {
            "[\n" + list.joinToString(",\n") { msgMap ->
                "  {\n" +
                        "    \"role\": \"${msgMap["role"]}\",\n" +
                        "    \"content\": \"${msgMap["content"]?.replace("\"", "\\\"")?.replace("\n", "\\n")}\"\n" +
                        "  }"
            } + "\n]"
        }
    } else {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val header = "# Conversation: $title\nExported on: ${sdf.format(Date())}\n\n---\n\n"
        header + messages.joinToString("\n\n---\n\n") { msg ->
            val speaker = if (msg.role == "model") "**AI ASSISTANT**" else "**USER**"
            "$speaker:\n${msg.content}"
        }
    }

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Chat History: $title")
            putExtra(Intent.EXTRA_TEXT, contentToSend)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Export Chat History via")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun TypingAnimatedTextWrapper(
    text: String,
    isModel: Boolean,
    msgTimestamp: Long,
    content: @Composable (String) -> Unit
) {
    // Only animate if it's from the model and was created within the last 5 seconds
    val isNew = remember { System.currentTimeMillis() - msgTimestamp < 5000 }
    if (!isModel || !isNew) {
        content(text)
        return
    }

    var displayedLength by remember { mutableStateOf(0) }

    LaunchedEffect(text) {
        if (displayedLength < text.length) {
            val lengthToAnimate = text.length - displayedLength
            val delayPerChar = (2500L / text.length.coerceAtLeast(1)).coerceIn(5L, 30L)
            for (i in displayedLength..text.length) {
                displayedLength = i
                kotlinx.coroutines.delay(delayPerChar)
            }
        }
    }

    content(text.substring(0, displayedLength))
}

@Composable
fun StreamingTextAnimationWrapper(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (String) -> Unit
) {
    if (!enabled || text.isEmpty()) {
        content(text)
        return
    }

    var displayedText by remember { mutableStateOf("") }

    LaunchedEffect(text) {
        if (!text.startsWith(displayedText)) {
            displayedText = ""
        }
        
        while (displayedText.length < text.length) {
            val nextLength = (displayedText.length + 1).coerceAtMost(text.length)
            displayedText = text.substring(0, nextLength)
            val distance = text.length - displayedText.length
            val delayMs = if (distance > 50) 2L else if (distance > 20) 8L else 20L
            kotlinx.coroutines.delay(delayMs)
        }
    }

    content(displayedText)
}

