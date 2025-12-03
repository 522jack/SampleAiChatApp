package com.claude.support.model

import kotlinx.serialization.Serializable

// ==================== CRM Data Models ====================

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val plan: String,
    val registeredAt: String
)

@Serializable
data class Ticket(
    val id: String,
    val userId: String,
    val category: String,
    val status: String,
    val priority: String,
    val title: String,
    val description: String,
    val solution: String,
    val createdAt: String,
    val resolvedAt: String? = null,
    val tags: List<String> = emptyList()
)

@Serializable
data class SupportData(
    val users: List<User>,
    val tickets: List<Ticket>
)

// ==================== API Models ====================

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

// ==================== Categorization Models ====================

enum class QuestionCategory(val displayName: String) {
    AUTHORIZATION("авторизация"),
    PAYMENT("оплата"),
    FUNCTIONALITY("функционал"),
    BUGS("баги"),
    OTHER("другое")
}

@Serializable
data class CategoryResult(
    val category: String,
    val confidence: Double,
    val reasoning: String? = null
)

// ==================== RAG Models ====================

@Serializable
data class DocumentChunk(
    val documentId: String,
    val documentTitle: String,
    val chunkIndex: Int,
    val content: String,
    val embedding: List<Double>? = null
)

@Serializable
data class SearchResult(
    val chunk: DocumentChunk,
    val similarity: Double,
    val source: String
)