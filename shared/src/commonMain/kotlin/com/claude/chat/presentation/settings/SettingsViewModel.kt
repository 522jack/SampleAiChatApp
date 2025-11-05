package com.claude.chat.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.repository.ChatRepository
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
    private val repository: ChatRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        loadSettings()
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SaveApiKey -> saveApiKey(intent.apiKey)
            is SettingsIntent.SaveSystemPrompt -> saveSystemPrompt(intent.prompt)
            is SettingsIntent.ToggleJsonMode -> toggleJsonMode(intent.enabled)
            is SettingsIntent.ClearAllData -> clearAllData()
            is SettingsIntent.ClearApiKey -> clearApiKey()
            is SettingsIntent.UpdateApiKeyInput -> updateApiKeyInput(intent.apiKey)
            is SettingsIntent.UpdateSystemPromptInput -> updateSystemPromptInput(intent.prompt)
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val apiKey = repository.getApiKey() ?: ""
                val systemPrompt = repository.getSystemPrompt() ?: ""
                val jsonMode = repository.getJsonMode()

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
    val isLoading: Boolean = true,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

/**
 * User intents for settings screen
 */
sealed class SettingsIntent {
    data class SaveApiKey(val apiKey: String) : SettingsIntent()
    data class SaveSystemPrompt(val prompt: String) : SettingsIntent()
    data class ToggleJsonMode(val enabled: Boolean) : SettingsIntent()
    data object ClearAllData : SettingsIntent()
    data object ClearApiKey : SettingsIntent()
    data class UpdateApiKeyInput(val apiKey: String) : SettingsIntent()
    data class UpdateSystemPromptInput(val prompt: String) : SettingsIntent()
}
