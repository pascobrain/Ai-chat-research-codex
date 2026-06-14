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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.*
import com.example.util.E2BCodeExecutor
import com.example.util.ExecutionResult
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.ui.viewinterop.AndroidView
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
    // A robust pattern matching block code markdown
    val pattern = Pattern.compile("```(\\w*)\\s*\\n(.*?)(?:\\n?\\s*```|$)", Pattern.DOTALL)
    val matcher = pattern.matcher(content)
    var lastIndex = 0

    while (matcher.find()) {
        val textBefore = content.substring(lastIndex, matcher.start())
        if (textBefore.isNotEmpty()) {
            blocks.add(MessageBlock.Text(textBefore))
        }
        val language = matcher.group(1)?.trim() ?: ""
        var code = matcher.group(2) ?: ""
        // Strip trailing backticks if any fallback occurred
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
        "jsx" -> "jsx"
        "tsx" -> "tsx"
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

fun generatePreviewHtml(code: String, language: String): String {
    val cleanLang = language.lowercase().trim()
    if (cleanLang == "mermaid") {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background-color: #0F172A;
                        color: #E2E8F0;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        overflow: hidden;
                    }
                    #container {
                        width: 100%;
                        height: 100%;
                        display: flex;
                        flex-direction: column;
                        justify-content: center;
                        align-items: center;
                        box-sizing: border-box;
                        padding: 24px;
                        overflow: auto;
                    }
                    .mermaid {
                        width: 100%;
                        max-width: 100%;
                        opacity: 0;
                        transition: opacity 0.4s ease-in-out;
                    }
                    .mermaid[data-processed="true"] {
                        opacity: 1;
                    }
                    #error-div {
                        color: #F87171 !important;
                        background-color: #1E293B !important;
                        border: 1px solid #EF4444 !important;
                        border-radius: 8px !important;
                        padding: 16px !important;
                        font-family: monospace !important;
                        font-size: 11px !important;
                        max-width: 90%;
                        box-shadow: 0 4px 12px rgba(239, 68, 68, 0.2);
                    }
                </style>
                <script>
                    window.onerror = function(message) {
                        showError(message);
                        return true;
                    };
                    function showError(msg) {
                        var container = document.getElementById("container");
                        container.innerHTML = `
                            <div id="error-div">
                                <b style="color: #EF4444;">Mermaid Syntax Error:</b><br/>
                                <pre style="margin: 8px 0 0 0; padding: 8px; background: #0F172A; border-radius: 4px; overflow-x: auto; font-size: 10px; border: 1px solid #334155; color: #CBD5E1;">${'$'}{msg}</pre>
                            </div>
                        `;
                    }
                    mermaid.initialize({
                        startOnLoad: true,
                        theme: 'dark',
                        securityLevel: 'loose',
                        themeVariables: {
                            background: '#0F172A',
                            primaryColor: '#1E293B',
                            primaryTextColor: '#F1F5F9',
                            lineColor: '#64748B',
                            secondaryColor: '#1E293B',
                            tertiaryColor: '#1E293B'
                        }
                    });
                </script>
            </head>
            <body>
                <div id="container">
                    <div class="mermaid">
                        $code
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    val isHtml = language.lowercase().trim() == "html"
    if (isHtml) {
        val hasHtmlTags = code.contains("<html", ignoreCase = true) || code.contains("<body", ignoreCase = true)
        if (hasHtmlTags) {
            val tailwindInject = "<head><script src=\"https://cdn.tailwindcss.com\"></script>"
            return code.replace("<head>", tailwindInject, ignoreCase = true)
                .replace("<HEAD>", tailwindInject, ignoreCase = true)
        } else {
            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1">
                    <script src="https://cdn.tailwindcss.com"></script>
                    <style>
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                            margin: 0;
                            padding: 16px;
                            background-color: #0f172a;
                            color: #f1f5f9;
                        }
                    </style>
                </head>
                <body>
                    $code
                </body>
                </html>
            """.trimIndent()
        }
    }

    // React JSX/TSX:
    val cleanedCode = code
        .replace(Regex("(?m)^\\s*import\\s+[^;\\n]+;?"), "")
        .replace(Regex("(?m)^\\s*export\\s+default\\s+(class|function|const|let|var)"), "$1")
        .replace(Regex("(?m)^\\s*export\\s+(class|function|const|let|var)"), "$1")
        .replace(Regex("(?m)^\\s*export\\s+default\\s+\\w+;?"), "")
        .replace(Regex(":\\s*React\\.FC(?:<[^>]*>)?"), "")
        .replace(Regex(":\\s*JSX\\.Element"), "")

    val componentNameRegex = Regex("(?:function|const|let|var|class)\\s+([A-Z]\\w*)")
    val foundNames = componentNameRegex.findAll(cleanedCode).map { it.groupValues[1] }.distinct().toList()
    val bindingCode = foundNames.joinToString("\n") { "try { window.$it = $it; } catch (e) {}" }
    val foundNamesJson = foundNames.joinToString(", ") { "\"$it\"" }

    val bootstrapJS = """
        try {
            let componentToRender = null;
            if (typeof App !== 'undefined') {
                componentToRender = App;
            } else if (typeof Main !== 'undefined') {
                componentToRender = Main;
            } else {
                const potentialNames = [$foundNamesJson];
                for (const name of potentialNames) {
                    if (typeof window[name] === 'function') {
                        componentToRender = window[name];
                        break;
                    }
                }
            }

            const container = document.getElementById('root');
            const root = ReactDOM.createRoot(container);
            
            if (componentToRender) {
                root.render(React.createElement(componentToRender));
            } else {
                try {
                    const trimmed = `${'$'}{window._user_raw_code}`.trim();
                    if (trimmed.startsWith('<') && trimmed.endsWith('>')) {
                        const compiledFragment = eval(Babel.transform(trimmed, { presets: ['react'] }).code);
                        root.render(compiledFragment);
                    } else {
                        root.render(React.createElement('div', { className: "p-4 bg-red-900/40 text-red-200 rounded border border-red-500 font-mono text-xs" }, 
                            React.createElement('strong', null, 'System Indicator: '), 
                            'No isolated React components detected. Write default components export or render inline JSX elements.'
                        ));
                    }
                } catch(err) {
                    root.render(React.createElement('div', { className: "p-4 bg-red-900/40 text-red-100 rounded border border-red-600 font-mono text-xs whitespace-pre-wrap" }, 
                        React.createElement('strong', null, 'Failed to Transpile Raw JSX Value: '), 
                        err.message
                    ));
                }
            }
        } catch (error) {
            document.getElementById('root').innerHTML = `
                <div class="p-4 bg-red-950 text-red-200 rounded border border-red-600 font-mono text-xs">
                    <h3 class="font-bold">Render Initialization Error:</h3>
                    <pre class="mt-2 text-xs overflow-auto whitespace-pre-wrap">${'$'}{error.message}</pre>
                </div>
            `;
        }
    """.trimIndent()

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>JSX React Sandbox Preview</title>
            <script src="https://unpkg.com/react@18/umd/react.production.min.js" crossorigin></script>
            <script src="https://unpkg.com/react-dom@18/umd/react-dom.production.min.js" crossorigin></script>
            <script src="https://unpkg.com/@babel/standalone/babel.min.js"></script>
            <script src="https://cdn.tailwindcss.com"></script>
            <style>
                body {
                    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    margin: 0;
                    padding: 16px;
                    background-color: #0F172A;
                    color: #F1F5F9;
                }
                * {
                    box-sizing: border-box;
                }
            </style>
        </head>
        <body>
            <div id="root"></div>
            
            <script>
                // Fallback for visual transpile checks
                window._user_raw_code = `${'$'}{code.replace("`", "\\`").replace("${'$'}", "\\${'$'}")}`;
                
                // Catch any global errors (compiled or runtime) and display on page
                window.addEventListener('error', function(event) {
                    const container = document.getElementById('root');
                    if (container) {
                        container.innerHTML = `
                            <div class="p-4 bg-red-950 text-red-200 rounded border border-red-600 font-mono text-xs">
                                <h3 class="font-bold text-sm text-red-100 flex items-center">
                                    Compilation & Rendering Error
                                </h3>
                                <pre class="mt-2 text-xs overflow-auto whitespace-pre-wrap">${'$'}{event.message}</pre>
                                <div class="mt-3 text-[10px] text-red-300/80">
                                    File: ${'$'}{event.filename ? event.filename.split('/').pop() : 'inline'} | Line: ${'$'}{event.lineno || 'N/A'}
                                </div>
                            </div>
                        `;
                    }
                    event.preventDefault();
                });
            </script>
            
            <script type="text/babel" data-presets="react,typescript">
                $cleanedCode
                
                $bindingCode
                
                $bootstrapJS
            </script>
        </body>
        </html>
    """.trimIndent()
}

