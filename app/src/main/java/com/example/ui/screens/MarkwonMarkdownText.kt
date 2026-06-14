package com.example.ui.screens

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.prism4j.Prism4j
import io.noties.prism4j.GrammarLocator
import io.noties.prism4j.GrammarImpl
import io.noties.prism4j.TokenImpl
import io.noties.prism4j.PatternImpl
import java.util.regex.Pattern as javaPattern

@Composable
fun MarkwonMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    isDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    val markwon = remember(textColor, isDarkTheme) {
        // Universal parser patterns for common coding languages
        val commentsPattern = PatternImpl(javaPattern.compile("//.*|/\\*[\\s\\S]*?\\*/|#.*"), false, false, null, null)
        val stringsPattern = PatternImpl(javaPattern.compile("\"\"\"[\\s\\S]*?\"\"\"|\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*'"), false, false, null, null)
        val keywordsPattern = PatternImpl(javaPattern.compile("\\b(?:package|import|class|interface|object|fun|def|function|let|const|var|val|if|else|elif|while|for|in|return|null|true|false|as|is|when|private|protected|public|internal|override|companion|constructor|init|try|catch|finally|export|from|await|async|yield|typeof|instanceof)\\b"), false, false, null, null)
        val numbersPattern = PatternImpl(javaPattern.compile("\\b\\d+(?:\\.\\d+)?\\b"), false, false, null, null)
        val classesPattern = PatternImpl(javaPattern.compile("\\b[A-Z]\\w*\\b|@[A-Za-z_]\\w*\\b"), false, false, null, null)
        val functionsPattern = PatternImpl(javaPattern.compile("[A-Za-z_]\\w*(?=\\s*\\()"), false, false, null, null)

        val tokens = listOf(
            TokenImpl("comment", listOf(commentsPattern)),
            TokenImpl("string", listOf(stringsPattern)),
            TokenImpl("keyword", listOf(keywordsPattern)),
            TokenImpl("number", listOf(numbersPattern)),
            TokenImpl("class-name", listOf(classesPattern)),
            TokenImpl("function", listOf(functionsPattern))
        )
        val universalGrammar = GrammarImpl("universal", tokens)

        val activeLanguages = setOf(
            "kotlin", "java", "json", "js", "ts", "python", "html", "css", "xml", 
            "bash", "shell", "clike", "javascript", "typescript", "c", "cpp", "go", "rust",
            "jsx", "tsx"
        )

        val customLocator = object : GrammarLocator {
            override fun grammar(prism4j: Prism4j, name: String): Prism4j.Grammar? {
                val normalized = name.lowercase().trim()
                return if (activeLanguages.contains(normalized)) {
                    universalGrammar
                } else {
                    null
                }
            }
            override fun languages(): Set<String> = activeLanguages
        }
        val prism4j = Prism4j(customLocator)
        val highlightTheme = Prism4jThemeDefault.create()

        Markwon.builder(context)
            .usePlugin(CorePlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, highlightTheme))
            .usePlugin(TablePlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    val density = context.resources.displayMetrics.density
                    builder.blockMargin((12 * density).toInt())
                    
                    // Code block sizing
                    builder.codeBlockTextSize((13 * density).toInt())
                    builder.codeTextSize((13 * density).toInt())

                    // Dynamic colors based on light vs dark theme
                    val codeBgColor = if (isDarkTheme) {
                        android.graphics.Color.parseColor("#121115")
                    } else {
                        android.graphics.Color.parseColor("#F4F4F6")
                    }
                    val codeTxColor = if (isDarkTheme) {
                        android.graphics.Color.parseColor("#FF9800") // Warm amber
                    } else {
                        android.graphics.Color.parseColor("#E65100") // High-contrast orange
                    }

                    builder.codeBackgroundColor(codeBgColor)
                    builder.codeTextColor(codeTxColor)

                    // Fallback block code styling if encountered inline inside text block
                    val codeBlockBg = if (isDarkTheme) {
                        android.graphics.Color.parseColor("#15151A")
                    } else {
                        android.graphics.Color.parseColor("#E9E9ED")
                    }
                    val codeBlockTx = if (isDarkTheme) {
                        android.graphics.Color.parseColor("#D4D4D4")
                    } else {
                        android.graphics.Color.parseColor("#333333")
                    }

                    builder.codeBlockBackgroundColor(codeBlockBg)
                    builder.codeBlockTextColor(codeBlockTx)

                    // Dynamic clickable links
                    val linkColorInt = if (isDarkTheme) {
                        android.graphics.Color.parseColor("#64B5F6") // Bright blue for readability on dark backgrounds
                    } else {
                        android.graphics.Color.parseColor("#1565C0") // Deep blue for high-contrast on light backgrounds
                    }
                    builder.linkColor(linkColorInt)

                    // Blockquotes & headings break line styling
                    val blockQuoteBarColor = if (isDarkTheme) {
                        android.graphics.Color.parseColor("#424242")
                    } else {
                        android.graphics.Color.parseColor("#CCCCCC")
                    }
                    builder.blockQuoteColor(blockQuoteBarColor)
                    builder.blockQuoteWidth((4 * density).toInt())

                    if (textColor != Color.Unspecified) {
                        builder.headingBreakColor(textColor.copy(alpha = 0.2f).toArgb())
                    }
                }
            })
            .build()
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                if (textColor != Color.Unspecified) {
                    setTextColor(textColor.toArgb())
                }
                textSize = 15f
                setLineSpacing(0f, 1.3f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            if (textColor != Color.Unspecified) {
                textView.setTextColor(textColor.toArgb())
            }
            textView.movementMethod = LinkMovementMethod.getInstance()
            markwon.setMarkdown(textView, markdown)
        }
    )
}
