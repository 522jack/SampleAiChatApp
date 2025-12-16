package com.claude.chat.presentation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Chat input bar with text field, voice input button and send button
 */
@Composable
fun ChatInputBar(
    enabled: Boolean,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    isRecording: Boolean = false,
    onStartVoiceInput: () -> Unit = {},
    onStopVoiceInput: () -> Unit = {}
) {
    var text by remember { mutableStateOf("") }

    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom)),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f),
                placeholder = { Text("Type a message...") },
                enabled = enabled && !isRecording,
                maxLines = 5
            )

            // Voice input button
            VoiceInputButton(
                isRecording = isRecording,
                enabled = enabled && text.isBlank(),
                onStartRecording = onStartVoiceInput,
                onStopRecording = onStopVoiceInput
            )

            // Send button
            FilledIconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendMessage(text)
                        text = ""
                    }
                },
                enabled = enabled && text.isNotBlank() && !isRecording
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message"
                )
            }
        }
    }
}
