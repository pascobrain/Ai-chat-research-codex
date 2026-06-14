package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppChatInput(
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
    val infiniteTransition = rememberInfiniteTransition(label = "input_infinite")
    val isAnalyzingPulseScale by if (isAnalyzing) {
        infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        remember { mutableStateOf(1.0f) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Horizontally Scrollable Fast-Action Chips Row (Intuitive workflows)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Pair("📝 Zusammenfassen", "Bitte fasse folgendes präzise zusammen: "),
                Pair("🔍 Grammatik prüfen", "Bitte korrigiere die Grammatik und verbessere den Schreibstil für: "),
                Pair("💻 Code erklären", "Bitte erkläre die Logik und Funktion des folgenden Codes: "),
                Pair("🎓 Quiz erstellen", "Bitte erstelle ein kurzes, interaktives Quiz zum Thema: "),
                Pair("📋 Checkliste erstellen", "Bitte erstelle eine detaillierte Schritt-für-Schritt Checkliste für: ")
            ).forEach { (label, promptTemplate) ->
                AssistChip(
                    onClick = { onInputChange(promptTemplate) },
                    label = { Text(label, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium) },
                    shape = RoundedCornerShape(12.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Suggestions and Slash Commands Floating Panel
        val slashCommands = remember {
            listOf(
                Triple("/prompt-db", "Datenbank-Entwurf generieren", "Entwirft SQLite Tabellen & Insert-Muster aus dem bisherigen Chatverlauf."),
                Triple("/summarize", "Chat zusammenfassen", "Erstellt eine strukturierte Zusammenfassung der Konversation."),
                Triple("/help", "Hilfe anzeigen", "Zeigt eine Erläuterung aller verfügbaren Slash-Befehle."),
                Triple("/clear", "Chatverlauf leeren", "Löscht den gesamten Verlauf der aktuellen Konversation."),
                Triple("/diagnostic", "Ping-Test durchführen", "Prüft die API-Verbindung mit einem Ping-Pong-Test.")
            )
        }

        val matchingSlashCommands = remember(inputText) {
            if (inputText.startsWith("/")) {
                slashCommands.filter { it.first.startsWith(inputText, ignoreCase = true) }
            } else {
                emptyList()
            }
        }

        AnimatedVisibility(
            visible = matchingSlashCommands.isNotEmpty() || (inputText.isNotBlank() && suggestions.isNotEmpty()),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = if (matchingSlashCommands.isNotEmpty()) "⚡ SLASH-BEFEHLE (SHORTCUTS)" else "💡 VORSCHLÄGE (AUTO-COMPLETE)",
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.75.sp,
                        modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        if (matchingSlashCommands.isNotEmpty()) {
                            items(matchingSlashCommands) { (cmd, title, desc) ->
                                val cmdIcon = when (cmd) {
                                    "/prompt-db" -> Icons.Default.Storage
                                    "/summarize" -> Icons.Default.Description
                                    "/clear" -> Icons.Default.DeleteSweep
                                    "/diagnostic" -> Icons.Default.Dns
                                    else -> Icons.Default.Help
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onInputChange(cmd) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = cmdIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cmd,
                                            fontSize = 14.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = title,
                                            fontSize = 11.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = desc,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(suggestions) { completion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSelectSuggestion(completion) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = completion,
                                        fontSize = 13.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Nachricht schreiben...", fontSize = 14.sp) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onEnhanceClick,
                            enabled = !isAnalyzing && !isEnhancing,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isEnhancing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.25.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = if (isEnhancing) "Verbessere..." else "Prompt optimieren",
                                    fontSize = 12.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (selectedContextEntries.isNotEmpty()) {
                        AssistChip(
                            onClick = onClearContext,
                            label = { Text("Kontext löschen (${selectedContextEntries.size})", fontSize = 10.sp) },
                            shape = CircleShape,
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f),
                                labelColor = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (isAnalyzing) {
                        onCancel()
                    } else {
                        onSend()
                    }
                },
                enabled = inputText.isNotBlank() || isAnalyzing,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .graphicsLayer {
                        scaleX = isAnalyzingPulseScale
                        scaleY = isAnalyzingPulseScale
                    }
                    .background(
                        if (isAnalyzing) MaterialTheme.colorScheme.error
                        else if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .testTag("send_button")
            ) {
                Icon(
                    imageVector = if (isAnalyzing) Icons.Default.Stop else Icons.Default.Send,
                    contentDescription = if (isAnalyzing) "Abbrechen" else "Senden",
                    tint = if (isAnalyzing || inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