data class ExecutionHistoryItem(
    val runIndex: Int,
    val timestamp: String,
    val arguments: String,
    val envVars: String,
    val stdin: String,
    val result: ExecutionResult,
    val durationMs: Long
)

data class TerminalPalette(
    val backgroundColor: Color,
    val textColor: Color,
    val labelColor: Color,
    val errorColor: Color,
    val statusColor: Color,
    val highlightColor: Color
)

val terminalThemesColors = mapOf(
    "midnight" to TerminalPalette(
        backgroundColor = Color(0xFF111827),
        textColor = Color(0xFFE5E7EB),
        labelColor = Color(0xFF9CA3AF),
        errorColor = Color(0xFFEF4444),
        statusColor = Color(0xFF10B981),
        highlightColor = Color(0xFF1E3A8A)
    ),
    "amber" to TerminalPalette(
        backgroundColor = Color(0xFF181005),
        textColor = Color(0xFFFFB300),
        labelColor = Color(0xFFD48A00),
        errorColor = Color(0xFFFF5252),
        statusColor = Color(0xFFFFC107),
        highlightColor = Color(0xFF5D3E00)
    ),
    "matrix" to TerminalPalette(
        backgroundColor = Color(0xFF020904),
        textColor = Color(0xFF00FF41),
        labelColor = Color(0xFF008F11),
        errorColor = Color(0xFFFF3333),
        statusColor = Color(0xFF00FF41),
        highlightColor = Color(0xFF003F0A)
    ),
    "cyberpunk" to TerminalPalette(
        backgroundColor = Color(0xFF1A0B2E),
        textColor = Color(0xFF00F0FF),
        labelColor = Color(0xFFFF007F),
        errorColor = Color(0xFFFF0055),
        statusColor = Color(0xFF39FF14),
        highlightColor = Color(0xFF5A189A)
    )
)

fun highlightTerminalText(
    text: String,
    query: String,
    palette: TerminalPalette
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(text)
        
        // 1. Highlight standard alert tokens
        val alertPattern = Pattern.compile("(?i)\\b(error|exception|fail|failed|failure|traceback|warning|warn|success|successful|ok)\\b")
        val matcherAlerts = alertPattern.matcher(text)
        while (matcherAlerts.find()) {
            val start = matcherAlerts.start()
            val end = matcherAlerts.end()
            val matchedWord = matcherAlerts.group().lowercase()
            val highlightColor = when {
                matchedWord.contains("error") || matchedWord.contains("exception") || matchedWord.contains("fail") -> palette.errorColor
                matchedWord.contains("warning") || matchedWord.contains("warn") -> Color(0xFFFFA000)
                else -> palette.statusColor
            }
            addStyle(
                style = SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold),
                start = start,
                end = end
            )
        }

        // 2. Highlight user search matches
        if (query.isNotBlank()) {
            var index = text.indexOf(query, ignoreCase = true)
            while (index != -1) {
                val start = index
                val end = index + query.length
                addStyle(
                    style = SpanStyle(background = palette.highlightColor, fontWeight = FontWeight.Bold, color = Color.White),
                    start = start,
                    end = end
                )
                index = text.indexOf(query, start + 1, ignoreCase = true)
            }
        }
    }
}

