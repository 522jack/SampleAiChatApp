package com.claude.chat.presentation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.claude.chat.domain.model.Message
import com.claude.chat.domain.model.MessageRole
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Message bubble component
 */
@Composable
fun MessageBubble(
    message: Message,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val isSystem = message.role == MessageRole.SYSTEM

    // Check if this is a summary message (conversation compression)
    if (message.isSummary && isSystem) {
        // Display summary card
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📝",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column {
                        Text(
                            text = "Conversation Summary",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (message.summarizedTokens != null && message.tokensSaved != null) {
                            Text(
                                text = "Summarized ${message.summarizedTokens} tokens • Saved ~${message.tokensSaved} tokens",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else if (isSystem) {
        // Display task summary (system message)
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Task Summary",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Divider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f)
                )

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2f
                    ),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )

                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    } else if (message.comparisonResponse != null) {
        // Display comparison card for assistant messages with comparison data
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelComparisonCard(comparisonResponse = message.comparisonResponse)

            // Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        // Regular message bubble
        val bubbleColor = when {
            message.isError -> MaterialTheme.colorScheme.errorContainer
            isUser -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        }

        val textColor = when {
            message.isError -> MaterialTheme.colorScheme.onErrorContainer
            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        }

        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
            ) {
                Card(
                    modifier = Modifier.widthIn(max = 320.dp),
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = bubbleColor
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Show RAG indicator for assistant messages generated from knowledge base
                        if (!isUser && message.isFromRag) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📚",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "Knowledge Base",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }

                        // Use MarkdownText for assistant messages to support clickable links
                        if (!isUser) {
                            MarkdownText(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        } else {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColor
                            )
                        }

                        // Token usage display for assistant messages
                        if (!isUser && (message.inputTokens != null || message.outputTokens != null)) {
                            Text(
                                text = buildString {
                                    append("Tokens: ")
                                    message.inputTokens?.let { append("in=$it ") }
                                    message.outputTokens?.let { append("out=$it") }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimestamp(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.7f)
                            )

                            IconButton(
                                onClick = onCopy,
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy message",
                                    modifier = Modifier.size(16.dp),
                                    tint = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // Extract and display links as chips below the message bubble
            if (!isUser) {
                val links = extractLinksFromText(message.content)
                if (links.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinkChipsRow(
                        links = links,
                        modifier = Modifier.widthIn(max = 320.dp)
                    )
                }
            }
        }
    }
}

/**
 * Extracts all markdown links from text
 * Returns list of pairs: (link text, link url)
 */
private fun extractLinksFromText(text: String): List<Pair<String, String>> {
    val linkPattern = Regex("""\[([^\]]+)]\(([^)]+)\)""")
    return linkPattern.findAll(text).map { matchResult ->
        val linkText = matchResult.groupValues[1]
        val linkUrl = matchResult.groupValues[2]
        linkText to linkUrl
    }.toList()
}

/**
 * Displays a horizontal scrollable row of link chips
 */
@Composable
private fun LinkChipsRow(
    links: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    // Get theme values outside of lambda
    val chipContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    val chipLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    val labelStyle = MaterialTheme.typography.labelMedium

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        links.forEach { (linkText, linkUrl) ->
            AssistChip(
                onClick = {
                    try {
                        uriHandler.openUri(linkUrl)
                    } catch (_: Exception) {
                        println("Failed to open URL: $linkUrl")
                    }
                },
                label = {
                    Text(
                        text = linkText,
                        style = labelStyle,
                        maxLines = 1
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = chipContainerColor,
                    labelColor = chipLabelColor,
                    leadingIconContentColor = chipLabelColor
                )
            )
        }
    }
}

private fun formatTimestamp(timestamp: Instant): String {
    val localDateTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.hour.toString().padStart(2, '0')}:${localDateTime.minute.toString().padStart(2, '0')}"
}
