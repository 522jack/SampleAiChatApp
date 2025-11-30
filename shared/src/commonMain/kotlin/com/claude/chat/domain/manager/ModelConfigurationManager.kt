package com.claude.chat.domain.manager

import com.claude.chat.data.repository.ChatRepository
import io.github.aakira.napier.Napier

/**
 * Manager for model configuration settings
 * Handles model selection, JSON mode, Tech Spec mode, comparison mode, and MCP settings
 */
class ModelConfigurationManager(
    private val repository: ChatRepository
) {
    /**
     * Get JSON mode setting
     */
    suspend fun getJsonMode(): Boolean {
        return repository.getJsonMode()
    }

    /**
     * Toggle JSON mode
     */
    suspend fun toggleJsonMode(enabled: Boolean): Result<Unit> {
        return try {
            repository.saveJsonMode(enabled)
            Napier.d("JSON mode ${if (enabled) "enabled" else "disabled"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error toggling JSON mode", e)
            Result.failure(e)
        }
    }

    /**
     * Get Tech Spec mode setting
     */
    suspend fun getTechSpecMode(): Boolean {
        return repository.getTechSpecMode()
    }

    /**
     * Toggle Tech Spec mode
     */
    suspend fun toggleTechSpecMode(enabled: Boolean): Result<Unit> {
        return try {
            repository.saveTechSpecMode(enabled)
            Napier.d("Tech Spec mode ${if (enabled) "enabled" else "disabled"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error toggling Tech Spec mode", e)
            Result.failure(e)
        }
    }

    /**
     * Get model comparison mode setting
     */
    suspend fun getModelComparisonMode(): Boolean {
        return repository.getModelComparisonMode()
    }

    /**
     * Toggle model comparison mode
     */
    suspend fun toggleModelComparisonMode(enabled: Boolean): Result<Unit> {
        return try {
            repository.saveModelComparisonMode(enabled)
            Napier.d("Model comparison mode ${if (enabled) "enabled" else "disabled"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error toggling Model comparison mode", e)
            Result.failure(e)
        }
    }

    /**
     * Get MCP enabled setting
     */
    suspend fun getMcpEnabled(): Boolean {
        return repository.getMcpEnabled()
    }

    /**
     * Toggle MCP (Model Context Protocol)
     */
    suspend fun toggleMcp(enabled: Boolean): Result<Unit> {
        return try {
            repository.saveMcpEnabled(enabled)
            Napier.d("MCP ${if (enabled) "enabled" else "disabled"}")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error toggling MCP", e)
            Result.failure(e)
        }
    }

    /**
     * Get selected model
     */
    suspend fun getSelectedModel(): String {
        return repository.getSelectedModel()
    }

    /**
     * Save selected model
     */
    suspend fun saveSelectedModel(modelId: String): Result<Unit> {
        return try {
            repository.saveSelectedModel(modelId)
            Napier.d("Selected model saved: $modelId")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error saving selected model", e)
            Result.failure(e)
        }
    }

    /**
     * Load all model configuration settings
     */
    suspend fun loadAllSettings(): ModelSettings {
        return try {
            ModelSettings(
                jsonMode = getJsonMode(),
                techSpecMode = getTechSpecMode(),
                comparisonMode = getModelComparisonMode(),
                mcpEnabled = getMcpEnabled(),
                selectedModel = getSelectedModel()
            )
        } catch (e: Exception) {
            Napier.e("Error loading model settings", e)
            ModelSettings()
        }
    }
}

/**
 * Data class to hold all model configuration settings
 */
data class ModelSettings(
    val jsonMode: Boolean = false,
    val techSpecMode: Boolean = false,
    val comparisonMode: Boolean = false,
    val mcpEnabled: Boolean = false,
    val selectedModel: String = ""
)