fun saveTerminalLogsToFile(context: Context, stdout: String, stderr: String, runNumber: Int): Uri? {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val filename = "sandbox_run_${runNumber}_$timestamp.log"
    val content = "=== E2B SANDBOX RUN #$runNumber LOGS ($timestamp) ===\n\nSTDOUT:\n$stdout\n\nSTDERR:\n$stderr\n"
    
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
                    outputStream?.write(content.toByteArray())
                }
                uri
            } else {
                null
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(content.toByteArray())
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
    modifier: Modifier = Modifier,
    e2bApiKey: String = ""
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val panelBackground = Color(0xFF1E1E1E)
    val headerBackground = Color(0xFF2D2D2D)
    val textColor = Color(0xFFD4D4D4)

    val resolvedLanguage = remember(code, language) {
        detectLanguage(code, language)
    }

    val supportsExecution = when (resolvedLanguage.lowercase().trim()) {
        "python", "py", "javascript", "js", "typescript", "ts", "bash", "sh", "shell" -> true
        else -> false
    }

    val supportsPreview = when (resolvedLanguage.lowercase().trim()) {
        "jsx", "tsx", "html", "svg", "xml", "mermaid" -> true
        else -> false
    }

    // Advanced Runner inputs
    var isConfigExpanded by remember { mutableStateOf(false) }
    var cliArgs by remember { mutableStateOf("") }
    var envVarsState by remember { mutableStateOf("") }
    var stdinState by remember { mutableStateOf("") }

    // Runner executions state
    var isRunning by remember { mutableStateOf(false) }
    var executionResult by remember { mutableStateOf<ExecutionResult?>(null) }
    var activeTerminalTab by remember { mutableStateOf("terminal") }
    var executionDurationMs by remember { mutableStateOf<Long?>(null) }
    var runCounter by remember { mutableStateOf(0) }
    val executionHistory = remember { mutableStateListOf<ExecutionHistoryItem>() }
    var selectedHistoryItemIndex by remember { mutableStateOf(-1) }

    // Terminal configurations
    var terminalTheme by remember { mutableStateOf("midnight") }
    var isTerminalSearchVisible by remember { mutableStateOf(false) }
    var terminalSearchQuery by remember { mutableStateOf("") }
    var isLineWrappingEnabled by remember { mutableStateOf(true) }

    var showPreview by remember { mutableStateOf(supportsPreview) } // Default to preview if available and fully supported
    var isExpanded by remember { mutableStateOf(false) }
    var isCopied by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            kotlinx.coroutines.delay(2000)
            isCopied = false
        }
    }

    val containerHeight by animateDpAsState(
        targetValue = if (isExpanded) 520.dp else 280.dp,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 300f
        ),
        label = "code_block_expansion"
    )

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = resolvedLanguage.uppercase().ifBlank { "CODE" },
                        color = Color(0xFF9CA3AF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    if (supportsPreview) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF37373D))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            TextButton(
                                onClick = { showPreview = false },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (!showPreview) Color(0xFF1E1E1E) else Color.Transparent),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (!showPreview) Color.White else Color(0xFF9CA3AF)
                                )
                            ) {
                                Text("CODE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(
                                onClick = { showPreview = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier
                                    .height(20.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (showPreview) Color(0xFF1E1E1E) else Color.Transparent),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (showPreview) Color(0xFF4CAF50) else Color(0xFF9CA3AF)
                                )
                            ) {
                                Text("PREVIEW", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Config settings toggle
                    if (supportsExecution) {
                        IconButton(
                            onClick = { isConfigExpanded = !isConfigExpanded },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configure execution options",
                                tint = if (isConfigExpanded) MaterialTheme.colorScheme.primary else Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Execute Button
                    if (supportsExecution) {
                        if (isRunning) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            IconButton(
                                onClick = {
                                    if (e2bApiKey.isBlank()) {
                                        Toast.makeText(context, "E2B API Key is missing. Add it in settings.", Toast.LENGTH_LONG).show()
                                    } else {
                                        coroutineScope.launch {
                                            isRunning = true
                                            val startTime = System.currentTimeMillis()
                                            val res = E2BCodeExecutor.executeCode(
                                                language = resolvedLanguage,
                                                code = code,
                                                apiKey = e2bApiKey,
                                                arguments = cliArgs,
                                                envVars = envVarsState,
                                                stdin = stdinState
                                            )
                                            val endTime = System.currentTimeMillis()
                                            val diff = endTime - startTime
                                            executionDurationMs = diff
                                            executionResult = res
                                            runCounter++
                                            
                                            // Add to History list
                                            executionHistory.add(
                                                ExecutionHistoryItem(
                                                    runIndex = runCounter,
                                                    timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                                                    arguments = cliArgs,
                                                    envVars = envVarsState,
                                                    stdin = stdinState,
                                                    result = res,
                                                    durationMs = diff
                                                )
                                            )
                                            selectedHistoryItemIndex = executionHistory.size - 1
                                            isRunning = false
                                        }
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run code",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Copy Button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Copied Code", code)
                            clipboard.setPrimaryClip(clip)
                            isCopied = true
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (isCopied) Color(0xFF10B981) else Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Download Button
                    IconButton(
                        onClick = {
                            val uri = saveCodeToFile(context, code, resolvedLanguage)
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

                    // Zoom/Expand Button (Toggles height of code/preview block smoothly)
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                            contentDescription = if (isExpanded) "Minimize view" else "Maximize view",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Full-screen View Dialog Button
                    IconButton(
                        onClick = { isFullScreen = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Full-screen mode",
                            tint = Color(0xFF9CA3AF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Advanced Collapsible Configuration Drawer
            AnimatedVisibility(visible = isConfigExpanded) {
                Surface(
                    color = Color(0xFF181818),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "CONFIG-SETUP FÜR SANDBOX",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            TextButton(
                                onClick = {
                                    cliArgs = ""
                                    envVarsState = ""
                                    stdinState = ""
                                },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Text("ZURÜCKSETZEN", fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = cliArgs,
                                onValueChange = { cliArgs = it },
                                label = { Text("CMD-Args (z.B. -v --debug)", fontSize = 9.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF333333),
                                    focusedContainerColor = Color(0xFF111111),
                                    unfocusedContainerColor = Color(0xFF111111)
                                )
                            )
                            OutlinedTextField(
                                value = envVarsState,
                                onValueChange = { envVarsState = it },
                                label = { Text("Env-Vars (z.B. DEBUG=1)", fontSize = 9.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF333333),
                                    focusedContainerColor = Color(0xFF111111),
                                    unfocusedContainerColor = Color(0xFF111111)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = stdinState,
                            onValueChange = { stdinState = it },
                            label = { Text("Standard-Eingabe (STDIN) für Eingabeaufforderungen (z.B. input())", fontSize = 9.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            placeholder = { Text("Trage hier Daten zeilenweise ein, die vom ausgeführten Skript gelesen werden...", fontSize = 10.sp, color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color(0xFF333333),
                                focusedContainerColor = Color(0xFF111111),
                                unfocusedContainerColor = Color(0xFF111111)
                            )
                        )
                    }
                }
            }

            // Code / Preview Content Display Area
            if (showPreview && supportsPreview) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(containerHeight)
                        .background(Color(0xFF0F172A))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    textZoom = 100
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    
                                    // Enable multi-touch zoom for Mermaid/Previews so they can pan/pinch zoom easily!
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }
                            }
                        },
                        update = { webView ->
                            val htmlData = generatePreviewHtml(code, resolvedLanguage)
                            webView.loadDataWithBaseURL("https://localhost", htmlData, "text/html", "UTF-8", null)
                        }
                    )
                }
            } else {
                val verticalScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(containerHeight)
                        .verticalScroll(verticalScrollState)
                        .padding(vertical = 14.dp)
                ) {
                    // Line numbers column (fixed on the left, does not scroll horizontally)
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier
                            .padding(start = 14.dp, end = 10.dp)
                            .width(28.dp)
                    ) {
                        val linesCount = code.lines().size.coerceAtLeast(1)
                        for (i in 1..linesCount) {
                            Text(
                                text = i.toString(),
                                color = Color(0xFF6E7681), // GitHub VS Code style line number color
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // Vertical Divider line between line numbers and code
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0xFF333333))
                    )

                    // Code block scrollable horizontally on the right
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(scrollState)
                            .padding(start = 10.dp, end = 14.dp)
                    ) {
                        Text(
                            text = highlightCode(code, resolvedLanguage),
                            color = textColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Execution Output Drawer & Terminal Emulator
            if (isRunning || executionResult != null) {
                HorizontalDivider(color = Color(0xFF2D2D2D), thickness = 1.dp)
                
                // Active terminal palette theme configuration
                val palette = terminalThemesColors[terminalTheme] ?: terminalThemesColors["midnight"]!!

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.backgroundColor)
                        .padding(12.dp)
                ) {
                    // Simulated OS Terminal window titlebar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Mac-style window controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(9.dp).background(Color(0xFFFF5F56), shape = RoundedCornerShape(50))) // Close
                            Box(modifier = Modifier.size(9.dp).background(Color(0xFFFFBD2E), shape = RoundedCornerShape(50))) // Minimize
                            Box(modifier = Modifier.size(9.dp).background(Color(0xFF27C93F), shape = RoundedCornerShape(50))) // Maximize
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "e2b-sandbox-terminal@ubuntu",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.labelColor.copy(alpha = 0.7f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        
                        // Theme toggles inside window
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("midnight", "amber", "matrix", "cyberpunk").forEach { themeKey ->
                                val dotColor = when (themeKey) {
                                    "midnight" -> Color(0xFF64748B)
                                    "amber" -> Color(0xFFFFB300)
                                    "matrix" -> Color(0xFF00FF41)
                                    else -> Color(0xFFFF007F)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(dotColor)
                                        .border(
                                            width = if (terminalTheme == themeKey) 1.dp else 0.dp,
                                            color = Color.White,
                                            shape = RoundedCornerShape(50)
                                        )
                                        .clickable { terminalTheme = themeKey }
                                )
                            }
                        }
                    }

                    // Status Strip Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val statusBgColor = when {
                                isRunning -> Color(0xFFFFB300).copy(alpha = 0.15f)
                                executionResult?.isSuccess == true -> palette.statusColor.copy(alpha = 0.15f)
                                else -> palette.errorColor.copy(alpha = 0.15f)
                            }
                            val statusBorderColor = when {
                                isRunning -> Color(0xFFFFB300).copy(alpha = 0.40f)
                                executionResult?.isSuccess == true -> palette.statusColor.copy(alpha = 0.40f)
                                else -> palette.errorColor.copy(alpha = 0.40f)
                            }
                            val statusTextColor = when {
                                isRunning -> Color(0xFFFFB300)
                                executionResult?.isSuccess == true -> palette.statusColor
                                else -> palette.errorColor
                            }
                            val statusTextValue = when {
                                isRunning -> "SANDBOX RUNNING"
                                executionResult?.isSuccess == true -> "OUTPUT (SUCCESS)"
                                else -> "OUTPUT (FAILED)"
                            }

                            // Primary status badge
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = statusBgColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, statusBorderColor),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(statusTextColor)
                                    )
                                    Text(
                                        text = statusTextValue,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = statusTextColor
                                    )
                                }
                            }

                            // Latency Badge
                            executionDurationMs?.let { ms ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = palette.textColor.copy(alpha = 0.04f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, palette.textColor.copy(alpha = 0.12f)),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = "⚡ ${ms}ms",
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.labelColor
                                        )
                                    }
                                }
                            }

                            // Exit Code Badge
                            executionResult?.exitCode?.let { codeNum ->
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (codeNum == 0) palette.statusColor.copy(alpha = 0.12f) else palette.errorColor.copy(alpha = 0.12f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (codeNum == 0) palette.statusColor.copy(alpha = 0.3f) else palette.errorColor.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.height(20.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    ) {
                                        Text(
                                            text = "exit: $codeNum",
                                            fontSize = 8.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = if (codeNum == 0) palette.statusColor else palette.errorColor
                                        )
                                    }
                                }
                            }
                        }

                        // Sandbox Info Pill
                        Text(
                            text = "MicroVM (Ubuntu 22.04 LTS)",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.labelColor.copy(alpha = 0.5f)
                        )
                    }

                    // Interactive Tab selection bar combined with Quick Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Tab switcher
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val tabs = listOf(
                                "terminal" to "🖥️ Logs",
                                "diagnostics" to "🔬 Diagnose",
                                "env" to "⚙️ Setup"
                            )
                            tabs.forEach { (tabKey, tabLabel) ->
                                val isSelected = activeTerminalTab == tabKey
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) palette.statusColor.copy(alpha = 0.15f) else Color.Transparent,
                                    border = androidx.compose.foundation.BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) palette.statusColor.copy(alpha = 0.4f) else palette.textColor.copy(alpha = 0.12f)
                                    ),
                                    modifier = Modifier
                                        .clickable { activeTerminalTab = tabKey }
                                        .height(24.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Text(
                                            text = tabLabel,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) palette.statusColor else palette.textColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }

                        // Right side: Quick Actions block
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Output Search Trigger
                            IconButton(
                                onClick = { isTerminalSearchVisible = !isTerminalSearchVisible },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Find in output",
                                    tint = if (isTerminalSearchVisible) palette.statusColor else palette.labelColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            // Word Wrap Toggle icon
                            IconButton(
                                onClick = { isLineWrappingEnabled = !isLineWrappingEnabled },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = if (isLineWrappingEnabled) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isLineWrappingEnabled) "Disable line wrapping" else "Enable line wrapping",
                                    tint = palette.labelColor,
                                    modifier = Modifier.size(13.dp)
                                )
                            }

                            // Copy stdout log
                            IconButton(
                                onClick = {
                                    val outText = buildString {
                                        executionResult?.stdout?.let { if (it.isNotBlank()) append(it).append("\n") }
                                        executionResult?.stderr?.let { if (it.isNotBlank()) append("STDERR:\n").append(it) }
                                    }
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Terminal logs", outText))
                                    Toast.makeText(context, "Terminal-Ausgabe kopiert!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy output text",
                                    tint = palette.labelColor,
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            // Download stdout log
                            IconButton(
                                onClick = {
                                    val out = executionResult?.stdout ?: ""
                                    val err = executionResult?.stderr ?: ""
                                    val uri = saveTerminalLogsToFile(context, out, err, runCounter)
                                    if (uri != null) {
                                        Toast.makeText(context, "Log-Datei heruntergeladen!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to export logs", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Download output log file",
                                    tint = palette.labelColor,
                                    modifier = Modifier.size(11.dp)
                                )
                            }

                            // Clear terminal view
                            IconButton(
                                onClick = {
                                    executionResult = null
                                    executionDurationMs = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear logs and reset view",
                                    tint = palette.labelColor,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }

                    // Dynamic history drop-down row
                    if (executionHistory.size > 1) {
                        var isHistoryExpanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.padding(top = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(palette.textColor.copy(alpha = 0.05f))
                                    .clickable { isHistoryExpanded = true }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = palette.labelColor,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LOG VERLAUF: Run #${executionHistory.getOrNull(selectedHistoryItemIndex)?.runIndex ?: "N/A"}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.labelColor
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Change historical item",
                                    tint = palette.labelColor,
                                    modifier = Modifier.size(10.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = isHistoryExpanded,
                                onDismissRequest = { isHistoryExpanded = false },
                                modifier = Modifier.background(palette.backgroundColor).border(1.dp, palette.textColor.copy(alpha = 0.15f))
                            ) {
                                executionHistory.forEachIndexed { index, hist ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Run #${hist.runIndex} [${hist.timestamp}] (${hist.durationMs}ms) - Exit: ${hist.result.exitCode ?: "N/A"}",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (hist.result.isSuccess) palette.statusColor else palette.errorColor
                                            )
                                        },
                                        onClick = {
                                            selectedHistoryItemIndex = index
                                            executionResult = hist.result
                                            executionDurationMs = hist.durationMs
                                            cliArgs = hist.arguments
                                            envVarsState = hist.envVars
                                            stdinState = hist.stdin
                                            isHistoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Interactive Terminal search box
                    AnimatedVisibility(visible = isTerminalSearchVisible) {
                        OutlinedTextField(
                            value = terminalSearchQuery,
                            onValueChange = { terminalSearchQuery = it },
                            placeholder = { Text("Suche in Terminalausgaben...", fontSize = 11.sp, color = palette.labelColor) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.textColor),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = palette.labelColor, modifier = Modifier.size(14.dp)) },
                            trailingIcon = {
                                if (terminalSearchQuery.isNotBlank()) {
                                    IconButton(onClick = { terminalSearchQuery = "" }, modifier = Modifier.size(14.dp)) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search query", tint = palette.labelColor, modifier = Modifier.size(12.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(38.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.statusColor,
                                unfocusedBorderColor = palette.textColor.copy(alpha = 0.15f),
                                focusedContainerColor = palette.backgroundColor.copy(alpha = 0.5f),
                                unfocusedContainerColor = palette.backgroundColor.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isRunning) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp,
                                color = palette.statusColor
                            )
                            Text(
                                text = "Isolierte MicroVM wird gestartet & Skript ausgeführt...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.statusColor,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "E2B Secure Sandbox • Volume Mounts aktiv • Firewall aktiv",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = palette.labelColor.copy(alpha = 0.6f)
                            )
                        }
                    } else if (executionResult != null) {
                        executionResult?.let { res ->
                            when (activeTerminalTab) {
                                "terminal" -> {
                                    val outputConsoleModifier = if (isLineWrappingEnabled) {
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                            .verticalScroll(rememberScrollState())
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 280.dp)
                                            .verticalScroll(rememberScrollState())
                                            .horizontalScroll(rememberScrollState())
                                    }

                                    Column(modifier = outputConsoleModifier) {
                                        if (res.error != null) {
                                            Text(
                                                text = res.error,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = palette.errorColor,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        } else {
                                            if (res.stdout.isNotBlank()) {
                                                Text(
                                                    text = highlightTerminalText(res.stdout, terminalSearchQuery, palette),
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = palette.textColor,
                                                    modifier = Modifier.padding(bottom = 6.dp),
                                                    lineHeight = 16.sp
                                                )
                                            }
                                            
                                            if (res.stderr.isNotBlank()) {
                                                Text(
                                                    text = "STDERR TRACEBACK:",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = palette.errorColor,
                                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                                )
                                                Text(
                                                    text = highlightTerminalText(res.stderr, terminalSearchQuery, palette),
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = palette.errorColor,
                                                    modifier = Modifier.padding(bottom = 4.dp),
                                                    lineHeight = 16.sp
                                                )
                                            }

                                            if (res.stdout.isBlank() && res.stderr.isBlank()) {
                                                Text(
                                                    text = "(The script successfully completed execution with no returned logs. Exit code: ${res.exitCode})",
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = palette.labelColor,
                                                    modifier = Modifier.padding(vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                "diagnostics" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Row showing Performance overview Cards
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            // Speed meter Card
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = palette.textColor.copy(alpha = 0.04f)
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, palette.textColor.copy(alpha = 0.08f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = "RUN-LATENZ",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = palette.labelColor
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    val ms = executionDurationMs ?: 0L
                                                    val (speedEmoji, speedLabel, desc) = when {
                                                        ms < 150 -> Triple("⚡", "Blitzschnell", "Optimale Latenz")
                                                        ms < 800 -> Triple("🏎️", "Schnell", "Regulärer Run")
                                                        else -> Triple("⏳", "Kalkulierend", "Längerer Workload")
                                                    }
                                                    Text(
                                                        text = "$speedEmoji $ms ms",
                                                        fontSize = 13.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = palette.statusColor
                                                    )
                                                    Text(
                                                        text = "$speedLabel • $desc",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = palette.labelColor.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }

                                            // Exit Code Card
                                            Card(
                                                colors = CardDefaults.cardColors(
                                                    containerColor = palette.textColor.copy(alpha = 0.04f)
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, palette.textColor.copy(alpha = 0.08f)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = "STEUERUNGSCODE",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = palette.labelColor
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    val isClean = res.exitCode == 0 && res.error == null
                                                    Text(
                                                        text = "Exit: ${res.exitCode ?: "N/A"}",
                                                        fontSize = 13.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isClean) palette.statusColor else palette.errorColor
                                                    )
                                                    Text(
                                                        text = if (isClean) "Clean Execution" else "Exceptions reported",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = if (isClean) palette.statusColor.copy(alpha = 0.8f) else palette.errorColor.copy(alpha = 0.8f)
                                                    )
                                                }
                                            }
                                        }

                                        // Run History Average Info
                                        if (executionHistory.isNotEmpty()) {
                                            val runCountValue = executionHistory.size
                                            val avgMs = executionHistory.map { it.durationMs }.average().toInt()
                                            Surface(
                                                color = palette.textColor.copy(alpha = 0.02f),
                                                shape = RoundedCornerShape(4.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, palette.textColor.copy(alpha = 0.05f)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Gesamt-Verlauf: $runCountValue Runs",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = palette.labelColor
                                                    )
                                                    Text(
                                                        text = "Ø Dauer: ${avgMs}ms",
                                                        fontSize = 8.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = palette.statusColor,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Auto-Diagnostic Suggestions Box
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (res.isSuccess && res.exitCode == 0) palette.statusColor.copy(alpha = 0.03f) else palette.errorColor.copy(alpha = 0.03f)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                width = 1.dp,
                                                color = if (res.isSuccess && res.exitCode == 0) palette.statusColor.copy(alpha = 0.15f) else palette.errorColor.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = "DIAGNOSE & TIPPS",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (res.isSuccess && res.exitCode == 0) palette.statusColor else palette.errorColor
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val diagnosticMessage = when {
                                                    res.error != null -> {
                                                        "Ein schwerer Verbindungsfehler oder API-Fehler blockierte die Sandbox: '${res.error}'. Falls das Problem weiterhin besteht, kontrolliere, ob dein E2B Key noch gültig und aktiv ist."
                                                    }
                                                    res.stderr.contains("ModuleNotFoundError", ignoreCase = true) || res.stderr.contains("ImportError", ignoreCase = true) -> {
                                                        "ModuleNotFoundError: Ein Import im Skript schlägt fehl. Nutze pip-install Syntax im Code oder bitte das KI-Modell, die Abhängigkeiten vorab zu konfigurieren."
                                                    }
                                                    res.stderr.contains("SyntaxError", ignoreCase = true) || res.stderr.contains("IndentationError", ignoreCase = true) -> {
                                                        "Syntax/Indentation: Dein Code hat Syntax- oder Strukturfehler. Einrückungen bei Python oder offene Klammern bei JavaScript blockieren den Lauf."
                                                    }
                                                    res.stderr.contains("KeyError", ignoreCase = true) || res.stderr.contains("IndexError", ignoreCase = true) -> {
                                                        "KeyError/IndexError: Daten-Indexfehler. Es wird versucht, auf ein Array-Element oder ein Dict-Key zuzugreifen, das so nicht existiert."
                                                    }
                                                    res.stderr.isNotBlank() -> {
                                                        "Es gab Ausnahmefehler während der Laufzeit (siehe STDERR). Analysiere die Fehlermeldung oberhalb, um fehlerhafte Codezeilen zu isolieren."
                                                    }
                                                    res.stdout.isBlank() -> {
                                                        "Das Programm beendete fehlerfrei (Exit: 0), gab aber keinen Output aus. Ergänze Print-Befehle (z. B. `print()` in Python oder `console.log()` in JS), um Daten im Terminal anzuzeigen."
                                                    }
                                                    else -> {
                                                        "Das Skript läuft fehlerfrei! Es wurden alle Log-Ausgaben erfolgreich verarbeitet und die temporäre MicroVM ordnungsgemäß heruntergefahren."
                                                    }
                                                }
                                                Text(
                                                    text = diagnosticMessage,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = palette.textColor.copy(alpha = 0.85f),
                                                    lineHeight = 14.sp
                                                )
                                            }
                                        }
                                    }
                                }
                                "env" -> {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "UMGEBUNGS-SETUP & VARS",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.labelColor
                                        )
                                        
                                        // CLI Args Badge Card
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = palette.textColor.copy(alpha = 0.03f)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.textColor.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                                Text(
                                                    text = "CMD-Arguments:",
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = palette.labelColor
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = cliArgs.ifBlank { "(Keine Argumente deklariert)" },
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (cliArgs.isNotBlank()) palette.textColor else palette.labelColor.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        // Env vars Badge Card
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = palette.textColor.copy(alpha = 0.03f)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.textColor.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                                Text(
                                                    text = "Environment Variables:",
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = palette.labelColor
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = envVarsState.ifBlank { "(Keine Umgebungsvariablen deklariert)" },
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (envVarsState.isNotBlank()) palette.textColor else palette.labelColor.copy(alpha = 0.6f)
                                                )
                                            }
                                        }

                                        // Custom Stdin flow
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = palette.textColor.copy(alpha = 0.03f)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, palette.textColor.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                                                Text(
                                                    text = "Standard Eingabe (STDIN):",
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = palette.labelColor
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = stdinState.ifBlank { "(Kein vorab eingespeister Text-Stream)" },
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (stdinState.isNotBlank()) palette.textColor else palette.labelColor.copy(alpha = 0.6f),
                                                    maxLines = 4
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

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = panelBackground
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header inside Fullscreen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerBackground)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            IconButton(
                                onClick = { isFullScreen = false },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close full-screen",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                text = "${resolvedLanguage.uppercase()} FULL-SCREEN WORKSPACE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Preview Toggle for supportsPreview (JSX/TSX, HTML, XML, SVG, Mermaid)
                            if (supportsPreview) {
                                Button(
                                    onClick = { showPreview = !showPreview },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (showPreview) MaterialTheme.colorScheme.primary else Color(0xFF37373D),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(if (showPreview) "CODE VIEW" else "PREVIEW VIEW", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Run code in full screen (Python/JS/TS)
                            if (supportsExecution) {
                                if (isRunning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (e2bApiKey.isBlank()) {
                                                Toast.makeText(context, "E2B API Key is missing. Add it in settings.", Toast.LENGTH_LONG).show()
                                            } else {
                                                coroutineScope.launch {
                                                    isRunning = true
                                                    val startTime = System.currentTimeMillis()
                                                    val res = E2BCodeExecutor.executeCode(
                                                        language = resolvedLanguage,
                                                        code = code,
                                                        apiKey = e2bApiKey,
                                                        arguments = cliArgs,
                                                        envVars = envVarsState,
                                                        stdin = stdinState
                                                    )
                                                    val endTime = System.currentTimeMillis()
                                                    val diff = endTime - startTime
                                                    executionDurationMs = diff
                                                    executionResult = res
                                                    runCounter++
                                                    executionHistory.add(
                                                        ExecutionHistoryItem(
                                                            runIndex = runCounter,
                                                            timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                                                            arguments = cliArgs,
                                                            envVars = envVarsState,
                                                            stdin = stdinState,
                                                            result = res,
                                                            durationMs = diff
                                                        )
                                                    )
                                                    selectedHistoryItemIndex = executionHistory.size - 1
                                                    isRunning = false
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Run code",
                                            tint = Color(0xFF4CAF50),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }

                            // Copy in full screen
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Code", code)
                                    clipboard.setPrimaryClip(clip)
                                    isCopied = true
                                    Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = if (isCopied) Color(0xFF10B981) else Color(0xFF9CA3AF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Save in full screen
                            IconButton(
                                onClick = {
                                    val uri = saveCodeToFile(context, code, resolvedLanguage)
                                    if (uri != null) {
                                        Toast.makeText(context, "Saved code to Downloads!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = "Download code file",
                                    tint = Color(0xFF9CA3AF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Content Area (Flexible layout)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        if (showPreview && supportsPreview) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewClient = WebViewClient()
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            textZoom = 100
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                        }
                                    }
                                },
                                update = { webView ->
                                    val htmlData = generatePreviewHtml(code, resolvedLanguage)
                                    webView.loadDataWithBaseURL("https://localhost", htmlData, "text/html", "UTF-8", null)
                                }
                            )
                        } else {
                            val fsVerticalScrollState = rememberScrollState()
                            val fsHorizontalScrollState = rememberScrollState()
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(fsVerticalScrollState)
                                    .padding(vertical = 16.dp)
                            ) {
                                // Fullscreen Line Numbers
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier
                                        .padding(start = 16.dp, end = 12.dp)
                                        .width(36.dp)
                                ) {
                                    val linesCount = code.lines().size.coerceAtLeast(1)
                                    for (i in 1..linesCount) {
                                        Text(
                                            text = i.toString(),
                                            color = Color(0xFF6E7681),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp,
                                            lineHeight = 20.sp
                                        )
                                    }
                                }

                                // Fullscreen Divider
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .fillMaxHeight()
                                        .background(Color(0xFF333333))
                                )

                                // Fullscreen Code Content
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .horizontalScroll(fsHorizontalScrollState)
                                        .padding(start = 12.dp, end = 16.dp)
                                ) {
                                    Text(
                                        text = highlightCode(code, resolvedLanguage),
                                        color = textColor,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }

                    // Terminal Output inside full screen
                    if (isRunning || executionResult != null) {
                        HorizontalDivider(color = Color(0xFF2D2D2D), thickness = 1.dp)
                        
                        val palette = terminalThemesColors[terminalTheme] ?: terminalThemesColors["midnight"]!!
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp) // generous terminal height in full-screen
                                .background(palette.backgroundColor)
                                .padding(12.dp)
                        ) {
                            // status stuff
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(9.dp).background(Color(0xFFFF5F56), shape = RoundedCornerShape(50)))
                                    Box(modifier = Modifier.size(9.dp).background(Color(0xFFFFBD2E), shape = RoundedCornerShape(50)))
                                    Box(modifier = Modifier.size(9.dp).background(Color(0xFF27C93F), shape = RoundedCornerShape(50)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "fullscreen-sandbox-terminal@ubuntu",
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.labelColor.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            // Scrolling Terminal Output Log
                            val terminalScrollState = rememberScrollState()
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(terminalScrollState)
                                    .background(Color.Black.copy(alpha = 0.2f))
                                    .padding(8.dp)
                            ) {
                                val outText = when {
                                    isRunning -> "Executing program in isolated E2B Docker container...\nPreparing standard file descriptors..."
                                    executionResult != null -> {
                                        val builder = StringBuilder()
                                        if (executionResult?.error?.isNotBlank() == true) {
                                            builder.append("API ERROR / CONNECTION EXCEPTION:\n")
                                                .append(executionResult?.error).append("\n")
                                        }
                                        if (executionResult?.stdout?.isNotBlank() == true) {
                                            builder.append(executionResult?.stdout)
                                        }
                                        if (executionResult?.stderr?.isNotBlank() == true) {
                                            if (builder.isNotEmpty()) builder.append("\n")
                                            builder.append("STDERR:\n").append(executionResult?.stderr)
                                        }
                                        if (builder.isEmpty()) {
                                            builder.append("(Execution finished with no output)")
                                        }
                                        builder.toString()
                                    }
                                    else -> "Idle"
                                }
                                Text(
                                    text = highlightTerminalText(outText, terminalSearchQuery, palette),
                                    color = palette.textColor,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun detectLanguage(code: String, declaredLanguage: String): String {
    val decl = declaredLanguage.trim().lowercase()
    if (decl.isNotBlank() && decl != "code" && decl != "text") {
        return declaredLanguage
    }

    val hasHtml = code.contains("<!DOCTYPE html>", ignoreCase = true) ||
            code.contains("<html", ignoreCase = true) ||
            (code.contains("<div", ignoreCase = true) && code.contains("</div>", ignoreCase = true)) ||
            (code.contains("<script", ignoreCase = true) && code.contains("</script>", ignoreCase = true))

    val hasMermaid = code.contains("graph TD", ignoreCase = true) ||
            code.contains("sequenceDiagram", ignoreCase = true) ||
            code.contains("flowchart ", ignoreCase = true) ||
            code.contains("gantt", ignoreCase = true) ||
            code.contains("classDiagram", ignoreCase = true)

    val hasJson = try {
        val trimmed = code.trim()
        ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) && trimmed.contains("\":")
    } catch (e: Exception) {
        false
    }

    val hasPython = code.contains("def ") ||
            code.contains("import os") ||
            code.contains("import sys") ||
            (code.contains("print(") && !code.contains("System.out.println") && !code.contains("console.log")) ||
            code.contains("elif ") ||
            code.contains("if __name__ == ")

    val hasKotlin = code.contains("fun ") ||
            code.contains("val ") ||
            code.contains("var ") ||
            code.contains("suspend fun") ||
            code.contains("import kotlinx.") ||
            code.contains("companion object")

    val hasJava = code.contains("public class ") ||
            code.contains("public static void main") ||
            code.contains("System.out.println") ||
            (code.contains("import java.") && !code.contains("fun "))

    val hasJsTs = code.contains("function ") ||
            code.contains("const ") ||
            code.contains("let ") ||
            code.contains("console.log") ||
            (code.contains("import ") && code.contains("from "))

    val hasTs = hasJsTs && (code.contains("interface ") || code.contains("type ") || code.contains("as ") || code.contains(": "))

    val hasSql = (code.contains("select ", ignoreCase = true) && code.contains("from ", ignoreCase = true)) ||
            code.contains("insert into ", ignoreCase = true) ||
            code.contains("create table ", ignoreCase = true) ||
            (code.contains("update ", ignoreCase = true) && code.contains("set ", ignoreCase = true))

    val hasRust = code.contains("fn ") && (code.contains("println!") || code.contains("let mut "))

    val hasCpp = code.contains("#include <") || code.contains("std::cout")

    val hasCss = code.contains("body {") || code.contains(".class {") || code.contains("#id {")

    return when {
        hasMermaid -> "mermaid"
        hasHtml -> "html"
        hasJson -> "json"
        hasKotlin -> "kotlin"
        hasJava -> "java"
        hasPython -> "python"
        hasTs -> "typescript"
        hasJsTs -> "javascript"
        hasSql -> "sql"
        hasRust -> "rust"
        hasCpp -> "cpp"
        hasCss -> "css"
        else -> "code"
    }
}

// Client-side syntax highlighting resembling Prism/Shiki premium Dark Themes (VS Code Dark style)
fun highlightCode(code: String, language: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        append(code)

        val protectedSpans = mutableListOf<IntRange>()

        // 1. Comments (Double-slash, blocks, and hash comments)
        val commentPattern = Pattern.compile("//.*|/\\*[^*]*\\*(?:[^/]*\\*)*/|#.*")
        val matcherComments = commentPattern.matcher(code)
        while (matcherComments.find()) {
            val start = matcherComments.start()
            val end = matcherComments.end()
            addStyle(
                style = SpanStyle(color = Color(0xFF6A9955)), // Green
                start = start,
                end = end
            )
            protectedSpans.add(start..end)
        }

        // 2. String Literals
        val stringPattern = Pattern.compile("\"\"\"[^\"]*\"\"\"|\"[^\"]*\"|'[^']*'")
        val matcherStrings = stringPattern.matcher(code)
        while (matcherStrings.find()) {
            val start = matcherStrings.start()
            val end = matcherStrings.end()
            val insideComment = protectedSpans.any { start >= it.first && end <= it.last }
            if (!insideComment) {
                addStyle(
                    style = SpanStyle(color = Color(0xFFCE9178)), // Peach/Orange
                    start = start,
                    end = end
                )
                protectedSpans.add(start..end)
            }
        }

        // Helper to check if style overlaps with are already protected comments/strings
        fun isProtected(start: Int, end: Int): Boolean {
            return protectedSpans.any { range ->
                (start >= range.first && start < range.last) ||
                (end > range.first && end <= range.last) ||
                (start <= range.first && end >= range.last)
            }
        }

        // 3. Numbers
        val numbersPattern = Pattern.compile("\\b(\\d+)\\b")
        val matcherNumbers = numbersPattern.matcher(code)
        while (matcherNumbers.find()) {
            val start = matcherNumbers.start()
            val end = matcherNumbers.end()
            if (!isProtected(start, end)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFFB5CEA8)), // Soft pale green
                    start = start,
                    end = end
                )
            }
        }

        // 4. Class/Interface/Type Names (Capitalized)
        val classPattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\b")
        val matcherClass = classPattern.matcher(code)
        while (matcherClass.find()) {
            val start = matcherClass.start()
            val end = matcherClass.end()
            if (!isProtected(start, end)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFF4EC9B0)), // Blue-green types
                    start = start,
                    end = end
                )
            }
        }

        // 5. Language Keywords
        val keywordsPattern = Pattern.compile(
            "\\b(val|var|fun|class|interface|object|return|import|package|private|public|protected|override|suspend|if|else|when|for|while|in|data|null|true|false|this|async|await|const|let|function|def|from|as|try|catch|finally|except|throw|throws|new|struct|enum|fn|pub|use|impl|type|static|implicit|explicit|virtual|abstract|volatile|transient|synchronized|void|int|double|float|long|short|char|byte|boolean)\\b"
        )
        val matcherKeywords = keywordsPattern.matcher(code)
        while (matcherKeywords.find()) {
            val start = matcherKeywords.start()
            val end = matcherKeywords.end()
            if (!isProtected(start, end)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFF569CD6), fontWeight = FontWeight.Bold), // Royal blue keywords
                    start = start,
                    end = end
                )
            }
        }

        // 6. Function Calls
        val matcherFunction = Pattern.compile("\\b(\\w+)(?=\\s*\\()").matcher(code)
        while (matcherFunction.find()) {
            val start = matcherFunction.start()
            val end = matcherFunction.end()
            if (!isProtected(start, end)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFFDCDCAA)), // Yellow functions
                    start = start,
                    end = end
                )
            }
        }

        // 7. Annotations/Decorators
        val annotationPattern = Pattern.compile("@\\w+")
        val matcherAnnotation = annotationPattern.matcher(code)
        while (matcherAnnotation.find()) {
            val start = matcherAnnotation.start()
            val end = matcherAnnotation.end()
            if (!isProtected(start, end)) {
                addStyle(
                    style = SpanStyle(color = Color(0xFFC586C0)), // Lavender magenta
                    start = start,
                    end = end
                )
            }
        }

        // 8. Markup support (JSX / HTML / XML tags & attributes)
        val isMarkup = when (language.lowercase().trim()) {
            "jsx", "tsx", "html", "xml", "svg" -> true
            else -> false
        }
        if (isMarkup) {
            // HTML/JSX tag names
            val tagPattern = Pattern.compile("(?<=<|</)[a-zA-Z_][\\w.-]*")
            val matcherTag = tagPattern.matcher(code)
            while (matcherTag.find()) {
                val start = matcherTag.start()
                val end = matcherTag.end()
                if (!isProtected(start, end)) {
                    addStyle(
                        style = SpanStyle(color = Color(0xFF569CD6)), // Blue and turquoise tag names
                        start = start,
                        end = end
                    )
                }
            }

            // HTML/JSX brackets
            val bracketsPattern = Pattern.compile("</?|/?>")
            val matcherBrackets = bracketsPattern.matcher(code)
            while (matcherBrackets.find()) {
                val start = matcherBrackets.start()
                val end = matcherBrackets.end()
                if (!isProtected(start, end)) {
                    addStyle(
                        style = SpanStyle(color = Color(0xFF808080)), // Grey code brackets
                        start = start,
                        end = end
                    )
                }
            }

            // HTML/JSX attributes
            val attrPattern = Pattern.compile("\\b[a-zA-Z_][\\w.-]*(?=\\s*=)")
            val matcherAttr = attrPattern.matcher(code)
            while (matcherAttr.find()) {
                val start = matcherAttr.start()
                val end = matcherAttr.end()
                if (!isProtected(start, end)) {
                    addStyle(
                        style = SpanStyle(color = Color(0xFF9CDCFE)), // Light-blue properties
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
}
