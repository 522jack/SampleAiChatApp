package com.claude.chat.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.claude.chat.data.model.ClaudeModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    modelProvider: String = "CLAUDE",
    ollamaModels: List<String> = emptyList()
) {
    var expanded by remember { mutableStateOf(false) }

    // Determine display name based on provider
    val displayName = if (modelProvider == "OLLAMA") {
        selectedModel.ifEmpty { "Select Model" }
    } else {
        ClaudeModel.fromModelId(selectedModel).displayName
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.menuAnchor()
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelMedium
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Select model"
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (modelProvider == "OLLAMA") {
                // Show Ollama models
                if (ollamaModels.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "No models available",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        onClick = { },
                        enabled = false
                    )
                } else {
                    ollamaModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = model,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = {
                                onModelSelected(model)
                                expanded = false
                            },
                            contentPadding = PaddingValues(
                                horizontal = 16.dp,
                                vertical = 8.dp
                            )
                        )
                    }
                }
            } else {
                // Show Claude models
                val models = ClaudeModel.entries
                models.forEach { model ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = model.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = model.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            onModelSelected(model.modelId)
                            expanded = false
                        },
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                    )
                }
            }
        }
    }
}