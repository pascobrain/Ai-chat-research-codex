package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
    val convTitle by viewModel.currentConversationTitle.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()
    val darkThemeState by viewModel.isDarkTheme.collectAsState()

    // Settings & Knowledge references
    val knowledgeEntries by viewModel.knowledgeEntries.collectAsState()
    val selectedContextEntries by viewModel.selectedContextEntries.collectAsState()
    val fontSizeLevel by viewModel.fontSizeLevel.collectAsState()
    val isSyntaxHighlightingEnabled by viewModel.enableSyntaxHighlighting.collectAsState()

    // Title editing variables
    var isEditingTitle by remember { mutableStateOf(false) }
    var editedTitleInput by remember { mutableStateOf(convTitle) }

    // Attachment popup triggers
    var isAttachPopupOpen by remember { mutableStateOf(false) }

    // Export history state controller
    var isExportMenuOpen by remember { mutableStateOf(false) }

    // Keep editing title synced
    LaunchedEffect(convTitle) {
        editedTitleInput = convTitle
    }

    // AutoScroll to latest messages on change
    LaunchedEffect(messages.size, isAnalyzing) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Moshi deserializer for research links
    val moshi = remember { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
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
                    IconButton(onClick = { viewModel.toggleSidebar() }) {
                        Icon(imageVector = Icons.Default.Menu, contentDescription = "Open sidebar Drawer")
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

                    // Theme toggle
                    IconButton(onClick = { viewModel.toggleTheme() }) {
                        Icon(
                            imageVector = if (darkThemeState) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle mode theme"
                        )
                    }

                    // Export dialogue option
                    Box {
                        IconButton(
                            onClick = { isExportMenuOpen = true },
                            enabled = messages.isNotEmpty(),
                            modifier = Modifier.testTag("export_conversation_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Export History Options"
                            )
                        }

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
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
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
                                text = "Ground suggestions are dynamically active. Tap below or type code prompts to begin searching.",
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
                                "explain differences between Gemini 1.5 Flash and Pro",
                                "best practices for SQLite or Room database in Android",
                                "how to structure clean architecture with MVVM in Kotlin"
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
                        footerContent = {
                            if (isAnalyzing) {
                                item {
                                    MessageBubbleSkeleton(isUser = false, showTypingDots = true)
                                }
                            }
                        }
                    ) { msg ->
                            val isUser = msg.role == "user"
                            val timeText = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(msg.timestamp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                if (!isUser) {
                                    // AI Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                Column(
                                    modifier = Modifier.fillMaxWidth(if (isUser) 0.82f else 0.88f),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                ) {
                                    // Chat bubble Card
                                    Box {
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
                                                    Text(
                                                        text = msg.content,
                                                        color = Color.White,
                                                        fontSize = baseFontSize,
                                                        lineHeight = 20.sp
                                                    )
                                                } else {
                                                    // Dynamic Code/Text block split parsing renderer
                                                    val messageBlocks = splitMessageContent(msg.content)
                                                    messageBlocks.forEach { block ->
                                                        when (block) {
                                                            is MessageBlock.Text -> {
                                                                MarkdownText(
                                                                    text = block.content,
                                                                    isDark = darkThemeState,
                                                                    baseFontSize = baseFontSize,
                                                                    textColor = MaterialTheme.colorScheme.onSurface,
                                                                    modifier = Modifier.padding(bottom = 6.dp)
                                                                )
                                                            }
                                                            is MessageBlock.Code -> {
                                                                if (isSyntaxHighlightingEnabled) {
                                                                    CodeBlockView(
                                                                        language = block.language,
                                                                        code = block.code,
                                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                                    )
                                                                } else {
                                                                    // Monospace fallback
                                                                     Box(
                                                                        modifier = Modifier
                                                                            .fillMaxWidth()
                                                                            .background(Color.Black.copy(alpha = 0.08f))
                                                                            .padding(8.dp)
                                                                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                                                                    ) {
                                                                        Text(
                                                                            text = block.code,
                                                                            fontSize = 13.sp,
                                                                            fontFamily = FontFamily.Monospace
                                                                        )
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
                                             text = timeText,
                                             fontSize = 10.sp,
                                             color = MaterialTheme.colorScheme.outline
                                         )
                                         if (!isUser) {
                                             Spacer(modifier = Modifier.width(8.dp))
                                             val isSpeaking = currentlySpeakingMsgId == msg.id
                                             IconButton(
                                                 onClick = {
                                                     val ttsInstance = tts
                                                     if (ttsInstance != null) {
                                                         if (isSpeaking) {
                                                             ttsInstance.stop()
                                                             currentlySpeakingMsgId = null
                                                         } else {
                                                             ttsInstance.stop()
                                                             currentlySpeakingMsgId = msg.id
                                                             val params = android.os.Bundle().apply {
                                                                 putString(android.speech.tts.TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, msg.id.toString())
                                                             }
                                                             val cleanSpeechText = msg.content
                                                                 .replace(Regex("\\[([^\\]]+)\\]\\((https?://[^\\s)]+)\\)"), "$1")
                                                                 .replace(Regex("[`*#_]"), "")
                                                             ttsInstance.speak(
                                                                 cleanSpeechText,
                                                                 android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                                                                 params,
                                                                 msg.id.toString()
                                                             )
                                                         }
                                                     } else {
                                                         Toast.makeText(context, "Text to speech not initialized", Toast.LENGTH_SHORT).show()
                                                     }
                                                 },
                                                 modifier = Modifier.size(24.dp)
                                             ) {
                                                 Icon(
                                                     imageVector = if (isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeMute,
                                                     contentDescription = "Speak response",
                                                     tint = if (isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                     modifier = Modifier.size(14.dp)
                                                 )
                                             }

                                             Spacer(modifier = Modifier.width(4.dp))

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

                // BOTTOM DESIGN BAR: Active Suggestions panel, Input box, contexts selection
                CustomChatInput(
                    inputText = inputText,
                    onInputChange = { viewModel.onInputChange(it) },
                    onSend = { viewModel.sendMessage() },
                    isAnalyzing = isAnalyzing,
                    selectedContextEntries = selectedContextEntries,
                    onClearContext = { viewModel.clearContextSelections() },
                    onAttachClick = { isAttachPopupOpen = true },
                    suggestions = suggestions,
                    onSelectSuggestion = { viewModel.appendSuggestion(it) },
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

// Generic, robust FlatList container styled like list component but fully type-safe in Jetpack Compose
@Composable
fun <T> FlatList(
    data: List<T>,
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    key: ((item: T) -> Any)? = null,
    footerContent: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    renderItem: @Composable androidx.compose.foundation.lazy.LazyItemScope.(item: T) -> Unit
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement
    ) {
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
    isAnalyzing: Boolean,
    selectedContextEntries: Set<Long>,
    onClearContext: () -> Unit,
    onAttachClick: () -> Unit,
    suggestions: List<String>,
    onSelectSuggestion: (String) -> Unit,
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
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Send trigger
            IconButton(
                onClick = {
                    onSend()
                    focusManager.clearFocus()
                },
                enabled = inputText.isNotBlank() && !isAnalyzing,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isAnalyzing) Color(0xFF1E88E5)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send message",
                    tint = if (inputText.isNotBlank() && !isAnalyzing) Color.White else MaterialTheme.colorScheme.outline
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
fun MessageBubbleSkeleton(isUser: Boolean, showTypingDots: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
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
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
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

