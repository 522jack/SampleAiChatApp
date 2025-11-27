package com.claude.chat.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * Composable that renders text with markdown formatting
 * Supports:
 * - Links: [text](url)
 * - Block quotes: > text
 * - Bold: **text**
 * - Italic: *text*
 * - Inline code: `code`
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val quoteBackgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val quoteBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val codeBackgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    // Split text into lines to handle block quotes
    val lines = text.lines()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]

            // Check if line is a block quote
            if (line.trimStart().startsWith(">")) {
                // Collect all consecutive quote lines
                val quoteLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].trimStart().startsWith(">")) {
                    quoteLines.add(lines[j].trimStart().removePrefix(">").trimStart())
                    j++
                }

                // Render quote block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(quoteBackgroundColor)
                        .padding(start = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(quoteBorderColor)
                    )

                    val quoteText = quoteLines.joinToString("\n")
                    MarkdownInlineText(
                        text = quoteText,
                        style = style.copy(fontStyle = FontStyle.Italic),
                        color = color.copy(alpha = 0.9f),
                        linkColor = linkColor,
                        codeBackgroundColor = codeBackgroundColor,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                        uriHandler = uriHandler
                    )
                }

                i = j
            } else if (line.isNotBlank()) {
                // Regular line with inline formatting
                MarkdownInlineText(
                    text = line,
                    style = style,
                    color = color,
                    linkColor = linkColor,
                    codeBackgroundColor = codeBackgroundColor,
                    uriHandler = uriHandler
                )
                i++
            } else {
                // Empty line - add small space
                Spacer(modifier = Modifier.height(4.dp))
                i++
            }
        }
    }
}

/**
 * Renders text with inline markdown formatting (links, bold, italic, code)
 */
@Composable
private fun MarkdownInlineText(
    text: String,
    style: TextStyle,
    color: Color,
    linkColor: Color,
    codeBackgroundColor: Color,
    modifier: Modifier = Modifier,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    // Parse inline markdown and create annotated string
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val processedText = text

        // Pattern to match: links, bold, italic, inline code
        // Order matters: links first, then bold, then italic, then code
        val patterns = listOf(
            "LINK" to Regex("""\[([^\]]+)]\(([^)]+)\)"""),
            "BOLD" to Regex("""\*\*([^*]+)\*\*"""),
            "ITALIC" to Regex("""\*([^*]+)\*"""),
            "CODE" to Regex("""`([^`]+)`""")
        )

        // Find all matches and sort by position
        val allMatches = mutableListOf<Triple<String, MatchResult, Int>>()
        patterns.forEach { (type, pattern) ->
            pattern.findAll(processedText).forEach { match ->
                allMatches.add(Triple(type, match, match.range.first))
            }
        }
        allMatches.sortBy { it.third }

        // Remove overlapping matches (keep first occurrence)
        val nonOverlapping = mutableListOf<Triple<String, MatchResult, Int>>()
        var lastEnd = -1
        allMatches.forEach { (type, match, _) ->
            if (match.range.first >= lastEnd) {
                nonOverlapping.add(Triple(type, match, match.range.first))
                lastEnd = match.range.last + 1
            }
        }

        // Build annotated string
        nonOverlapping.forEach { (type, matchResult, _) ->
            // Add text before match
            if (matchResult.range.first > currentIndex) {
                pushStyle(SpanStyle(color = color))
                append(processedText.substring(currentIndex, matchResult.range.first))
                pop()
            }

            when (type) {
                "LINK" -> {
                    val linkText = matchResult.groupValues[1]
                    val linkUrl = matchResult.groupValues[2]

                    pushStringAnnotation(tag = "URL", annotation = linkUrl)
                    pushStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                    append(linkText)
                    pop()
                    pop()
                }
                "BOLD" -> {
                    val boldText = matchResult.groupValues[1]
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color))
                    append(boldText)
                    pop()
                }
                "ITALIC" -> {
                    val italicText = matchResult.groupValues[1]
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = color))
                    append(italicText)
                    pop()
                }
                "CODE" -> {
                    val codeText = matchResult.groupValues[1]
                    pushStyle(
                        SpanStyle(
                            background = codeBackgroundColor,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = color
                        )
                    )
                    append(" $codeText ")
                    pop()
                }
            }

            currentIndex = matchResult.range.last + 1
        }

        // Add remaining text
        if (currentIndex < processedText.length) {
            pushStyle(SpanStyle(color = color))
            append(processedText.substring(currentIndex))
            pop()
        }
    }

    ClickableText(
        text = annotatedString,
        style = style,
        modifier = modifier,
        onClick = { offset ->
            annotatedString.getStringAnnotations(
                tag = "URL",
                start = offset,
                end = offset
            ).firstOrNull()?.let { annotation ->
                try {
                    uriHandler.openUri(annotation.item)
                } catch (e: Exception) {
                    println("Failed to open URL: ${annotation.item}")
                }
            }
        }
    )
}