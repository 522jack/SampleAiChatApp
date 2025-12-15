package com.claude.chat.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.claude.chat.platform.rememberFilePickerHelper
import com.claude.chat.presentation.settings.mvi.SettingsIntent
import com.claude.chat.presentation.settings.mvi.SettingsUiState
import kotlinx.coroutines.delay

/**
 * Settings screen UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSupport: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            // Auto-dismiss after showing success
            delay(2000)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // API Key Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Claude API Key",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = { onIntent(SettingsIntent.UpdateApiKeyInput(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key") },
                        placeholder = { Text("sk-ant-api...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onIntent(SettingsIntent.SaveApiKey(state.apiKey)) },
                            modifier = Modifier.weight(1f),
                            enabled = state.apiKey.isNotBlank()
                        ) {
                            Text("Save API Key")
                        }

                        if (state.apiKey.isNotBlank()) {
                            OutlinedButton(
                                onClick = { onIntent(SettingsIntent.ClearApiKey) },
                                modifier = Modifier.weight(0.5f)
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    // Show API key info if it exists
                    if (state.apiKey.isNotBlank()) {
                        val keyPreview = if (state.apiKey.length > 15) {
                            "${state.apiKey.take(10)}...${state.apiKey.takeLast(4)}"
                        } else {
                            state.apiKey.take(10) + "..."
                        }

                        Text(
                            "Current key: $keyPreview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        "Get your API key from: https://console.anthropic.com/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Model Provider Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Model Provider",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Choose between cloud-based Claude or local Ollama models",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Provider selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = state.modelProvider == "CLAUDE",
                            onClick = { onIntent(SettingsIntent.UpdateModelProvider("CLAUDE")) },
                            label = { Text("Claude (Cloud)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = state.modelProvider == "OLLAMA",
                            onClick = { onIntent(SettingsIntent.UpdateModelProvider("OLLAMA")) },
                            label = { Text("Ollama (Local)") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Ollama settings (show only when OLLAMA is selected)
                    if (state.modelProvider == "OLLAMA") {
                        HorizontalDivider()

                        // Ollama status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (state.ollamaHealthy)
                                    Icons.Default.CheckCircle
                                else
                                    Icons.Default.Error,
                                contentDescription = if (state.ollamaHealthy) "Connected" else "Disconnected",
                                tint = if (state.ollamaHealthy)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                            Text(
                                if (state.ollamaHealthy)
                                    "Ollama is running"
                                else
                                    "Ollama is not available. Please start Ollama.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.ollamaHealthy)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }

                        // Base URL
                        OutlinedTextField(
                            value = state.ollamaBaseUrl,
                            onValueChange = { onIntent(SettingsIntent.UpdateOllamaBaseUrlInput(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ollama Base URL") },
                            placeholder = { Text("http://localhost:11434") },
                            singleLine = true
                        )

                        // Apply URL button
                        Button(
                            onClick = { onIntent(SettingsIntent.SaveOllamaBaseUrl(state.ollamaBaseUrl)) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = state.ollamaBaseUrl.isNotBlank()
                        ) {
                            Text("Apply URL")
                        }

                        // Model selection
                        if (state.availableOllamaModels.isNotEmpty()) {
                            var expanded by remember { mutableStateOf(false) }

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded }
                            ) {
                                OutlinedTextField(
                                    value = state.ollamaModel,
                                    onValueChange = { },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(),
                                    label = { Text("Model") },
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    state.availableOllamaModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                onIntent(SettingsIntent.UpdateOllamaModel(model))
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            OutlinedTextField(
                                value = state.ollamaModel,
                                onValueChange = { onIntent(SettingsIntent.UpdateOllamaModel(it)) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Model") },
                                placeholder = { Text("llama2, mistral, etc.") },
                                singleLine = true
                            )

                            if (!state.ollamaHealthy) {
                                Text(
                                    "Manual entry mode. Start Ollama to see available models.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Refresh models button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onIntent(SettingsIntent.CheckOllamaHealth) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Check Status")
                            }

                            Button(
                                onClick = { onIntent(SettingsIntent.RefreshOllamaModels) },
                                modifier = Modifier.weight(1f),
                                enabled = state.ollamaHealthy
                            ) {
                                Text("Refresh Models")
                            }
                        }

                        // Help text
                        Text(
                            "Install Ollama from ollama.com and run 'ollama pull llama2' to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // System Prompt Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "System Prompt (Optional)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = state.systemPrompt,
                        onValueChange = { onIntent(SettingsIntent.UpdateSystemPromptInput(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        label = { Text("System Prompt") },
                        placeholder = { Text("You are a helpful assistant...") },
                        maxLines = 5
                    )

                    Button(
                        onClick = { onIntent(SettingsIntent.SaveSystemPrompt(state.systemPrompt)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save System Prompt")
                    }
                }
            }

            // Temperature Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Temperature",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Controls randomness in responses. Range: 0.0 (focused) to 1.0 (creative)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = state.temperature,
                        onValueChange = { onIntent(SettingsIntent.UpdateTemperatureInput(it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Temperature") },
                        placeholder = { Text("1.0") },
                        singleLine = true
                    )

                    Button(
                        onClick = { onIntent(SettingsIntent.SaveTemperature(state.temperature)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Temperature")
                    }
                }
            }

            // Theme Mode Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Theme",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Choose between light, dark, or system theme",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (value, label) ->
                            FilterChip(
                                selected = state.themeMode == value,
                                onClick = { onIntent(SettingsIntent.UpdateThemeMode(value)) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // JSON Mode Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "JSON Response Format",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Enable to receive responses in JSON format",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.jsonModeEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.ToggleJsonMode(it)) }
                    )
                }
            }

            // Tech Spec Mode Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Technical Specification Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Agent will ask 5 clarifying questions before creating a specification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.techSpecModeEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.ToggleTechSpecMode(it)) }
                    )
                }
            }

            // Model Comparison Mode Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Model Comparison Mode",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Get responses from 3 models (Haiku 3, Sonnet 3.7, Sonnet 4.5) with metrics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.modelComparisonModeEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.ToggleModelComparisonMode(it)) }
                    )
                }
            }

            // MCP (Model Context Protocol) Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "MCP Tools",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Enable Model Context Protocol tools (calculator, time, JSON formatter, etc.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.mcpEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.ToggleMcp(it)) }
                    )
                }
            }

            // MCP External Servers Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "External MCP Servers",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = { onIntent(SettingsIntent.ShowAddServerDialog) }
                        ) {
                            Text("Add Server")
                        }
                    }

                    Text(
                        "Connect to external MCP servers (Weather, Files, etc.)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.mcpServers.isEmpty()) {
                        Text(
                            "No external servers configured. Add one to get started!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        state.mcpServers.forEach { server ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            server.name,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        val config = server.config
                                        if (config is com.claude.chat.data.model.McpConnectionConfig.HttpConfig) {
                                            Text(
                                                config.url,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            "Type: ${server.type.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Switch(
                                            checked = server.enabled,
                                            onCheckedChange = {
                                                onIntent(SettingsIntent.ToggleMcpServer(server.id, it))
                                            }
                                        )
                                        IconButton(
                                            onClick = { onIntent(SettingsIntent.RemoveMcpServer(server.id)) }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove server",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // RAG Mode Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "RAG Mode (Knowledge Base)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Use indexed documents as knowledge base for responses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.ragModeEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.ToggleRagMode(it)) }
                    )
                }
            }

            // RAG Reranking Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "RAG Reranking",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Use LLM-based reranking for more accurate search results (slower but more precise)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.ragRerankingEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.ToggleRagReranking(it)) },
                        enabled = state.ragModeEnabled
                    )
                }
            }

            // RAG Documents Management Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Knowledge Base Documents",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(
                            onClick = { onIntent(SettingsIntent.ShowAddDocumentDialog) }
                        ) {
                            Text("Add Document")
                        }
                    }

                    Text(
                        "Documents are indexed and used to answer questions when RAG mode is enabled. Requires OLLAMA running locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state.ragDocuments.isEmpty()) {
                        Text(
                            "No documents indexed. Add documents to build your knowledge base!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Text(
                            "${state.ragDocuments.size} document(s) indexed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        state.ragDocuments.forEach { document ->
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            document.title,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            "${document.content.length} characters",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            "Added: ${document.timestamp}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { onIntent(SettingsIntent.RemoveRagDocument(document.id)) }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Remove document",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // User Profile Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "User Profile",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        "Load a JSON file with your profile to personalize AI agent behavior. The profile will be included in all conversations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Current profile display
                    if (state.userProfile != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Active Profile",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            state.userProfile.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        if (state.userProfile.bio != null) {
                                            Text(
                                                state.userProfile.bio,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Profile active",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // File picker and actions
                    val filePickerHelper = rememberFilePickerHelper()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                filePickerHelper.pickTextFile { content ->
                                    content?.let {
                                        onIntent(SettingsIntent.LoadUserProfile(it))
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Upload,
                                contentDescription = "Upload profile",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (state.userProfile != null) "Replace" else "Load Profile")
                        }

                        if (state.userProfile != null) {
                            OutlinedButton(
                                onClick = { onIntent(SettingsIntent.ClearUserProfile) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Clear profile",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }

                    // Info text
                    Text(
                        "Tip: See user_profile_example.json in project root for format reference",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Support Service Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "AI-Powered Support",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Text(
                        "Get instant answers to your questions using AI assistant with knowledge base and ticket history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )

                    Button(
                        onClick = onNavigateToSupport,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Support Assistant")
                    }
                }
            }

            // Clear Data Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Danger Zone",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Clear All Chat History")
                    }
                }
            }

            // Success/Error messages
            if (state.saveSuccess) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        "Settings saved successfully!",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            state.error?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Data?") },
            text = { Text("This will delete all chat history. This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onIntent(SettingsIntent.ClearAllData)
                        showClearDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add MCP Server dialog
    if (state.showAddServerDialog) {
        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.HideAddServerDialog) },
            title = { Text("Add MCP Server") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = state.newServerName,
                        onValueChange = { onIntent(SettingsIntent.UpdateServerName(it)) },
                        label = { Text("Server Name") },
                        placeholder = { Text("Weather Server") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = state.newServerUrl,
                        onValueChange = { onIntent(SettingsIntent.UpdateServerUrl(it)) },
                        label = { Text("Server URL") },
                        placeholder = { Text("http://localhost:3000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Add a URL to an external MCP server. The server must be running and accessible.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { onIntent(SettingsIntent.SaveNewServer) },
                    enabled = state.newServerName.isNotBlank() && state.newServerUrl.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(SettingsIntent.HideAddServerDialog) }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add RAG Document dialog
    if (state.showAddDocumentDialog) {
        val filePickerHelper = rememberFilePickerHelper()

        AlertDialog(
            onDismissRequest = { onIntent(SettingsIntent.HideAddDocumentDialog) },
            title = { Text("Add Document to Knowledge Base") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = state.newDocumentTitle,
                        onValueChange = { onIntent(SettingsIntent.UpdateDocumentTitle(it)) },
                        label = { Text("Document Title") },
                        placeholder = { Text("Kotlin Documentation") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    )

                    // Show selected file info if content is loaded
                    if (state.newDocumentContent.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "File loaded",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        "${state.newDocumentContent.length} characters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { onIntent(SettingsIntent.UpdateDocumentContent("")) }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Clear file"
                                    )
                                }
                            }
                        }
                    }

                    // File upload button
                    Button(
                        onClick = {
                            filePickerHelper.pickTextFile { content ->
                                content?.let {
                                    onIntent(SettingsIntent.UpdateDocumentContent(it))
                                    // Auto-set title from first line if title is empty
                                    if (state.newDocumentTitle.isBlank()) {
                                        val firstLine = it.lines().firstOrNull()?.take(50) ?: "Document"
                                        onIntent(SettingsIntent.UpdateDocumentTitle(firstLine))
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading
                    ) {
                        Icon(
                            Icons.Default.Upload,
                            contentDescription = "Upload file",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Select File (.txt, .md)")
                    }

                    if (state.error == null) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    "Requirements:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "• OLLAMA running on localhost:11434",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "• Model: nomic-embed-text installed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "• Install: https://ollama.ai",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Show error in dialog if present
                    state.error?.let { error ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                error,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    if (state.isLoading) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Indexing document...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onIntent(SettingsIntent.SaveNewDocument) },
                    enabled = state.newDocumentTitle.isNotBlank() &&
                             state.newDocumentContent.isNotBlank() &&
                             !state.isLoading
                ) {
                    Text("Index Document")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onIntent(SettingsIntent.HideAddDocumentDialog) },
                    enabled = !state.isLoading
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
