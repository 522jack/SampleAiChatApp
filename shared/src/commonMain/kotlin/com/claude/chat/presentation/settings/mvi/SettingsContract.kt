package com.claude.chat.presentation.settings.mvi

import com.claude.chat.data.model.McpServerConfig
import com.claude.chat.data.model.RagDocument

/**
 * UI State for settings screen
 */
data class SettingsUiState(
    val apiKey: String = "",
    val systemPrompt: String = "",
    val jsonModeEnabled: Boolean = false,
    val techSpecModeEnabled: Boolean = false,
    val modelComparisonModeEnabled: Boolean = false,
    val mcpEnabled: Boolean = false,
    val temperature: String = "1.0",
    val themeMode: String = "SYSTEM",
    val isLoading: Boolean = true,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    // Model Provider settings
    val modelProvider: String = "CLAUDE",
    val ollamaBaseUrl: String = "http://localhost:11434",
    val ollamaModel: String = "llama2",
    val availableOllamaModels: List<String> = emptyList(),
    val ollamaHealthy: Boolean = false,
    // MCP Server management
    val mcpServers: List<McpServerConfig> = emptyList(),
    val showAddServerDialog: Boolean = false,
    val newServerName: String = "",
    val newServerUrl: String = "",
    // RAG management
    val ragModeEnabled: Boolean = false,
    val ragRerankingEnabled: Boolean = false,
    val ragDocuments: List<RagDocument> = emptyList(),
    val showAddDocumentDialog: Boolean = false,
    val newDocumentTitle: String = "",
    val newDocumentContent: String = "",
    // User Profile management
    val userProfile: com.claude.chat.domain.model.UserProfile? = null,
    val isUserProfileActive: Boolean = false
)

/**
 * User intents for settings screen
 */
sealed class SettingsIntent {
    data class SaveApiKey(val apiKey: String) : SettingsIntent()
    data class SaveSystemPrompt(val prompt: String) : SettingsIntent()
    data class ToggleJsonMode(val enabled: Boolean) : SettingsIntent()
    data class ToggleTechSpecMode(val enabled: Boolean) : SettingsIntent()
    data class ToggleModelComparisonMode(val enabled: Boolean) : SettingsIntent()
    data class ToggleMcp(val enabled: Boolean) : SettingsIntent()
    data object ClearAllData : SettingsIntent()
    data object ClearApiKey : SettingsIntent()
    data class UpdateApiKeyInput(val apiKey: String) : SettingsIntent()
    data class UpdateSystemPromptInput(val prompt: String) : SettingsIntent()
    data class UpdateTemperatureInput(val temperature: String) : SettingsIntent()
    data class SaveTemperature(val temperature: String) : SettingsIntent()
    // MCP Server management
    data object AddMcpServer : SettingsIntent()
    data class RemoveMcpServer(val serverId: String) : SettingsIntent()
    data class ToggleMcpServer(val serverId: String, val enabled: Boolean) : SettingsIntent()
    data object ShowAddServerDialog : SettingsIntent()
    data object HideAddServerDialog : SettingsIntent()
    data class UpdateServerName(val name: String) : SettingsIntent()
    data class UpdateServerUrl(val url: String) : SettingsIntent()
    data object SaveNewServer : SettingsIntent()
    // RAG management
    data class ToggleRagMode(val enabled: Boolean) : SettingsIntent()
    data class ToggleRagReranking(val enabled: Boolean) : SettingsIntent()
    data object ShowAddDocumentDialog : SettingsIntent()
    data object HideAddDocumentDialog : SettingsIntent()
    data class UpdateDocumentTitle(val title: String) : SettingsIntent()
    data class UpdateDocumentContent(val content: String) : SettingsIntent()
    data object SaveNewDocument : SettingsIntent()
    data class RemoveRagDocument(val documentId: String) : SettingsIntent()
    // Theme management
    data class UpdateThemeMode(val themeMode: String) : SettingsIntent()
    // Model Provider management
    data class UpdateModelProvider(val provider: String) : SettingsIntent()
    data class UpdateOllamaBaseUrlInput(val url: String) : SettingsIntent()
    data class SaveOllamaBaseUrl(val url: String) : SettingsIntent()
    data class UpdateOllamaModel(val model: String) : SettingsIntent()
    data object RefreshOllamaModels : SettingsIntent()
    data object CheckOllamaHealth : SettingsIntent()
    // User Profile management
    data class LoadUserProfile(val jsonContent: String) : SettingsIntent()
    data object ClearUserProfile : SettingsIntent()
}