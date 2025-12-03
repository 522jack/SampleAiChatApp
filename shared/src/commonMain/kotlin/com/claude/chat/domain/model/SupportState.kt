package com.claude.chat.domain.model

import com.claude.chat.data.model.SourceReference

/**
 * Состояние экрана поддержки
 */
data class SupportState(
    val question: String = "",
    val answer: String? = null,
    val category: String? = null,
    val sources: List<SourceReference> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)