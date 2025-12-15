package com.claude.chat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.model.*
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.di.AppContainer
import com.claude.chat.domain.manager.ApiConfigurationManager
import com.claude.chat.domain.manager.ModelConfigurationManager
import com.claude.chat.domain.manager.RagConfigurationManager
import com.claude.chat.presentation.settings.mvi.SettingsIntent
import com.claude.chat.presentation.settings.mvi.SettingsUiState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for settings screen
 * Delegates business logic to specialized managers
 */
class SettingsViewModel(
    private val repository: ChatRepository,
    private val appContainer: AppContainer,
    private val apiConfigManager: ApiConfigurationManager,
    private val modelConfigManager: ModelConfigurationManager,
    private val ragConfigManager: RagConfigurationManager
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadAllSettings()
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            // API Configuration
            is SettingsIntent.SaveApiKey -> saveApiKey(intent.apiKey)
            is SettingsIntent.ClearApiKey -> clearApiKey()
            is SettingsIntent.UpdateApiKeyInput -> updateApiKeyInput(intent.apiKey)
            is SettingsIntent.SaveSystemPrompt -> saveSystemPrompt(intent.prompt)
            is SettingsIntent.UpdateSystemPromptInput -> updateSystemPromptInput(intent.prompt)
            is SettingsIntent.SaveTemperature -> saveTemperature(intent.temperature)
            is SettingsIntent.UpdateTemperatureInput -> updateTemperatureInput(intent.temperature)

            // Model Configuration
            is SettingsIntent.ToggleJsonMode -> toggleJsonMode(intent.enabled)
            is SettingsIntent.ToggleTechSpecMode -> toggleTechSpecMode(intent.enabled)
            is SettingsIntent.ToggleModelComparisonMode -> toggleModelComparisonMode(intent.enabled)
            is SettingsIntent.ToggleMcp -> toggleMcp(intent.enabled)

            // Data Management
            is SettingsIntent.ClearAllData -> clearAllData()

            // MCP Server Management
            is SettingsIntent.AddMcpServer -> showAddServerDialog()
            is SettingsIntent.RemoveMcpServer -> removeMcpServer(intent.serverId)
            is SettingsIntent.ToggleMcpServer -> toggleMcpServer(intent.serverId, intent.enabled)
            is SettingsIntent.ShowAddServerDialog -> _state.update { it.copy(showAddServerDialog = true) }
            is SettingsIntent.HideAddServerDialog -> _state.update { it.copy(showAddServerDialog = false) }
            is SettingsIntent.UpdateServerName -> _state.update { it.copy(newServerName = intent.name) }
            is SettingsIntent.UpdateServerUrl -> _state.update { it.copy(newServerUrl = intent.url) }
            is SettingsIntent.SaveNewServer -> saveNewServer()

            // RAG Management
            is SettingsIntent.ToggleRagMode -> toggleRagMode(intent.enabled)
            is SettingsIntent.ToggleRagReranking -> toggleRagReranking(intent.enabled)
            is SettingsIntent.ShowAddDocumentDialog -> _state.update { it.copy(showAddDocumentDialog = true) }
            is SettingsIntent.HideAddDocumentDialog -> _state.update {
                it.copy(showAddDocumentDialog = false, newDocumentTitle = "", newDocumentContent = "")
            }
            is SettingsIntent.UpdateDocumentTitle -> _state.update { it.copy(newDocumentTitle = intent.title) }
            is SettingsIntent.UpdateDocumentContent -> _state.update { it.copy(newDocumentContent = intent.content) }
            is SettingsIntent.SaveNewDocument -> saveNewDocument()
            is SettingsIntent.RemoveRagDocument -> removeRagDocument(intent.documentId)

            // Theme Management
            is SettingsIntent.UpdateThemeMode -> updateThemeMode(intent.themeMode)

            // Model Provider Management
            is SettingsIntent.UpdateModelProvider -> updateModelProvider(intent.provider)
            is SettingsIntent.UpdateOllamaBaseUrlInput -> updateOllamaBaseUrlInput(intent.url)
            is SettingsIntent.SaveOllamaBaseUrl -> saveOllamaBaseUrl(intent.url)
            is SettingsIntent.UpdateOllamaModel -> updateOllamaModel(intent.model)
            is SettingsIntent.RefreshOllamaModels -> refreshOllamaModels()
            is SettingsIntent.CheckOllamaHealth -> checkOllamaHealth()

            // User Profile Management
            is SettingsIntent.LoadUserProfile -> loadUserProfile(intent.jsonContent)
            is SettingsIntent.ClearUserProfile -> clearUserProfile()
        }
    }

    // ============================================================================
    // Loading Settings
    // ============================================================================

    private fun loadAllSettings() {
        viewModelScope.launch {
            try {
                // Load API settings
                val apiKey = apiConfigManager.getApiKey() ?: ""
                val systemPrompt = apiConfigManager.getSystemPrompt() ?: ""
                val temperature = apiConfigManager.getTemperature()

                // Log API key info (without exposing full key)
                apiConfigManager.logApiKeyInfo(apiKey)

                // Load model settings
                val modelSettings = modelConfigManager.loadAllSettings()

                // Load RAG settings
                ragConfigManager.loadRagIndex()
                val ragSettings = ragConfigManager.loadAllSettings()

                // Load MCP servers
                val mcpServers = appContainer.mcpManager.getExternalServers()

                // Load theme mode
                val themeMode = appContainer.settingsStorage.getThemeMode()

                // Load model provider settings
                val modelProvider = repository.getModelProvider()
                val ollamaBaseUrl = repository.getOllamaBaseUrl()
                val ollamaModel = repository.getOllamaModel()

                // Load user profile
                val userProfile = repository.getUserProfile()

                _state.update {
                    it.copy(
                        apiKey = apiKey,
                        systemPrompt = systemPrompt,
                        temperature = temperature.toString(),
                        jsonModeEnabled = modelSettings.jsonMode,
                        techSpecModeEnabled = modelSettings.techSpecMode,
                        modelComparisonModeEnabled = modelSettings.comparisonMode,
                        mcpEnabled = modelSettings.mcpEnabled,
                        ragModeEnabled = ragSettings.ragMode,
                        ragRerankingEnabled = ragSettings.ragReranking,
                        ragDocuments = ragSettings.documents,
                        mcpServers = mcpServers,
                        themeMode = themeMode,
                        modelProvider = modelProvider,
                        ollamaBaseUrl = ollamaBaseUrl,
                        ollamaModel = ollamaModel,
                        userProfile = userProfile,
                        isUserProfileActive = userProfile != null,
                        isLoading = false
                    )
                }

                // Load Ollama models if Ollama provider is selected
                if (modelProvider == "OLLAMA") {
                    refreshOllamaModels()
                    checkOllamaHealth()
                }
            } catch (e: Exception) {
                Napier.e("Error loading settings", e)
                _state.update {
                    it.copy(
                        error = "Failed to load settings",
                        isLoading = false
                    )
                }
            }
        }
    }

    // ============================================================================
    // API Configuration
    // ============================================================================

    private fun updateApiKeyInput(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    private fun updateSystemPromptInput(prompt: String) {
        _state.update { it.copy(systemPrompt = prompt) }
    }

    private fun updateTemperatureInput(temperature: String) {
        _state.update { it.copy(temperature = temperature) }
    }

    private fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            val result = apiConfigManager.validateAndSaveApiKey(apiKey)
            handleOperationResult(result, "API key saved successfully")
        }
    }

    private fun clearApiKey() {
        viewModelScope.launch {
            val result = apiConfigManager.clearApiKey()
            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        apiKey = "",
                        saveSuccess = true,
                        error = null
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        error = "Failed to clear API key",
                        saveSuccess = false
                    )
                }
            }
        }
    }

    private fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            val result = apiConfigManager.saveSystemPrompt(prompt)
            handleOperationResult(result, "System prompt saved successfully")
        }
    }

    private fun saveTemperature(temperatureStr: String) {
        viewModelScope.launch {
            val result = apiConfigManager.validateAndSaveTemperature(temperatureStr)
            handleOperationResult(result, "Temperature saved successfully")
        }
    }

    // ============================================================================
    // Model Configuration
    // ============================================================================

    private fun toggleJsonMode(enabled: Boolean) {
        viewModelScope.launch {
            val result = modelConfigManager.toggleJsonMode(enabled)
            if (result.isSuccess) {
                _state.update { it.copy(jsonModeEnabled = enabled) }
            } else {
                _state.update { it.copy(error = "Failed to update JSON mode setting") }
            }
        }
    }

    private fun toggleTechSpecMode(enabled: Boolean) {
        viewModelScope.launch {
            val result = modelConfigManager.toggleTechSpecMode(enabled)
            if (result.isSuccess) {
                _state.update { it.copy(techSpecModeEnabled = enabled) }
            } else {
                _state.update { it.copy(error = "Failed to update Tech Spec mode setting") }
            }
        }
    }

    private fun toggleModelComparisonMode(enabled: Boolean) {
        viewModelScope.launch {
            val result = modelConfigManager.toggleModelComparisonMode(enabled)
            if (result.isSuccess) {
                _state.update { it.copy(modelComparisonModeEnabled = enabled) }
            } else {
                _state.update { it.copy(error = "Failed to update Model comparison mode setting") }
            }
        }
    }

    private fun toggleMcp(enabled: Boolean) {
        viewModelScope.launch {
            val result = modelConfigManager.toggleMcp(enabled)
            if (result.isSuccess) {
                _state.update { it.copy(mcpEnabled = enabled) }
            } else {
                _state.update { it.copy(error = "Failed to update MCP setting") }
            }
        }
    }

    // ============================================================================
    // Data Management
    // ============================================================================

    private fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.clearMessages()
                _state.update {
                    it.copy(
                        saveSuccess = true,
                        error = null
                    )
                }
                Napier.d("All data cleared successfully")
            } catch (e: Exception) {
                Napier.e("Error clearing data", e)
                _state.update {
                    it.copy(
                        error = "Failed to clear data",
                        saveSuccess = false
                    )
                }
            }
        }
    }

    // ============================================================================
    // MCP Server Management
    // ============================================================================

    private fun showAddServerDialog() {
        _state.update {
            it.copy(
                showAddServerDialog = true,
                newServerName = "",
                newServerUrl = ""
            )
        }
    }

    private fun saveNewServer() {
        viewModelScope.launch {
            try {
                val name = _state.value.newServerName.trim()
                val url = _state.value.newServerUrl.trim()

                if (name.isBlank() || url.isBlank()) {
                    _state.update { it.copy(error = "Name and URL are required") }
                    return@launch
                }

                // Determine type based on URL format
                val config = if (url.startsWith("http://") || url.startsWith("https://")) {
                    McpServerConfig(
                        id = "mcp-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
                        name = name,
                        type = McpServerType.HTTP,
                        enabled = true,
                        config = McpConnectionConfig.HttpConfig(url = url)
                    )
                } else {
                    McpServerConfig(
                        id = "mcp-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
                        name = name,
                        type = McpServerType.PROCESS,
                        enabled = true,
                        config = McpConnectionConfig.ProcessConfig(
                            command = "java",
                            args = listOf("-jar", url, "stdio")
                        )
                    )
                }

                appContainer.mcpManager.addExternalServer(config)
                loadMcpServers()

                _state.update {
                    it.copy(
                        showAddServerDialog = false,
                        newServerName = "",
                        newServerUrl = "",
                        saveSuccess = true
                    )
                }
            } catch (e: Exception) {
                Napier.e("Error saving MCP server", e)
                _state.update { it.copy(error = "Failed to add server: ${e.message}") }
            }
        }
    }

    private fun removeMcpServer(serverId: String) {
        viewModelScope.launch {
            try {
                appContainer.mcpManager.removeExternalServer(serverId)
                loadMcpServers()
                _state.update { it.copy(saveSuccess = true) }
            } catch (e: Exception) {
                Napier.e("Error removing MCP server", e)
                _state.update { it.copy(error = "Failed to remove server") }
            }
        }
    }

    private fun toggleMcpServer(serverId: String, enabled: Boolean) {
        viewModelScope.launch {
            try {
                appContainer.mcpManager.updateExternalServer(serverId, enabled)
                loadMcpServers()
                _state.update { it.copy(saveSuccess = true) }
            } catch (e: Exception) {
                Napier.e("Error toggling MCP server", e)
                _state.update { it.copy(error = "Failed to toggle server") }
            }
        }
    }

    private fun loadMcpServers() {
        viewModelScope.launch {
            try {
                val servers = appContainer.mcpManager.getExternalServers()
                _state.update { it.copy(mcpServers = servers) }
            } catch (e: Exception) {
                Napier.e("Error loading MCP servers", e)
            }
        }
    }

    // ============================================================================
    // RAG Management
    // ============================================================================

    private fun toggleRagMode(enabled: Boolean) {
        viewModelScope.launch {
            val result = ragConfigManager.toggleRagMode(enabled)
            if (result.isSuccess) {
                _state.update { it.copy(ragModeEnabled = enabled) }
            } else {
                _state.update { it.copy(error = "Failed to update RAG mode setting") }
            }
        }
    }

    private fun toggleRagReranking(enabled: Boolean) {
        viewModelScope.launch {
            val result = ragConfigManager.toggleRagReranking(enabled)
            if (result.isSuccess) {
                _state.update { it.copy(ragRerankingEnabled = enabled) }
            } else {
                _state.update { it.copy(error = "Failed to update RAG reranking setting") }
            }
        }
    }

    private fun saveNewDocument() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true, error = null) }

                val title = _state.value.newDocumentTitle
                val content = _state.value.newDocumentContent

                val result = ragConfigManager.indexDocument(title, content)

                if (result.isSuccess) {
                    loadRagDocuments()
                    _state.update {
                        it.copy(
                            showAddDocumentDialog = false,
                            newDocumentTitle = "",
                            newDocumentContent = "",
                            saveSuccess = true,
                            isLoading = false,
                            error = null
                        )
                    }
                } else {
                    val errorMessage = ragConfigManager.parseErrorMessage(result.exceptionOrNull())
                    _state.update {
                        it.copy(
                            error = errorMessage,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                Napier.e("Error saving document", e)
                val errorMessage = ragConfigManager.parseErrorMessage(e)
                _state.update {
                    it.copy(
                        error = errorMessage,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun removeRagDocument(documentId: String) {
        viewModelScope.launch {
            val result = ragConfigManager.removeDocument(documentId)
            if (result.isSuccess) {
                loadRagDocuments()
                _state.update { it.copy(saveSuccess = true) }
            } else {
                _state.update { it.copy(error = "Failed to remove document") }
            }
        }
    }

    private fun loadRagDocuments() {
        viewModelScope.launch {
            val documents = ragConfigManager.getIndexedDocuments()
            _state.update { it.copy(ragDocuments = documents) }
        }
    }

    // ============================================================================
    // Theme Management
    // ============================================================================

    private fun updateThemeMode(themeMode: String) {
        appContainer.settingsStorage.saveThemeMode(themeMode)
        _state.update { it.copy(themeMode = themeMode) }
        Napier.d("Theme mode updated to: $themeMode")
    }

    // ============================================================================
    // Model Provider Management
    // ============================================================================

    private fun updateModelProvider(provider: String) {
        viewModelScope.launch {
            repository.saveModelProvider(provider)
            _state.update { it.copy(modelProvider = provider) }
            Napier.d("Model provider updated to: $provider")

            // Load Ollama models when switching to Ollama
            if (provider == "OLLAMA") {
                refreshOllamaModels()
                checkOllamaHealth()
            }
        }
    }

    private fun updateOllamaBaseUrlInput(url: String) {
        _state.update { it.copy(ollamaBaseUrl = url) }
    }

    private fun saveOllamaBaseUrl(url: String) {
        viewModelScope.launch {
            repository.saveOllamaBaseUrl(url)
            Napier.d("Ollama base URL saved: $url")

            // Recreate OllamaClient with new URL to apply changes immediately
            appContainer.recreateOllamaClient()

            // Refresh models with new URL
            refreshOllamaModels()
            checkOllamaHealth()

            _state.update { it.copy(saveSuccess = true) }
        }
    }

    private fun updateOllamaModel(model: String) {
        viewModelScope.launch {
            repository.saveOllamaModel(model)
            _state.update { it.copy(ollamaModel = model) }
            Napier.d("Ollama model updated to: $model")
        }
    }

    private fun refreshOllamaModels() {
        viewModelScope.launch {
            val result = repository.listOllamaModels()
            if (result.isSuccess) {
                val models = result.getOrThrow()
                _state.update { it.copy(availableOllamaModels = models) }
                Napier.d("Loaded ${models.size} Ollama models")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to load models"
                Napier.e("Failed to load Ollama models: $error")
                _state.update { it.copy(availableOllamaModels = emptyList()) }
            }
        }
    }

    private fun checkOllamaHealth() {
        viewModelScope.launch {
            val healthy = repository.checkOllamaHealth()
            _state.update { it.copy(ollamaHealthy = healthy) }
            Napier.d("Ollama health check: $healthy")
        }
    }

    // ============================================================================
    // User Profile Management
    // ============================================================================

    private fun loadUserProfile(jsonContent: String) {
        viewModelScope.launch {
            try {
                val result = repository.loadUserProfile(jsonContent)
                if (result.isSuccess) {
                    val profile = result.getOrThrow()
                    _state.update {
                        it.copy(
                            userProfile = profile,
                            isUserProfileActive = true,
                            saveSuccess = true,
                            error = null
                        )
                    }
                    Napier.d("User profile loaded: ${profile.name}")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to load profile"
                    _state.update { it.copy(error = error, saveSuccess = false) }
                    Napier.e("Error loading user profile: $error")
                }
            } catch (e: Exception) {
                Napier.e("Error loading user profile", e)
                _state.update { it.copy(error = e.message, saveSuccess = false) }
            }
        }
    }

    private fun clearUserProfile() {
        viewModelScope.launch {
            try {
                val cleared = repository.clearUserProfile()
                if (cleared) {
                    _state.update {
                        it.copy(
                            userProfile = null,
                            isUserProfileActive = false,
                            saveSuccess = true
                        )
                    }
                    Napier.d("User profile cleared")
                } else {
                    _state.update { it.copy(error = "Failed to clear profile") }
                }
            } catch (e: Exception) {
                Napier.e("Error clearing user profile", e)
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun loadUserProfileState() {
        viewModelScope.launch {
            try {
                val profile = repository.getUserProfile()
                _state.update {
                    it.copy(
                        userProfile = profile,
                        isUserProfileActive = profile != null
                    )
                }
                if (profile != null) {
                    Napier.d("User profile state loaded: ${profile.name}")
                }
            } catch (e: Exception) {
                Napier.e("Error loading user profile state", e)
            }
        }
    }

    // ============================================================================
    // Utility Methods
    // ============================================================================

    fun resetSaveSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }

    private fun handleOperationResult(result: Result<Unit>, successMessage: String? = null) {
        if (result.isSuccess) {
            _state.update {
                it.copy(
                    saveSuccess = true,
                    error = null
                )
            }
            successMessage?.let { Napier.d(it) }
        } else {
            val errorMessage = result.exceptionOrNull()?.message ?: "Operation failed"
            _state.update {
                it.copy(
                    error = errorMessage,
                    saveSuccess = false
                )
            }
        }
    }
}