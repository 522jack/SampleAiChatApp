package com.claude.chat.presentation.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claude.chat.data.remote.SupportApiClient
import com.claude.chat.domain.model.SupportState
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана поддержки
 */
class SupportViewModel(
    private val supportApiClient: SupportApiClient,
    private val userId: String = "user_001" // TODO: получать из настроек/авторизации
) : ViewModel() {

    private val _state = MutableStateFlow(SupportState())
    val state: StateFlow<SupportState> = _state.asStateFlow()

    /**
     * Обновить текст вопроса
     */
    fun updateQuestion(question: String) {
        _state.value = _state.value.copy(question = question)
    }

    /**
     * Отправить вопрос в службу поддержки
     */
    fun askQuestion() {
        val question = _state.value.question.trim()

        if (question.isEmpty()) {
            _state.value = _state.value.copy(error = "Пожалуйста, введите ваш вопрос")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                answer = null // Сбрасываем предыдущий ответ
            )

            try {
                val result = supportApiClient.askQuestion(userId, question)

                result.onSuccess { response ->
                    Napier.i("Got support response: ${response.category}")
                    _state.value = _state.value.copy(
                        isLoading = false,
                        answer = response.answer,
                        category = response.category,
                        sources = response.sources,
                        error = null
                    )
                }.onFailure { error ->
                    Napier.e("Support request failed: ${error.message}", error)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Не удалось получить ответ: ${error.message}"
                    )
                }
            } catch (e: Exception) {
                Napier.e("Unexpected error during support request", e)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Произошла ошибка: ${e.message}"
                )
            }
        }
    }

    /**
     * Очистить форму для нового вопроса
     */
    fun clearForm() {
        _state.value = SupportState()
    }

    /**
     * Очистить ошибку
     */
    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}