package com.claude.chat.data.model

import kotlinx.serialization.Serializable

/**
 * Модели для сервиса поддержки
 */

@Serializable
data class SupportQuestion(
    val userId: String,
    val question: String
)

@Serializable
data class SourceReference(
    val type: String, // "doc" или "ticket"
    val title: String,
    val id: String? = null,
    val relevanceScore: Double? = null
)

@Serializable
data class SupportResponse(
    val answer: String,
    val category: String,
    val confidence: Double,
    val sources: List<SourceReference>
)