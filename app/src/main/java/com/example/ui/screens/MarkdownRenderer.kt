package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Robust markdown line structures.
 */
sealed interface MarkdownLine {
    data class Header(val level: Int, val text: String) : MarkdownLine
    data class BlockQuote(val text: String) : MarkdownLine
    data class BulletItem(val text: String) : MarkdownLine
    data class NumberedItem(val number: String, val text: String) : MarkdownLine
    object Separator : MarkdownLine
    data class Paragraph(val text: String) : MarkdownLine
}

/**
 * High-performance, gorgeous Markdown Text Renderer.
 * Supports: Bold, Italic, Bullet Lists, Numbered Lists, Headers, Quote Blocks, Separators, and Inline Code.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    baseFontSize: TextUnit = 14.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val lines = parseMarkdownLines(text)
    val parsedFontSizeValue = baseFontSize.value

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { line ->
            when (line) {
                is MarkdownLine.Header -> {
                    val headerSize = when (line.level) {
                        1 -> (parsedFontSizeValue + 6).sp
                        2 -> (parsedFontSizeValue + 4).sp
                        else -> (parsedFontSizeValue + 2).sp
                    }
                    Text(
                        text = parseInlineMarkdown(line.text, isDark),
                        fontSize = headerSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        lineHeight = (headerSize.value * 1.3).sp
                    )
                }
                is MarkdownLine.BlockQuote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                    ) {
                        // Quote accent indicator line
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(IntrinsicSize.Max)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                .padding(end = 8.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(line.text, isDark),
                            fontSize = baseFontSize,
                            fontStyle = FontStyle.Italic,
                            color = textColor.copy(alpha = 0.8f),
                            lineHeight = (parsedFontSizeValue * 1.4).sp
                        )
                    }
                }
                is MarkdownLine.BulletItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = baseFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(line.text, isDark),
                            fontSize = baseFontSize,
                            color = textColor,
                            lineHeight = (parsedFontSizeValue * 1.4).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownLine.NumberedItem -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${line.number}.",
                            fontSize = baseFontSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(line.text, isDark),
                            fontSize = baseFontSize,
                            color = textColor,
                            lineHeight = (parsedFontSizeValue * 1.4).sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                is MarkdownLine.Separator -> {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                is MarkdownLine.Paragraph -> {
                    if (line.text.isNotBlank()) {
                        Text(
                            text = parseInlineMarkdown(line.text, isDark),
                            fontSize = baseFontSize,
                            color = textColor,
                            lineHeight = (parsedFontSizeValue * 1.45).sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

/**
 * Parses raw text block line-by-line to identify block styles.
 */
fun parseMarkdownLines(text: String): List<MarkdownLine> {
    val rawLines = text.split("\n")
    val parsed = mutableListOf<MarkdownLine>()

    rawLines.forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("# ") -> {
                parsed.add(MarkdownLine.Header(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("## ") -> {
                parsed.add(MarkdownLine.Header(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("### ") -> {
                parsed.add(MarkdownLine.Header(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("> ") -> {
                parsed.add(MarkdownLine.BlockQuote(trimmed.removePrefix("> ").trim()))
            }
            trimmed.startsWith("- ") -> {
                parsed.add(MarkdownLine.BulletItem(trimmed.removePrefix("- ").trim()))
            }
            trimmed.startsWith("* ") -> {
                parsed.add(MarkdownLine.BulletItem(trimmed.removePrefix("* ").trim()))
            }
            trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                parsed.add(MarkdownLine.Separator)
            }
            // Check numbered list match e.g. "1. " or "12. "
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val match = Regex("^(\\d+)\\.\\s+(.*)").find(trimmed)
                if (match != null) {
                    val num = match.groupValues[1]
                    val content = match.groupValues[2]
                    parsed.add(MarkdownLine.NumberedItem(num, content))
                } else {
                    parsed.add(MarkdownLine.Paragraph(line))
                }
            }
            else -> {
                // Keep the original spacing if not empty
                parsed.add(MarkdownLine.Paragraph(line))
            }
        }
    }
    return parsed
}

/**
 * High-performance sequential scanner to parse inline formatting safely.
 */
fun parseInlineMarkdown(text: String, isDark: Boolean): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val n = text.length
        while (i < n) {
            // Check inline code first: `code`
            if (text[i] == '`') {
                val endIdx = text.indexOf('`', i + 1)
                if (endIdx != -1) {
                    val codeContent = text.substring(i + 1, endIdx)
                    val start = length
                    append(codeContent)
                    addStyle(
                        style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = if (isDark) Color(0xFF2E2E2E) else Color(0xFFE5E7EB),
                            color = if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
                        ),
                        start = start,
                        end = length
                    )
                    i = endIdx + 1
                    continue
                }
            }

            // Check bold: **text** or __text__
            if (i + 1 < n && text[i] == '*' && text[i + 1] == '*') {
                val endIdx = text.indexOf("**", i + 2)
                if (endIdx != -1) {
                    val boldContent = text.substring(i + 2, endIdx)
                    val start = length
                    append(boldContent)
                    addStyle(
                        style = SpanStyle(fontWeight = FontWeight.Bold),
                        start = start,
                        end = length
                    )
                    i = endIdx + 2
                    continue
                }
            }

            // Check italic: *text*
            if (text[i] == '*') {
                val endIdx = text.indexOf('*', i + 1)
                if (endIdx != -1) {
                    val italicContent = text.substring(i + 1, endIdx)
                    val start = length
                    append(italicContent)
                    addStyle(
                        style = SpanStyle(fontStyle = FontStyle.Italic),
                        start = start,
                        end = length
                    )
                    i = endIdx + 1
                    continue
                }
            }

            // Default fallback
            append(text[i])
            i++
        }
    }
}
