package com.claude.chat.presentation.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claude.chat.data.model.McpTool
import com.claude.chat.presentation.ui.ChatInputBar
import com.claude.chat.presentation.ui.McpToolDialog
import com.claude.chat.presentation.ui.McpToolsIndicator
import com.claude.chat.presentation.ui.McpToolsList
import com.claude.chat.presentation.ui.MessageBubble
import com.claude.chat.presentation.ui.ModelSelector
import com.claude.chat.presentation.ui.TypingIndicator
import kotlinx.coroutines.launch

/**
 * Main chat screen UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    state: ChatUiState,
    onIntent: (ChatIntent) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var selectedTool by remember { mutableStateOf<McpTool?>(null) }

    // Show MCP Tool Dialog
    selectedTool?.let { tool ->
        McpToolDialog(
            tool = tool,
            onDismiss = { selectedTool = null },
            onExecute = { arguments ->
                onIntent(ChatIntent.ExecuteMcpTool(tool.name, arguments))
            }
        )
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(state.messages.size - 1)
            }
        }
    }

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Claude Chat")
                        // MCP Tools indicator
                        if (state.mcpEnabled && state.availableMcpTools.isNotEmpty()) {
                            McpToolsIndicator(toolCount = state.availableMcpTools.size)
                        }
                    }
                },
                actions = {
                    // Hide model selector when comparison mode is enabled
                    if (!state.isModelComparisonMode) {
                        ModelSelector(
                            selectedModel = state.selectedModel,
                            onModelSelected = { modelId ->
                                onIntent(ChatIntent.SelectModel(modelId))
                            }
                        )
                    } else {
                        // Show indicator that comparison mode is active
                        Text(
                            "Comparison Mode",
                            modifier = Modifier.padding(horizontal = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        onIntent(ChatIntent.ClearHistory)
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Clear history")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                enabled = state.isApiKeyConfigured && !state.isLoading,
                onSendMessage = { text ->
                    onIntent(ChatIntent.SendMessage(text))
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!state.isApiKeyConfigured) {
                // Show API key setup message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "API Key Not Configured",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            "Please configure your Claude API key in settings",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onNavigateToSettings) {
                            Text("Open Settings")
                        }
                    }
                }
            } else if (state.messages.isEmpty()) {
                // Show empty state with MCP tools if available
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        "Start a conversation with Claude",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Show MCP tools list when enabled
                    if (state.mcpEnabled && state.availableMcpTools.isNotEmpty()) {
                        McpToolsList(
                            tools = state.availableMcpTools,
                            onToolClick = { tool -> selectedTool = tool },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                // Show messages
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Show MCP tools at the top of chat
                    if (state.mcpEnabled && state.availableMcpTools.isNotEmpty()) {
                        item {
                            McpToolsList(
                                tools = state.availableMcpTools,
                                onToolClick = { tool -> selectedTool = tool },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    items(
                        items = state.messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            onCopy = { onIntent(ChatIntent.CopyMessage(message)) }
                        )
                    }

                    if (state.isLoading) {
                        item {
                            TypingIndicator()
                        }
                    }
                }
            }

            // Error snackbar
            state.error?.let { error ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { onIntent(ChatIntent.RetryLastMessage) }) {
                            Text("Retry")
                        }
                    }
                ) {
                    Text(error)
                }
            }

            // Compression notification
            state.compressionNotification?.let { notification ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { onIntent(ChatIntent.DismissCompressionNotification) }) {
                            Text("OK")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(notification)
                }
            }
        }
    }
}
