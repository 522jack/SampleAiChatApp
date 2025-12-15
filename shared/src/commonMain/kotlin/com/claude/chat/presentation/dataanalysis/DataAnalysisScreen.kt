package com.claude.chat.presentation.dataanalysis

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claude.chat.domain.model.DataFileType
import com.claude.chat.domain.model.MessageRole
import com.claude.chat.platform.FilePickerHelper
import com.claude.chat.platform.rememberFilePickerHelper
import com.claude.chat.presentation.dataanalysis.mvi.DataAnalysisIntent
import com.claude.chat.presentation.dataanalysis.mvi.DataAnalysisUiState
import com.claude.chat.presentation.ui.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataAnalysisScreen(
    viewModel: DataAnalysisViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val filePicker = rememberFilePickerHelper()
    var inputText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Анализ данных") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    // Upload file button
                    IconButton(onClick = {
                        filePicker.pickTextFile { content ->
                            content?.let {
                                viewModel.onIntent(
                                    DataAnalysisIntent.LoadDataFile(
                                        fileName = "uploaded_file.txt",
                                        content = it
                                    )
                                )
                            }
                        }
                    }) {
                        Icon(Icons.Default.Add, "Загрузить файл")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Files list
            if (state.loadedFiles.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Загруженные файлы:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        state.loadedFiles.forEach { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        file.name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${file.type.name} • ${file.metadata.rowCount ?: 0} строк",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.onIntent(DataAnalysisIntent.RemoveDataFile(file.id))
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, "Удалить")
                                }
                            }
                        }
                    }
                }
            }

            // Error message
            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error,
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Chat messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                reverseLayout = true
            ) {
                items(state.messages.reversed()) { message ->
                    MessageBubble(
                        message = message,
                        onCopy = { /* TODO */ }
                    )
                }
            }

            // Input field
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Задайте вопрос о данных...") },
                    enabled = !state.isProcessing && state.loadedFiles.isNotEmpty()
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.onIntent(DataAnalysisIntent.SendQuestion(inputText))
                            inputText = ""
                        }
                    },
                    enabled = !state.isProcessing && inputText.isNotBlank()
                ) {
                    if (state.isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Send, "Отправить")
                    }
                }
            }

            // Loading indicator
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}