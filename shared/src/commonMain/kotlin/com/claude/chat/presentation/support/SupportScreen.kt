package com.claude.chat.presentation.support

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claude.chat.presentation.ui.MarkdownText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    viewModel: SupportViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Техподдержка") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    if (state.answer != null) {
                        IconButton(onClick = { viewModel.clearForm() }) {
                            Icon(Icons.Default.Delete, "Очистить")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Информация о сервисе
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "AI-ассистент поддержки",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Задайте вопрос о приложении, и я помогу найти ответ в документации и базе решенных тикетов.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Поле ввода вопроса
            OutlinedTextField(
                value = state.question,
                onValueChange = { viewModel.updateQuestion(it) },
                label = { Text("Ваш вопрос") },
                placeholder = { Text("Например: Почему не работает авторизация?") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                minLines = 3,
                maxLines = 5
            )

            // Кнопка отправки
            Button(
                onClick = { viewModel.askQuestion() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && state.question.isNotBlank()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Обрабатываю запрос...")
                } else {
                    Text("Получить ответ")
                }
            }

            // Ошибка
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

            // Ответ
            state.answer?.let { answer ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Категория вопроса
                    state.category?.let { category ->
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "Категория: $category",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Ответ ассистента
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Ответ:",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            MarkdownText(answer)
                        }
                    }

                    // Источники
                    if (state.sources.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Источники (${state.sources.size}):",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                state.sources.forEachIndexed { index, source ->
                                    Row(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val icon = when (source.type) {
                                            "doc" -> "\uD83D\uDCDA" // 📚
                                            "ticket" -> "\uD83C\uDFAB" // 🎫
                                            else -> "•"
                                        }
                                        Text(icon)
                                        Text(
                                            "${index + 1}. ${source.title}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}