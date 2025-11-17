package com.claude.chat.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.claude.chat.presentation.settings.SettingsIntent
import com.claude.chat.presentation.settings.SettingsUiState

/**
 * Settings screen UI
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onIntent: (SettingsIntent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            // Auto-dismiss after showing success
            kotlinx.coroutines.delay(2000)
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

            // JSON Mode Section
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
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
}
