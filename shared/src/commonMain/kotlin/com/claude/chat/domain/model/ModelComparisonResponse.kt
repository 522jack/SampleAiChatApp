package com.claude.chat.domain.model

import kotlinx.datetime.Instant

/**
 * Represents a response from a single model with metrics
 */
data class ModelResponse(
    val modelId: String,
    val modelName: String,
    val content: String,
    val responseTimeMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val totalCost: Double
)

/**
 * Represents a comparison response from multiple models
 */
data class ModelComparisonResponse(
    val id: String,
    val userQuestion: String,
    val responses: List<ModelResponse>,
    val timestamp: Instant
)

/**
 * Pricing information for Claude models (as of 2025)
 * Prices in USD per million tokens
 */
object ClaudePricing {
    data class ModelPricing(
        val inputPricePerMTok: Double,  // Price per million input tokens
        val outputPricePerMTok: Double   // Price per million output tokens
    )

    private val pricing = mapOf(
        // Haiku models - fastest and most cost-effective
        "claude-3-haiku-20240307" to ModelPricing(0.25, 1.25),
        "claude-3-5-haiku-20241022" to ModelPricing(0.80, 4.00),

        // Sonnet models - balanced performance
        "claude-3-sonnet-20240229" to ModelPricing(3.00, 15.00),
        "claude-3-5-sonnet-20240620" to ModelPricing(3.00, 15.00),
        "claude-3-7-sonnet-20250219" to ModelPricing(3.00, 15.00),

        // Sonnet 4 series - latest and most capable
        "claude-sonnet-4-20250514" to ModelPricing(3.00, 15.00),
        "claude-sonnet-4-5-20250929" to ModelPricing(3.00, 15.00),

        // Opus models - highest capability (older generation)
        "claude-3-opus-20240229" to ModelPricing(15.00, 75.00)
    )

    /**
     * Calculate cost for a model based on token usage
     */
    fun calculateCost(modelId: String, inputTokens: Int, outputTokens: Int): Double {
        val modelPricing = pricing[modelId] ?: ModelPricing(3.00, 15.00) // Default to Sonnet pricing

        val inputCost = (inputTokens.toDouble() / 1_000_000.0) * modelPricing.inputPricePerMTok
        val outputCost = (outputTokens.toDouble() / 1_000_000.0) * modelPricing.outputPricePerMTok

        return inputCost + outputCost
    }
}