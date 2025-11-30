package com.claude.chat.domain.manager

import com.claude.chat.data.repository.ChatRepository
import io.github.aakira.napier.Napier

/**
 * Manager for API configuration settings
 * Handles API key validation, system prompt, and temperature settings
 */
class ApiConfigurationManager(
    private val repository: ChatRepository
) {
    companion object {
        private const val API_KEY_PREFIX = "sk-ant-"
        private const val MIN_API_KEY_LENGTH = 20
        private const val MIN_TEMPERATURE = 0.0
        private const val MAX_TEMPERATURE = 1.0

        private val API_KEY_PATTERN = Regex("^$API_KEY_PREFIX[a-zA-Z0-9-_]{20,}$")
    }

    /**
     * Load current API key from storage
     */
    suspend fun getApiKey(): String? {
        return repository.getApiKey()
    }

    /**
     * Load system prompt from storage
     */
    suspend fun getSystemPrompt(): String? {
        return repository.getSystemPrompt()
    }

    /**
     * Load temperature setting from storage
     */
    suspend fun getTemperature(): Double {
        return repository.getTemperature()
    }

    /**
     * Check if API key is configured
     */
    suspend fun isApiKeyConfigured(): Boolean {
        return repository.isApiKeyConfigured()
    }

    /**
     * Validate and save API key
     * @return Result with success or error message
     */
    suspend fun validateAndSaveApiKey(apiKey: String): Result<Unit> {
        val trimmedKey = apiKey.trim()

        return when {
            !trimmedKey.startsWith(API_KEY_PREFIX) -> {
                Napier.w("Invalid API key format - incorrect prefix")
                Result.failure(
                    IllegalArgumentException("Invalid API key format. Claude API keys start with '$API_KEY_PREFIX'")
                )
            }
            trimmedKey.length < MIN_API_KEY_LENGTH -> {
                Napier.w("Invalid API key format - too short")
                Result.failure(
                    IllegalArgumentException("API key is too short. Please check your key.")
                )
            }
            !API_KEY_PATTERN.matches(trimmedKey) -> {
                Napier.w("Invalid API key format - invalid characters")
                Result.failure(
                    IllegalArgumentException("API key contains invalid characters.")
                )
            }
            else -> {
                try {
                    repository.saveApiKey(trimmedKey)
                    Napier.d("API key saved successfully")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving API key", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Clear API key from storage
     */
    suspend fun clearApiKey(): Result<Unit> {
        return try {
            repository.saveApiKey("")
            Napier.d("API key cleared successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error clearing API key", e)
            Result.failure(e)
        }
    }

    /**
     * Save system prompt
     */
    suspend fun saveSystemPrompt(prompt: String): Result<Unit> {
        return try {
            repository.saveSystemPrompt(prompt)
            Napier.d("System prompt saved successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error saving system prompt", e)
            Result.failure(e)
        }
    }

    /**
     * Validate and save temperature setting
     * @return Result with success or error message
     */
    suspend fun validateAndSaveTemperature(temperatureStr: String): Result<Unit> {
        val temperature = temperatureStr.toDoubleOrNull()

        return when {
            temperature == null -> {
                Napier.w("Invalid temperature value - not a number")
                Result.failure(
                    IllegalArgumentException("Invalid temperature value. Please enter a number.")
                )
            }
            temperature !in MIN_TEMPERATURE..MAX_TEMPERATURE -> {
                Napier.w("Invalid temperature value - out of range: $temperature")
                Result.failure(
                    IllegalArgumentException("Temperature must be between $MIN_TEMPERATURE and $MAX_TEMPERATURE")
                )
            }
            else -> {
                try {
                    repository.saveTemperature(temperature)
                    Napier.d("Temperature saved successfully: $temperature")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving temperature", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Log API key info for debugging (without exposing the full key)
     */
    fun logApiKeyInfo(apiKey: String?) {
        if (apiKey.isNullOrBlank()) {
            Napier.d("No API key found in storage")
        } else {
            val keyPreview = apiKey.take(10)
            val keyLength = apiKey.length
            val startsWithCorrectPrefix = apiKey.startsWith(API_KEY_PREFIX)
            Napier.d("Loaded API key: prefix='$keyPreview...', length=$keyLength, valid prefix=$startsWithCorrectPrefix")
        }
    }
}