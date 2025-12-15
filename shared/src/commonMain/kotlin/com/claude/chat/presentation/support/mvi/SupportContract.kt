package com.claude.chat.presentation.support.mvi

import com.claude.chat.data.model.SourceReference

/**
 * UI State для экрана поддержки
 */
data class SupportUiState(
    val question: String = "",
    val answer: String? = null,
    val category: String? = null,
    val sources: List<SourceReference> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * User Intents для экрана поддержки
 */
sealed class SupportIntent {
    data class UpdateQuestion(val question: String) : SupportIntent()
    data object AskQuestion : SupportIntent()
    data object ClearForm : SupportIntent()
    data object ClearError : SupportIntent()
}