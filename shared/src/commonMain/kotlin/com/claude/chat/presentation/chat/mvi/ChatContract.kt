package com.claude.chat.presentation.chat.mvi

import com.claude.chat.data.model.McpTool
import com.claude.chat.domain.manager.TechSpecManager
import com.claude.chat.domain.model.Message

/**
 * UI State for chat screen
 */
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isApiKeyConfigured: Boolean = false,
    val isTechSpecMode: Boolean = false,
    val techSpecState: TechSpecManager.TechSpecState = TechSpecManager.TechSpecState(),
    val selectedModel: String = "claude-3-5-haiku-20241022",
    val isModelComparisonMode: Boolean = false,
    val compressionNotification: String? = null,
    val mcpEnabled: Boolean = false,
    val availableMcpTools: List<McpTool> = emptyList(),
    val isTaskSummaryEnabled: Boolean = false,
    val isRagMode: Boolean = false,
    val ragDocuments: List<com.claude.chat.data.model.RagDocument> = emptyList(),
    // Model Provider
    val modelProvider: String = "CLAUDE",
    val ollamaModels: List<String> = emptyList()
)

/**
 * User intents for chat screen
 */
sealed class ChatIntent {
    data class SendMessage(val text: String) : ChatIntent()
    data object ClearHistory : ChatIntent()
    data object RetryLastMessage : ChatIntent()
    data class CopyMessage(val message: Message) : ChatIntent()
    data object LoadMessages : ChatIntent()
    data object CheckApiKey : ChatIntent()
    data class SelectModel(val modelId: String) : ChatIntent()
    data object ReloadSettings : ChatIntent()
    data object DismissCompressionNotification : ChatIntent()
    data class ExecuteMcpTool(val toolName: String, val arguments: Map<String, String>) : ChatIntent()
    data object StartTaskSummaryPolling : ChatIntent()
    data object StopTaskSummaryPolling : ChatIntent()
    data object RequestTaskSummary : ChatIntent()
    data class IndexDocument(val title: String, val content: String) : ChatIntent()
    data class RemoveRagDocument(val documentId: String) : ChatIntent()
    data object LoadRagDocuments : ChatIntent()
}