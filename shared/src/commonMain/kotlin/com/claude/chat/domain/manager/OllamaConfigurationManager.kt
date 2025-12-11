package com.claude.chat.domain.manager

import com.claude.chat.data.model.OllamaOptions
import com.claude.chat.data.repository.ChatRepository
import io.github.aakira.napier.Napier

/**
 * Manager for Ollama configuration settings
 * Handles model parameters optimization for local LLMs
 */
class OllamaConfigurationManager(
    private val repository: ChatRepository
) {
    companion object {
        // Default values based on Ollama recommendations (public for use in ChatRepositoryImpl)
        const val DEFAULT_TEMPERATURE = 0.4
        const val DEFAULT_TOP_P = 0.6
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_NUM_CTX = 4096
        const val DEFAULT_NUM_PREDICT = 512
        const val DEFAULT_REPEAT_PENALTY = 1.1
        const val DEFAULT_REPEAT_LAST_N = 64

        // Validation ranges
        const val MIN_TEMPERATURE = 0.0
        const val MAX_TEMPERATURE = 2.0
        const val MIN_TOP_P = 0.0
        const val MAX_TOP_P = 1.0
        const val MIN_TOP_K = 1
        const val MAX_TOP_K = 100
        const val MIN_NUM_CTX = 128
        const val MAX_NUM_CTX = 32768
        const val MIN_NUM_PREDICT = -2
        const val MAX_NUM_PREDICT = 8192
        const val MIN_REPEAT_PENALTY = 0.0
        const val MAX_REPEAT_PENALTY = 2.0
    }

    /**
     * Get current Ollama options or defaults
     */
    suspend fun getOllamaOptions(): OllamaOptions {
        return OllamaOptions(
            temperature = repository.getOllamaTemperature() ?: DEFAULT_TEMPERATURE,
            topP = repository.getOllamaTopP() ?: DEFAULT_TOP_P,
            topK = repository.getOllamaTopK() ?: DEFAULT_TOP_K,
            numCtx = repository.getOllamaNumCtx() ?: DEFAULT_NUM_CTX,
            numPredict = repository.getOllamaNumPredict() ?: DEFAULT_NUM_PREDICT,
            repeatPenalty = repository.getOllamaRepeatPenalty() ?: DEFAULT_REPEAT_PENALTY,
            repeatLastN = repository.getOllamaRepeatLastN() ?: DEFAULT_REPEAT_LAST_N,
            seed = repository.getOllamaSeed(),
            stop = repository.getOllamaStopSequences(),
            numThread = repository.getOllamaNumThread()
        )
    }

    /**
     * Get current system prompt template for Ollama
     */
    suspend fun getSystemPromptTemplate(): String? {
        return repository.getOllamaSystemPrompt()
    }

    /**
     * Save Ollama temperature setting
     */
    suspend fun saveTemperature(temperature: Double): Result<Unit> {
        return when {
            temperature !in MIN_TEMPERATURE..MAX_TEMPERATURE -> {
                Napier.w("Invalid temperature: $temperature")
                Result.failure(
                    IllegalArgumentException("Temperature must be between $MIN_TEMPERATURE and $MAX_TEMPERATURE")
                )
            }
            else -> {
                try {
                    repository.saveOllamaTemperature(temperature)
                    Napier.d("Ollama temperature saved: $temperature")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving Ollama temperature", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Save Ollama Top P setting
     */
    suspend fun saveTopP(topP: Double): Result<Unit> {
        return when {
            topP !in MIN_TOP_P..MAX_TOP_P -> {
                Napier.w("Invalid Top P: $topP")
                Result.failure(
                    IllegalArgumentException("Top P must be between $MIN_TOP_P and $MAX_TOP_P")
                )
            }
            else -> {
                try {
                    repository.saveOllamaTopP(topP)
                    Napier.d("Ollama Top P saved: $topP")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving Ollama Top P", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Save Ollama Top K setting
     */
    suspend fun saveTopK(topK: Int): Result<Unit> {
        return when {
            topK !in MIN_TOP_K..MAX_TOP_K -> {
                Napier.w("Invalid Top K: $topK")
                Result.failure(
                    IllegalArgumentException("Top K must be between $MIN_TOP_K and $MAX_TOP_K")
                )
            }
            else -> {
                try {
                    repository.saveOllamaTopK(topK)
                    Napier.d("Ollama Top K saved: $topK")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving Ollama Top K", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Save Ollama context window size
     */
    suspend fun saveNumCtx(numCtx: Int): Result<Unit> {
        return when {
            numCtx !in MIN_NUM_CTX..MAX_NUM_CTX -> {
                Napier.w("Invalid context window: $numCtx")
                Result.failure(
                    IllegalArgumentException("Context window must be between $MIN_NUM_CTX and $MAX_NUM_CTX")
                )
            }
            else -> {
                try {
                    repository.saveOllamaNumCtx(numCtx)
                    Napier.d("Ollama context window saved: $numCtx")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving Ollama context window", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Save Ollama max tokens to predict
     */
    suspend fun saveNumPredict(numPredict: Int): Result<Unit> {
        return when {
            numPredict !in MIN_NUM_PREDICT..MAX_NUM_PREDICT -> {
                Napier.w("Invalid max tokens: $numPredict")
                Result.failure(
                    IllegalArgumentException("Max tokens must be between $MIN_NUM_PREDICT and $MAX_NUM_PREDICT")
                )
            }
            else -> {
                try {
                    repository.saveOllamaNumPredict(numPredict)
                    Napier.d("Ollama max tokens saved: $numPredict")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving Ollama max tokens", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Save Ollama repeat penalty
     */
    suspend fun saveRepeatPenalty(repeatPenalty: Double): Result<Unit> {
        return when {
            repeatPenalty !in MIN_REPEAT_PENALTY..MAX_REPEAT_PENALTY -> {
                Napier.w("Invalid repeat penalty: $repeatPenalty")
                Result.failure(
                    IllegalArgumentException("Repeat penalty must be between $MIN_REPEAT_PENALTY and $MAX_REPEAT_PENALTY")
                )
            }
            else -> {
                try {
                    repository.saveOllamaRepeatPenalty(repeatPenalty)
                    Napier.d("Ollama repeat penalty saved: $repeatPenalty")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Napier.e("Error saving Ollama repeat penalty", e)
                    Result.failure(e)
                }
            }
        }
    }

    /**
     * Save Ollama system prompt template
     */
    suspend fun saveSystemPromptTemplate(prompt: String): Result<Unit> {
        return try {
            repository.saveOllamaSystemPrompt(prompt)
            Napier.d("Ollama system prompt template saved")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error saving Ollama system prompt template", e)
            Result.failure(e)
        }
    }

    /**
     * Save Ollama stop sequences
     */
    suspend fun saveStopSequences(sequences: List<String>): Result<Unit> {
        return try {
            repository.saveOllamaStopSequences(sequences)
            Napier.d("Ollama stop sequences saved: $sequences")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error saving Ollama stop sequences", e)
            Result.failure(e)
        }
    }

    /**
     * Save Ollama seed for reproducibility
     */
    suspend fun saveSeed(seed: Int?): Result<Unit> {
        return try {
            repository.saveOllamaSeed(seed)
            Napier.d("Ollama seed saved: $seed")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error saving Ollama seed", e)
            Result.failure(e)
        }
    }

    /**
     * Reset all Ollama settings to defaults
     */
    suspend fun resetToDefaults(): Result<Unit> {
        return try {
            repository.saveOllamaTemperature(DEFAULT_TEMPERATURE)
            repository.saveOllamaTopP(DEFAULT_TOP_P)
            repository.saveOllamaTopK(DEFAULT_TOP_K)
            repository.saveOllamaNumCtx(DEFAULT_NUM_CTX)
            repository.saveOllamaNumPredict(DEFAULT_NUM_PREDICT)
            repository.saveOllamaRepeatPenalty(DEFAULT_REPEAT_PENALTY)
            repository.saveOllamaRepeatLastN(DEFAULT_REPEAT_LAST_N)
            repository.saveOllamaSeed(null)
            repository.saveOllamaStopSequences(null)
            Napier.d("Ollama settings reset to defaults")
            Result.success(Unit)
        } catch (e: Exception) {
            Napier.e("Error resetting Ollama settings", e)
            Result.failure(e)
        }
    }

    /**
     * Get preset configurations for common use cases
     */
    fun getPreset(preset: OllamaPreset): OllamaOptions {
        return when (preset) {
            OllamaPreset.CREATIVE -> OllamaOptions(
                temperature = 1.2,
                topP = 0.95,
                topK = 50,
                numCtx = 4096,
                numPredict = 1024,
                repeatPenalty = 1.05,
                repeatLastN = 128
            )
            OllamaPreset.PRECISE -> OllamaOptions(
                temperature = 0.3,
                topP = 0.7,
                topK = 20,
                numCtx = 4096,
                numPredict = 512,
                repeatPenalty = 1.2,
                repeatLastN = 64
            )
            OllamaPreset.BALANCED -> OllamaOptions(
                temperature = DEFAULT_TEMPERATURE,
                topP = DEFAULT_TOP_P,
                topK = DEFAULT_TOP_K,
                numCtx = DEFAULT_NUM_CTX,
                numPredict = DEFAULT_NUM_PREDICT,
                repeatPenalty = DEFAULT_REPEAT_PENALTY,
                repeatLastN = DEFAULT_REPEAT_LAST_N
            )
            OllamaPreset.FAST -> OllamaOptions(
                temperature = 0.7,
                topP = 0.9,
                topK = 40,
                numCtx = 2048,
                numPredict = 256,
                repeatPenalty = 1.1,
                repeatLastN = 32
            )
            OllamaPreset.LONG_CONTEXT -> OllamaOptions(
                temperature = 0.7,
                topP = 0.9,
                topK = 40,
                numCtx = 8192,
                numPredict = 1024,
                repeatPenalty = 1.1,
                repeatLastN = 128
            )
        }
    }

    /**
     * Log current Ollama configuration
     */
    suspend fun logConfiguration() {
        val options = getOllamaOptions()
        Napier.d("═══════════════════════════════════════════════════════════")
        Napier.d("Current Ollama Configuration:")
        Napier.d("  Temperature: ${options.temperature}")
        Napier.d("  Top P: ${options.topP}")
        Napier.d("  Top K: ${options.topK}")
        Napier.d("  Context Window: ${options.numCtx}")
        Napier.d("  Max Tokens: ${options.numPredict}")
        Napier.d("  Repeat Penalty: ${options.repeatPenalty}")
        Napier.d("  Repeat Last N: ${options.repeatLastN}")
        Napier.d("  Seed: ${options.seed ?: "random"}")
        Napier.d("  Stop Sequences: ${options.stop}")
        Napier.d("═══════════════════════════════════════════════════════════")
    }
}

/**
 * Preset configurations for common use cases
 */
enum class OllamaPreset {
    /** High creativity and randomness */
    CREATIVE,
    /** Low temperature for accurate, deterministic responses */
    PRECISE,
    /** Balanced settings (default) */
    BALANCED,
    /** Faster responses with smaller context */
    FAST,
    /** Large context window for long conversations */
    LONG_CONTEXT
}
