package com.claude.chat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.model.*
import com.claude.chat.data.repository.ChatRepository
import com.claude.chat.di.AppContainer
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for settings screen
 */
class SettingsViewModel(
    private val repository: ChatRepository,
    private val appContainer: AppContainer
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
        loadMcpServers()
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SaveApiKey -> saveApiKey(intent.apiKey)
            is SettingsIntent.SaveSystemPrompt -> saveSystemPrompt(intent.prompt)
            is SettingsIntent.ToggleJsonMode -> toggleJsonMode(intent.enabled)
            is SettingsIntent.ToggleTechSpecMode -> toggleTechSpecMode(intent.enabled)
            is SettingsIntent.ToggleModelComparisonMode -> toggleModelComparisonMode(intent.enabled)
            is SettingsIntent.ToggleMcp -> toggleMcp(intent.enabled)
            is SettingsIntent.ClearAllData -> clearAllData()
            is SettingsIntent.ClearApiKey -> clearApiKey()
            is SettingsIntent.UpdateApiKeyInput -> updateApiKeyInput(intent.apiKey)
            is SettingsIntent.UpdateSystemPromptInput -> updateSystemPromptInput(intent.prompt)
            is SettingsIntent.UpdateTemperatureInput -> updateTemperatureInput(intent.temperature)
            is SettingsIntent.SaveTemperature -> saveTemperature(intent.temperature)
            is SettingsIntent.AddMcpServer -> showAddServerDialog()
            is SettingsIntent.RemoveMcpServer -> removeMcpServer(intent.serverId)
            is SettingsIntent.ToggleMcpServer -> toggleMcpServer(intent.serverId, intent.enabled)
            is SettingsIntent.ShowAddServerDialog -> _state.update { it.copy(showAddServerDialog = true) }
            is SettingsIntent.HideAddServerDialog -> _state.update { it.copy(showAddServerDialog = false) }
            is SettingsIntent.UpdateServerName -> _state.update { it.copy(newServerName = intent.name) }
            is SettingsIntent.UpdateServerUrl -> _state.update { it.copy(newServerUrl = intent.url) }
            is SettingsIntent.SaveNewServer -> saveNewServer()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val apiKey = repository.getApiKey() ?: ""
                val systemPrompt = repository.getSystemPrompt() ?: ""
                val jsonMode = repository.getJsonMode()
                val techSpecMode = repository.getTechSpecMode()
                val temperature = repository.getTemperature()
                val comparisonMode = repository.getModelComparisonMode()
                val mcpEnabled = repository.getMcpEnabled()

                // Log key info for debugging
                if (apiKey.isNotBlank()) {
                    val keyPreview = apiKey.take(10)
                    val keyLength = apiKey.length
                    val startsWithCorrectPrefix = apiKey.startsWith("sk-ant-")
                    Napier.d("Loaded API key: prefix='$keyPreview...', length=$keyLength, valid prefix=$startsWithCorrectPrefix")
                } else {
                    Napier.d("No API key found in storage")
                }

                _state.update {
                    it.copy(
                        apiKey = apiKey,
                        systemPrompt = systemPrompt,
                        jsonModeEnabled = jsonMode,
                        techSpecModeEnabled = techSpecMode,
                        modelComparisonModeEnabled = comparisonMode,
                        mcpEnabled = mcpEnabled,
                        temperature = temperature.toString(),
                        isLoading = false
                    )
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

    private fun updateApiKeyInput(apiKey: String) {
        _state.update { it.copy(apiKey = apiKey) }
    }

    private fun updateSystemPromptInput(prompt: String) {
        _state.update { it.copy(systemPrompt = prompt) }
    }

    private fun updateTemperatureInput(temperature: String) {
        _state.update { it.copy(temperature = temperature) }
    }

    private fun saveTemperature(temperatureStr: String) {
        viewModelScope.launch {
            try {
                val temperature = temperatureStr.toDoubleOrNull()
                if (temperature == null) {
                    _state.update {
                        it.copy(
                            error = "Invalid temperature value. Please enter a number.",
                            saveSuccess = false
                        )
                    }
                    return@launch
                }

                if (temperature !in 0.0..1.0) {
                    _state.update {
                        it.copy(
                            error = "Temperature must be between 0.0 and 1.0",
                            saveSuccess = false
                        )
                    }
                    return@launch
                }

                repository.saveTemperature(temperature)
                _state.update {
                    it.copy(
                        saveSuccess = true,
                        error = null
                    )
                }
                Napier.d("Temperature saved successfully: $temperature")
            } catch (e: Exception) {
                Napier.e("Error saving temperature", e)
                _state.update {
                    it.copy(
                        error = "Failed to save temperature: ${e.message}",
                        saveSuccess = false
                    )
                }
            }
        }
    }

    private fun saveApiKey(apiKey: String) {
        viewModelScope.launch {
            try {
                // Validate API key format
                val trimmedKey = apiKey.trim()
                if (!trimmedKey.startsWith("sk-ant-")) {
                    _state.update {
                        it.copy(
                            error = "Invalid API key format. Claude API keys start with 'sk-ant-'",
                            saveSuccess = false
                        )
                    }
                    return@launch
                }

                if (trimmedKey.length < 20) {
                    _state.update {
                        it.copy(
                            error = "API key is too short. Please check your key.",
                            saveSuccess = false
                        )
                    }
                    return@launch
                }

                repository.saveApiKey(trimmedKey)
                _state.update {
                    it.copy(
                        saveSuccess = true,
                        error = null
                    )
                }
                Napier.d("API key saved successfully")
            } catch (e: Exception) {
                Napier.e("Error saving API key", e)
                _state.update {
                    it.copy(
                        error = "Failed to save API key: ${e.message}",
                        saveSuccess = false
                    )
                }
            }
        }
    }

    private fun saveSystemPrompt(prompt: String) {
        viewModelScope.launch {
            try {
                repository.saveSystemPrompt(prompt)
                _state.update {
                    it.copy(
                        saveSuccess = true,
                        error = null
                    )
                }
                Napier.d("System prompt saved successfully")
            } catch (e: Exception) {
                Napier.e("Error saving system prompt", e)
                _state.update {
                    it.copy(
                        error = "Failed to save system prompt",
                        saveSuccess = false
                    )
                }
            }
        }
    }

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

    private fun clearApiKey() {
        viewModelScope.launch {
            try {
                repository.saveApiKey("")
                _state.update {
                    it.copy(
                        apiKey = "",
                        saveSuccess = true,
                        error = null
                    )
                }
                Napier.d("API key cleared successfully")
            } catch (e: Exception) {
                Napier.e("Error clearing API key", e)
                _state.update {
                    it.copy(
                        error = "Failed to clear API key",
                        saveSuccess = false
                    )
                }
            }
        }
    }

    private fun toggleJsonMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveJsonMode(enabled)
                _state.update {
                    it.copy(
                        jsonModeEnabled = enabled
                    )
                }
                Napier.d("JSON mode ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Napier.e("Error toggling JSON mode", e)
                _state.update {
                    it.copy(
                        error = "Failed to update JSON mode setting"
                    )
                }
            }
        }
    }

    private fun toggleTechSpecMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveTechSpecMode(enabled)
                _state.update {
                    it.copy(
                        techSpecModeEnabled = enabled
                    )
                }
                Napier.d("Tech Spec mode ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Napier.e("Error toggling Tech Spec mode", e)
                _state.update {
                    it.copy(
                        error = "Failed to update Tech Spec mode setting"
                    )
                }
            }
        }
    }

    private fun toggleModelComparisonMode(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveModelComparisonMode(enabled)
                _state.update {
                    it.copy(
                        modelComparisonModeEnabled = enabled
                    )
                }
                Napier.d("Model comparison mode ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Napier.e("Error toggling Model comparison mode", e)
                _state.update {
                    it.copy(
                        error = "Failed to update Model comparison mode setting"
                    )
                }
            }
        }
    }

    private fun toggleMcp(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repository.saveMcpEnabled(enabled)
                _state.update {
                    it.copy(
                        mcpEnabled = enabled
                    )
                }
                Napier.d("MCP ${if (enabled) "enabled" else "disabled"}")
            } catch (e: Exception) {
                Napier.e("Error toggling MCP", e)
                _state.update {
                    it.copy(
                        error = "Failed to update MCP setting"
                    )
                }
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
                    // HTTP/SSE connection
                    McpServerConfig(
                        id = "mcp-${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
                        name = name,
                        type = McpServerType.HTTP,
                        enabled = true,
                        config = McpConnectionConfig.HttpConfig(
                            url = url
                        )
                    )
                } else {
                    // Process-based connection (for desktop)
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
                // Update server config and reinitialize if needed
                appContainer.mcpManager.updateExternalServer(serverId, enabled)
                loadMcpServers()
                _state.update { it.copy(saveSuccess = true) }
            } catch (e: Exception) {
                Napier.e("Error toggling MCP server", e)
                _state.update { it.copy(error = "Failed to toggle server") }
            }
        }
    }

    fun resetSaveSuccess() {
        _state.update { it.copy(saveSuccess = false) }
    }
}

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
    val isLoading: Boolean = true,
    val error: String? = null,
    val saveSuccess: Boolean = false,
    // MCP Server management
    val mcpServers: List<McpServerConfig> = emptyList(),
    val showAddServerDialog: Boolean = false,
    val newServerName: String = "",
    val newServerUrl: String = ""
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
}
