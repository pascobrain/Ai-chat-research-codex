package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

sealed interface MessageBlock {
    data class Text(val content: String) : MessageBlock
    data class Code(val language: String, val code: String) : MessageBlock
}

fun splitMessageContent(content: String): List<MessageBlock> {
    if (content.isBlank()) return listOf(MessageBlock.Text(""))
    val blocks = mutableListOf<MessageBlock>()
    val pattern = Pattern.compile("```(\\w*)\\n(.*?)(\\n```|$)", Pattern.DOTALL)
    val matcher = pattern.matcher(content)
    var lastIndex = 0

    while (matcher.find()) {
        val textBefore = content.substring(lastIndex, matcher.start())
        if (textBefore.isNotEmpty()) {
            blocks.add(MessageBlock.Text(textBefore))
        }
        val language = matcher.group(1)?.trim() ?: ""
        var code = matcher.group(2) ?: ""
        // Strip trailing backticks if any
        if (code.endsWith("```")) {
            code = code.dropLast(3)
        }
        blocks.add(MessageBlock.Code(language, code))
        lastIndex = matcher.end()
    }

    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex)
        if (remaining.isNotEmpty()) {
            blocks.add(MessageBlock.Text(remaining))
        }
    }

    // Fallback if no matching code blocks found
    if (blocks.isEmpty()) {
        blocks.add(MessageBlock.Text(content))
    }
    return blocks
}

@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val isDark = true // Code blocks are always styled in premium dark mode

    val panelBackground = Color(0xFF1E1E1E)
    val headerBackground = Color(0xFF2D2D2D)
    val textColor = Color(0xFFD4D4D4)
    val accentColor = Color(0xFF3B82F6) // Electric blue highlights

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(panelBackground)
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBackground)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase().ifBlank { "CODE" },
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Code", code)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = Color(0xFF9CA3AF),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(14.dp)
            ) {
                Text(
                    text = highlightCode(code, language),
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// Simple, fast syntax highlighting parser for beautiful developer presentations
fun highlightCode(code: String, language: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(code)
        
        // Match Kotlin/Java/JS types of words
        val keywordsPattern = Pattern.compile(
            "\\b(val|var|fun|class|interface|object|return|import|package|class|private|public|protected|override|suspend|if|else|when|for|while|in|data|null|true|false|this|async|await|const|let|function)\\b"
        )
        val matcherKeywords = keywordsPattern.matcher(code)
        while (matcherKeywords.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold),
                start = matcherKeywords.start(),
                end = matcherKeywords.end()
            )
        }

        // Highlight strings "hello" or 'hello'
        val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'")
        val matcherStrings = stringPattern.matcher(code)
        while (matcherStrings.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFCE9178)),
                start = matcherStrings.start(),
                end = matcherStrings.end()
            )
        }

        // Highlight custom comments
        val commentPattern = Pattern.compile("//.*|/\\*[^*]*\\*/")
        val matcherComments = commentPattern.matcher(code)
        while (matcherComments.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFF6A9955)),
                start = matcherComments.start(),
                end = matcherComments.end()
            )
        }

        // Highlight integers and numbers
        val numbersPattern = Pattern.compile("\\b(\\d+)\\b")
        val matcherNumbers = numbersPattern.matcher(code)
        while (matcherNumbers.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFB5CEA8)),
                start = matcherNumbers.start(),
                end = matcherNumbers.end()
            )
        }
    }
}
