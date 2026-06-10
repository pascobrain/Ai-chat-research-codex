package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

fun saveCodeToFile(context: Context, code: String, language: String): Uri? {
    val extension = when (language.lowercase().trim()) {
        "kotlin", "kt" -> "kt"
        "java" -> "java"
        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"
        "html" -> "html"
        "css" -> "css"
        "json" -> "json"
        "python", "py" -> "py"
        "xml" -> "xml"
        "yaml", "yaml" -> "yaml"
        "markdown", "md" -> "md"
        "c++", "cpp" -> "cpp"
        "c" -> "c"
        "c#" -> "cs"
        "go" -> "go"
        "rust" -> "rs"
        "ruby" -> "rb"
        "swift" -> "swift"
        "sql" -> "sql"
        "shell", "sh", "bash" -> "sh"
        else -> "txt"
    }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val filename = "code_$timestamp.$extension"

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri).use { outputStream ->
                    outputStream?.write(code.toByteArray())
                }
                uri
            } else {
                null
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, filename)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(code.toByteArray())
            }
            Uri.fromFile(file)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@Composable
fun CodeBlockView(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val panelBackground = Color(0xFF1E1E1E)
    val headerBackground = Color(0xFF2D2D2D)
    val textColor = Color(0xFFD4D4D4)

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
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Copy Button
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

                    // Download Button
                    IconButton(
                        onClick = {
                            val uri = saveCodeToFile(context, code, language)
                            if (uri != null) {
                                Toast.makeText(context, "Saved code to Downloads!", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Download code file",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                    }
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

// Client-side syntax highlighting resembling Prism/Shiki premium Dark Themes (VS Code Dark style)
fun highlightCode(code: String, language: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(code)

        // 1. Numbers
        val numbersPattern = Pattern.compile("\\b(\\d+)\\b")
        val matcherNumbers = numbersPattern.matcher(code)
        while (matcherNumbers.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFB5CEA8)),
                start = matcherNumbers.start(),
                end = matcherNumbers.end()
            )
        }

        // 2. Class/Interface/Type Names (Capitalized, e.g., Composable, String, Int, List)
        val classPattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\b")
        val matcherClass = classPattern.matcher(code)
        while (matcherClass.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFF4EC9B0)),
                start = matcherClass.start(),
                end = matcherClass.end()
            )
        }

        // 3. Language Keywords
        val keywordsPattern = Pattern.compile(
            "\\b(val|var|fun|class|interface|object|return|import|package|private|public|protected|override|suspend|if|else|when|for|while|in|data|null|true|false|this|async|await|const|let|function|def|from|as|try|catch|finally|except|throw|throws|new|struct|enum|fn|pub|use|impl|type|static|implicit|explicit|virtual|abstract|volatile|transient|synchronized|void|int|double|float|long|short|char|byte|boolean)\\b"
        )
        val matcherKeywords = keywordsPattern.matcher(code)
        while (matcherKeywords.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold),
                start = matcherKeywords.start(),
                end = matcherKeywords.end()
            )
        }

        // 4. Function Calls
        val functionPattern = Pattern.compile("\\b(\\w+)(?=\\s*\\()")
        val matcherFunction = functionPattern.matcher(code)
        while (matcherFunction.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFDCDCAA)),
                start = matcherFunction.start(),
                end = matcherFunction.end()
            )
        }

        // 5. Annotations/Decorators (e.g., @Composable, @Test, @Serializable)
        val annotationPattern = Pattern.compile("@\\w+")
        val matcherAnnotation = annotationPattern.matcher(code)
        while (matcherAnnotation.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFC586C0)),
                start = matcherAnnotation.start(),
                end = matcherAnnotation.end()
            )
        }

        // 6. String Literals
        val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'|\"\"\"[^\"]*\"\"\"")
        val matcherStrings = stringPattern.matcher(code)
        while (matcherStrings.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFFCE9178)),
                start = matcherStrings.start(),
                end = matcherStrings.end()
            )
        }

        // 7. Comments
        val commentPattern = Pattern.compile("//.*|/\\*[^*]*\\*/|#.*")
        val matcherComments = commentPattern.matcher(code)
        while (matcherComments.find()) {
            addStyle(
                style = SpanStyle(color = Color(0xFF6A9955)),
                start = matcherComments.start(),
                end = matcherComments.end()
            )
        }
    }
}
