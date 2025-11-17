package com.claude.chat.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claude.chat.data.model.McpTool
import kotlinx.serialization.json.*

@Composable
fun McpToolDialog(
    tool: McpTool,
    onDismiss: () -> Unit,
    onExecute: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val parameters = remember { extractParameters(tool) }
    val parameterValues = remember { mutableStateMapOf<String, String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = tool.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                tool.description?.let { desc ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (parameters.isEmpty()) {
                    Text(
                        text = "This tool doesn't require any parameters",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    parameters.forEach { param ->
                        ParameterInput(
                            parameter = param,
                            value = parameterValues[param.name] ?: "",
                            onValueChange = { parameterValues[param.name] = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExecute(parameterValues.toMap())
                    onDismiss()
                }
            ) {
                Text("Execute")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ParameterInput(
    parameter: ToolParameter,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(parameter.name)
                    if (parameter.required) {
                        Text("*", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            placeholder = { Text(parameter.description ?: "") },
            modifier = Modifier.fillMaxWidth(),
            supportingText = {
                parameter.description?.let { Text(it) }
            },
            isError = parameter.required && value.isBlank()
        )
    }
}

private data class ToolParameter(
    val name: String,
    val description: String?,
    val required: Boolean,
    val type: String
)

private fun extractParameters(tool: McpTool): List<ToolParameter> {
    val parameters = mutableListOf<ToolParameter>()

    try {
        val inputSchema = tool.inputSchema
        val properties = inputSchema["properties"]?.jsonObject ?: return emptyList()
        val required = inputSchema["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()

        properties.forEach { (name, propValue) ->
            val prop = propValue.jsonObject
            val description = prop["description"]?.jsonPrimitive?.contentOrNull
            val type = prop["type"]?.jsonPrimitive?.contentOrNull ?: "string"

            parameters.add(
                ToolParameter(
                    name = name,
                    description = description,
                    required = name in required,
                    type = type
                )
            )
        }
    } catch (e: Exception) {
        // If we can't parse the schema, return empty list
        return emptyList()
    }

    return parameters
}